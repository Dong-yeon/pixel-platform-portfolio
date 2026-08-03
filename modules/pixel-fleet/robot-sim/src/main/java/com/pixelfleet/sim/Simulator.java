package com.pixelfleet.sim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfleet.sim.config.SimProperties;
import com.pixelfleet.sim.map.NodeMap;
import com.pixelfleet.sim.mqtt.SimMqttClient;
import com.pixelfleet.sim.robot.RobotState;
import com.pixelfleet.sim.robot.VirtualRobot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the virtual fleet. Each tick it advances every robot, drains/charges batteries,
 * rolls occasional task failures, and publishes the resulting telemetry. Incoming GOTO
 * commands (from the control server's dispatch) assign a task to a robot.
 *
 * <p>Movement model: a task is two legs — pick up at origin, drop at destination. Idle
 * robots roam to keep the map lively; a low battery sends a robot to the nearest dock.
 */
@Component
public class Simulator {

    private static final Logger log = LoggerFactory.getLogger(Simulator.class);
    private static final double ROAM_PROBABILITY = 0.15;
    /** 노드 중심에서 이만큼 떨어진 자리에 정차한다(로봇끼리 포개지지 않도록). */
    private static final double PARK_RADIUS = 1.1;
    /** 한 노드 둘레에 놓는 자리 수. 안쪽 고리가 차면 두 배 반경의 바깥 고리를 쓴다. */
    private static final int PARK_SLOTS = 6;
    /** 로봇 지름. 중심이 이보다 가까우면 지도에서 겹쳐 보인다. */
    private static final double ROBOT_CLEARANCE = 1.9;
    /** 이 tick 수마다 상태를 다시 발행한다(서버와의 상태 불일치 자가 복구). */
    private static final int HEARTBEAT_TICKS = 10;
    private static final String[] FAILURE_REASONS = {
            "obstacle timeout", "localization lost", "path blocked", "estop triggered"
    };

    private final SimProperties properties;
    private final NodeMap nodeMap;
    private final SimMqttClient mqtt;
    private final ObjectMapper objectMapper;

    private final Object lock = new Object();
    private final List<VirtualRobot> robots = new ArrayList<>();
    private final Map<String, VirtualRobot> byCode = new HashMap<>();
    private final Map<String, Integer> lastBatteryPercent = new HashMap<>();
    /** 하트비트 카운터 — 주기적으로 상태를 다시 알린다(아래 tick 참고). */
    private int tickCount = 0;

    public Simulator(SimProperties properties, NodeMap nodeMap, SimMqttClient mqtt, ObjectMapper objectMapper) {
        this.properties = properties;
        this.nodeMap = nodeMap;
        this.mqtt = mqtt;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        synchronized (lock) {
            for (SimProperties.RobotDef def : properties.getRobots()) {
                double[] home = spot(def.getHome(), def.getCode());
                VirtualRobot robot = new VirtualRobot(def.getCode(), def.getName(), home[0], home[1]);
                robots.add(robot);
                byCode.put(def.getCode(), robot);
            }
        }
        mqtt.connect(this::onCommand);
        // Bring the fleet online: publish an initial IDLE status, position and battery for each.
        synchronized (lock) {
            for (VirtualRobot robot : robots) {
                setState(robot, RobotState.IDLE);
                publishPosition(robot);
                publishBattery(robot, true);
            }
        }
        log.info("Simulator started with {} robots.", robots.size());
    }

    @Scheduled(fixedRateString = "${sim.tick-interval-ms}")
    public void tick() {
        synchronized (lock) {
            // 텔레메트리 하트비트.
            //
            // 상태·배터리는 "바뀔 때만" 발행하는데, 그것만으로는 서버와 한 번 어긋나면 영원히
            // 복구되지 않는다. 실제로 두 가지가 터졌다:
            //   1) 서버가 배차하며 MOVING으로 찍었으나 로봇이 그 작업을 받지 않은 경우 —
            //      로봇은 IDLE 그대로라 다시 알리지 않고, 그 로봇은 영영 배차 대상에서 빠진다.
            //   2) 시뮬레이터가 서버보다 먼저 떠서 초기 텔레메트리가 유실된 경우 —
            //      서버는 배터리를 0으로 알고, 정지 상태에서는 값이 안 바뀌어 재발행도 없어
            //      모든 로봇이 저배터리로 간주돼 배차가 전부 막힌다.
            //
            // 실제 로봇처럼 주기적으로 현재 상태를 통째로 다시 알려 스스로 맞춘다.
            if (++tickCount % HEARTBEAT_TICKS == 0) {
                for (VirtualRobot robot : robots) {
                    publishStatus(robot);
                    publishBattery(robot, true);
                    publishPosition(robot);
                }
            }

            for (VirtualRobot robot : robots) {
                switch (robot.getState()) {
                    case CHARGING -> tickCharging(robot);
                    case MOVING -> tickMoving(robot);
                    case IDLE -> tickIdle(robot);
                    default -> { /* OFFLINE / ERROR: nothing to do */ }
                }
            }
        }
    }

    private void tickCharging(VirtualRobot robot) {
        robot.charge(properties.getChargePerTick());
        publishBattery(robot, false);
        if (robot.getBattery() >= 95.0) {
            setState(robot, RobotState.IDLE);
        }
    }

    private void tickMoving(VirtualRobot robot) {
        boolean reached = robot.advanceToward(properties.getSpeed());
        robot.drain(properties.getBatteryDrainPerTick());
        publishPosition(robot);
        publishBattery(robot, false);

        // A task can fail mid-route; this exercises the control server's retry path.
        if (robot.hasTask() && ThreadLocalRandom.current().nextDouble() < properties.getFailureRate()) {
            String reason = FAILURE_REASONS[ThreadLocalRandom.current().nextInt(FAILURE_REASONS.length)];
            publishTask(robot, "failed", reason);
            robot.abortTask();
            setState(robot, RobotState.IDLE);
            return;
        }

        if (!reached) {
            return;
        }
        if (robot.hasPath()) {
            // Reached the pickup (origin) leg; keep going to the destination.
            return;
        }
        // Final arrival.
        if (robot.hasTask()) {
            if (!robot.isPickedUp()) {
                // leg1 완료 — 픽업 도착을 알리고 서버가 leg2 경로를 줄 때까지 기다린다.
                // IDLE로 내리지 않는다(다른 작업이 배차되면 안 되므로). 경로만 빈 채 MOVING 유지.
                robot.markPickedUp();
                // 적재 상태가 바뀌었으니 즉시 알린다 — 위치 채널에 실려 가므로 지도에 파렛트가
                // 바로 나타난다(하트비트를 기다리면 최대 10초 늦다).
                publishPosition(robot);
                publishTask(robot, "picked", null);
                return;
            }
            publishTask(robot, "completed", null);
            robot.abortTask();
            setState(robot, RobotState.IDLE);
            // 하역 완료 — 파렛트를 내렸음을 즉시 알린다. 이걸 빼면 로봇이 멈춘 뒤로 위치
            // 발행이 없어, 하트비트(10초)까지 지도에 파렛트가 남아 있는다.
            publishPosition(robot);
        } else if (robot.isChargingIntent()) {
            robot.abortTask();
            setState(robot, RobotState.CHARGING);
        } else {
            setState(robot, RobotState.IDLE);
        }
    }

    private void tickIdle(VirtualRobot robot) {
        if (robot.getBattery() < properties.getLowBatteryThreshold()) {
            String dock = nodeMap.nearestDock(robot.getX(), robot.getY());
            robot.startChargeRun(nodeMap.route(robot.position(), spot(dock, robot.getCode())));
            publishStatus(robot); // now MOVING toward the dock
        } else if (properties.isRoam() && ThreadLocalRandom.current().nextDouble() < ROAM_PROBABILITY) {
            String node = nodeMap.randomRoamNode(ThreadLocalRandom.current());
            robot.startRoam(nodeMap.route(robot.position(), spot(node, robot.getCode())));
            publishStatus(robot); // now MOVING to a roam target
        }
    }

    /**
     * 노드 안에서 로봇마다 다른 정차 자리를 준다.
     *
     * <p>노드 좌표를 그대로 목적지로 삼으면 같은 노드를 고른 로봇들이 <b>정확히 같은 점</b>에
     * 멈춰 지도에서 완전히 포개진다. 실제 현장에서도 여러 대가 한 지점에 겹쳐 서지 않으므로,
     * 노드 주변에 원형으로 흩어 놓는다(항상 같은 자리 = 재현 가능).
     *
     * <p><b>자리는 고정이 아니라 "비어 있는 곳"으로 고른다.</b> 로봇마다 고정 각도를 주는 방식은
     * 두 번 깨졌다 — 전체 대수로 각도를 나누면 인접 간격이 로봇 지름(1.9)보다 좁았고, 집(도크)
     * 기준으로 나누면 <b>집이 아닌 도크</b>에 선 로봇이 남의 자리와 정확히 겹쳤다(실측:
     * AMR-01·AMR-04가 둘 다 5.1,6.0). 시뮬레이터는 모든 로봇의 위치를 알고 있으니, 실제 현장의
     * AMR처럼 <b>빈자리를 찾아 선다</b>. 자기 자리부터 훑으므로 붐비지 않으면 늘 같은 자리다.
     */
    private double[] spot(String node, String robotCode) {
        double[] p = nodeMap.resolve(node);
        int base = defIndex(robotCode);

        for (double radius : new double[]{PARK_RADIUS, PARK_RADIUS * 2}) {
            for (int k = 0; k < PARK_SLOTS; k++) {
                double angle = 2 * Math.PI * ((base + k) % PARK_SLOTS) / PARK_SLOTS;
                double[] candidate = {p[0] + radius * Math.cos(angle), p[1] + radius * Math.sin(angle)};
                if (isClear(candidate, robotCode)) {
                    return candidate;
                }
            }
        }
        // 여기까지 오면 그 노드가 정말 붐비는 것이다 — 자기 자리로 돌아간다.
        double angle = 2 * Math.PI * base / PARK_SLOTS;
        return new double[]{p[0] + PARK_RADIUS * Math.cos(angle), p[1] + PARK_RADIUS * Math.sin(angle)};
    }

    /** 그 자리에 설 수 있는가 — 다른 로봇과 지름만큼은 떨어져야 한다. */
    private boolean isClear(double[] candidate, String robotCode) {
        for (VirtualRobot other : robots) {
            if (other.getCode().equals(robotCode)) {
                continue;
            }
            if (Math.hypot(other.getX() - candidate[0], other.getY() - candidate[1]) < ROBOT_CLEARANCE) {
                return false;
            }
        }
        return true;
    }

    private int defIndex(String robotCode) {
        List<SimProperties.RobotDef> defs = properties.getRobots();
        for (int i = 0; i < defs.size(); i++) {
            if (defs.get(i).getCode().equals(robotCode)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 서버 경로의 <b>마지막 점만</b> 그 로봇의 정차 자리로 바꾼다.
     *
     * <p>서버는 경로를 노드 <b>정중앙</b>으로 끝낸다(구간 점유 계산의 기준점이라 그래야 한다).
     * 그대로 서면 같은 노드에 온 로봇들이 정확히 한 점에 포개진다 — 지도에서 두 대가 한 대처럼
     * 보였던 원인이다. 마지막 점만 살짝 옮기면 점유 통제(중간 구간)는 그대로 두면서 겹침만 없앤다.
     *
     * <p>중간 웨이포인트는 손대지 않는다 — 그것들이 예약한 레인이고, 옮기면 통제되지 않은
     * 자리를 달리게 된다.
     */
    private List<double[]> parkAtOwnSlot(List<double[]> waypoints, String node, String robotCode) {
        if (waypoints.isEmpty() || node == null || node.isBlank()) {
            return waypoints;
        }
        List<double[]> adjusted = new ArrayList<>(waypoints);
        adjusted.set(adjusted.size() - 1, spot(node, robotCode));
        return adjusted;
    }


    /** 서버가 보낸 waypoints 배열을 읽는다. 없거나 형식이 다르면 빈 목록. */
    private List<double[]> readWaypoints(JsonNode json) {
        JsonNode arr = json.path("waypoints");
        if (!arr.isArray()) {
            return List.of();
        }
        List<double[]> out = new ArrayList<>();
        for (JsonNode p : arr) {
            if (p.isArray() && p.size() >= 2) {
                out.add(new double[]{p.get(0).asDouble(), p.get(1).asDouble()});
            }
        }
        return out;
    }

    /** Handle a downlink command: {@code fleet/{robotCode}/command} with a GOTO payload. */
    private void onCommand(String topic, String payload) {
        String[] parts = topic.split("/");
        if (parts.length != 3) {
            return;
        }
        String robotCode = parts[1];
        JsonNode json;
        try {
            json = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.warn("Malformed command payload on {}: {}", topic, payload);
            return;
        }
        if (!"GOTO".equals(json.path("command").asText())) {
            return;
        }

        synchronized (lock) {
            VirtualRobot robot = byCode.get(robotCode);
            if (robot == null) {
                log.warn("GOTO for unknown robot '{}'. Ignoring.", robotCode);
                return;
            }
            String taskCode = json.path("taskCode").asText();

            // 같은 작업의 두 번째 구간(leg2)이면 이어 붙인다 — 픽업에서 기다리던 로봇이 출발한다.
            if (taskCode.equals(robot.getCurrentTaskCode()) && robot.isAwaitingSecondLeg()) {
                List<double[]> leg2 = readWaypoints(json);
                if (!leg2.isEmpty()) {
                    robot.appendSecondLeg(parkAtOwnSlot(leg2, json.path("destination").asText(), robotCode));
                    log.info("Robot {} got second leg for {} ({} waypoints)", robotCode, taskCode, leg2.size());
                }
                return;
            }

            // Accept unless the robot is already executing a task or charging. In particular a
            // roaming robot (MOVING with no task) must yield: roaming is cosmetic idle motion, and
            // rejecting the GOTO here would orphan the task the server already marked ASSIGNED.
            if (robot.hasTask() || robot.getState() == RobotState.CHARGING) {
                log.warn("Robot {} busy ({}, hasTask={}); ignoring GOTO for {}.",
                        robotCode, robot.getState(), robot.hasTask(), taskCode);
                return;
            }
            // 경로는 서버가 정해서 보낸다(구간 점유 통제 때문). 로봇은 그대로 따라갈 뿐이다.
            List<double[]> waypoints = readWaypoints(json);
            if (waypoints.isEmpty()) {
                // 하위 호환: 웨이포인트 없이 온 GOTO는 로봇이 직접 경로를 만든다.
                double[] origin = spot(json.path("origin").asText(), robotCode);
                double[] destination = spot(json.path("destination").asText(), robotCode);
                robot.assignTask(taskCode,
                        nodeMap.route(robot.position(), origin),
                        nodeMap.route(origin, destination));
            } else {
                // 서버가 준 경로는 픽업까지(leg1)다. 하역까지는 픽업 도착 후 따로 받는다.
                robot.assignFirstLeg(taskCode, parkAtOwnSlot(waypoints, json.path("origin").asText(), robotCode));
            }
            publishStatus(robot);           // now MOVING
            publishTask(robot, "started", null);
            log.info("Robot {} accepted task {} ({} -> {})",
                    robotCode, taskCode, json.path("origin").asText(), json.path("destination").asText());
        }
    }

    // --- publishing helpers (topic/payload contract in docs/mqtt-topics.md) ---

    private void setState(VirtualRobot robot, RobotState state) {
        robot.setState(state);
        publishStatus(robot);
    }

    private void publishStatus(VirtualRobot robot) {
        mqtt.publish("fleet/" + robot.getCode() + "/status", Map.of("status", robot.getState().name()));
    }

    /**
     * 위치 + <b>적재 여부</b>를 함께 발행한다.
     *
     * <p>{@code laden}을 별도 토픽으로 "바뀔 때만" 보내지 않는 이유: 이 프로젝트에서 이미
     * 한 번 겪었다 — 상태 변경만 발행하면 그 한 건이 유실됐을 때 서버와 영구히 어긋난다.
     * 위치는 이동 중 매 tick 나가므로 여기 실어 보내면 <b>자가 복구</b>된다.
     *
     * <p>적재 여부는 로봇이 아는 물리 상태다. 서버도 leg 구조상 추론할 수 있지만
     * (leg1=공차 / leg2=적재), 실제 AMR이라면 파렛트 센서가 알려주는 값이다.
     */
    private void publishPosition(VirtualRobot robot) {
        mqtt.publish("fleet/" + robot.getCode() + "/position",
                Map.of("x", round(robot.getX()), "y", round(robot.getY()),
                        "laden", robot.isPickedUp()));
    }

    /** Publish battery only when the whole-percent value changed (or when forced), to limit noise. */
    private void publishBattery(VirtualRobot robot, boolean force) {
        int percent = robot.getBatteryPercent();
        Integer last = lastBatteryPercent.get(robot.getCode());
        if (force || last == null || last != percent) {
            lastBatteryPercent.put(robot.getCode(), percent);
            mqtt.publish("fleet/" + robot.getCode() + "/battery", Map.of("percent", percent));
        }
    }

    private void publishTask(VirtualRobot robot, String event, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskCode", robot.getCurrentTaskCode());
        payload.put("event", event);
        if (reason != null) {
            payload.put("reason", reason);
        }
        mqtt.publish("fleet/" + robot.getCode() + "/task", payload);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
