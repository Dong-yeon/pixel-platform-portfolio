package com.pixelfleet.sim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfleet.sim.config.SimProperties;
import com.pixelfleet.sim.map.NodeMap;
import com.pixelfleet.sim.map.RackMap;
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
import org.springframework.beans.factory.annotation.Value;
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
    /**
     * 노드가 놓인 세로 연결로를 따라 앞뒤로 서는 자리(노드 중심 기준 y 오프셋).
     * 간격은 로봇 지름(1.9)이라 줄을 서도 겹치지 않는다.
     */
    private static final double[] PARK_OFFSETS = {0, 1.9, -1.9, 3.8, -3.8, 5.7, -5.7};
    /** 로봇 지름. 중심이 이보다 가까우면 지도에서 겹쳐 보인다. */
    private static final double ROBOT_CLEARANCE = 1.9;
    /** 통로에서 이만큼은 떨어져 세운다 — 통로 위 정차는 남의 길을 막는다. */
    private static final double AISLE_KEEP_OUT = 1.2;
    /** 평면도 가장자리에서 이만큼은 안쪽에 세운다(밖으로 밀려나면 지도에서 사라진다). */
    private static final double MAP_MARGIN = 1.5;
    /** 이 tick 수마다 상태를 다시 발행한다(서버와의 상태 불일치 자가 복구). */
    private static final int HEARTBEAT_TICKS = 10;
    private static final String[] FAILURE_REASONS = {
            "obstacle timeout", "localization lost", "path blocked", "estop triggered"
    };

    private final SimProperties properties;
    private final NodeMap nodeMap;
    private final RackMap rackMap;
    private final SimMqttClient mqtt;
    private final ObjectMapper objectMapper;
    /**
     * 랙 피더가 렉 앞에서 멈춰 취출을 표현하는 시간(tick 수, P21). 없는 데이터를 애니메이션으로
     * 지어내지 않는다는 원칙에 따라 "몇 단에서 꺼내는지"는 흉내내지 않고 불투명한 정지로만
     * 표현한다 — 화물 엘리베이터의 {@code elevatorTravelSeconds}(fleet)와 같은 패턴이다
     * (설계 근거: docs/p21-warehouse-rack-feeder-design.md D7).
     */
    private final int rackFetchTicks;

    private final Object lock = new Object();
    private final List<VirtualRobot> robots = new ArrayList<>();
    private final Map<String, VirtualRobot> byCode = new HashMap<>();
    private final Map<String, Integer> lastBatteryPercent = new HashMap<>();
    /** 로봇 코드 → 찜해 둔 정차 자리. 같은 자리를 두 대가 고르는 것을 막는다. */
    private final Map<String, double[]> claimedSpot = new HashMap<>();
    /**
     * 로봇 코드 → 사는 층. 위층 노드는 아래층과 <b>좌표가 같으므로</b>(WH-DOCK-2F도 4,3)
     * 층을 모르면 2층 로봇과 1층 로봇이 겹쳐 보인다 — 실제로는 위아래로 떨어져 있다.
     */
    private final Map<String, Integer> floorByCode = new HashMap<>();
    /** 로봇 코드 → 집(충전 베이). 위층은 층마다 베이가 하나뿐이라 충전은 늘 자기 집으로 간다. */
    private final Map<String, String> homeByCode = new HashMap<>();
    /** 하트비트 카운터 — 주기적으로 상태를 다시 알린다(아래 tick 참고). */
    private int tickCount = 0;

    public Simulator(SimProperties properties, NodeMap nodeMap, RackMap rackMap, SimMqttClient mqtt,
                     ObjectMapper objectMapper, @Value("${sim.rack.fetch-seconds:8}") int rackFetchSeconds) {
        this.properties = properties;
        this.nodeMap = nodeMap;
        this.rackMap = rackMap;
        this.mqtt = mqtt;
        this.objectMapper = objectMapper;
        long tickMs = Math.max(1, properties.getTickIntervalMs());
        this.rackFetchTicks = Math.max(1, (int) Math.round(rackFetchSeconds * 1000.0 / tickMs));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        synchronized (lock) {
            // 층·집은 자리를 잡기 <b>전에</b> 다 채워 둔다 — spot()의 간격 검사가 층을 보기 때문이다.
            for (SimProperties.RobotDef def : properties.getRobots()) {
                floorByCode.put(def.getCode(), def.getFloor());
                homeByCode.put(def.getCode(), def.getHome());
            }
            for (SimProperties.RobotDef def : properties.getRobots()) {
                double[] home = spot(def.getHome(), def.getCode());
                VirtualRobot robot = new VirtualRobot(def.getCode(), def.getName(), def.getType(), home[0], home[1]);
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
                    // 다음 레그를 기다리는 중이면 step-done을 다시 알린다 — 그 한 건이
                    // 유실되면 서버는 로봇이 아직 달리는 줄 알고, 로봇은 영원히 기다린다
                    // (워치독은 진행 보고가 "끊긴" 것만 본다). 서버는 중복을 무시하거나,
                    // 이미 닫힌 주문이면 ORDER_DONE을 재송신해 로봇을 풀어 준다.
                    if (robot.isAwaitingNextLeg()) {
                        publishTask(robot, "step-done", null);
                    }
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
        // 랙 피더가 렉 앞에서 취출 중(P21) — 이동은 이미 끝났고, 배터리만 계속 닳는다
        // (팔을 뻗어 꺼내는 동안도 로봇은 켜져 있다). 위치는 안 바뀌므로 다시 발행하지 않는다.
        if (robot.isRetrieving()) {
            robot.drain(properties.getBatteryDrainPerTick());
            publishBattery(robot, false);
            if (robot.tickRetrieval()) {
                finishLeg(robot);
            }
            return;
        }

        boolean reached = robot.advanceToward(properties.getSpeed());
        robot.drain(properties.getBatteryDrainPerTick());
        publishPosition(robot);
        publishBattery(robot, false);

        // An order can fail mid-route; this exercises the control server's retry path.
        if (robot.hasOrder() && ThreadLocalRandom.current().nextDouble() < properties.getFailureRate()) {
            String reason = FAILURE_REASONS[ThreadLocalRandom.current().nextInt(FAILURE_REASONS.length)];
            publishTask(robot, "failed", reason);
            robot.clearOrder();
            setState(robot, RobotState.IDLE);
            return;
        }

        if (!reached || robot.hasPath()) {
            return; // 레그 중간 웨이포인트 — 계속 간다.
        }
        // 레그 목적지 도착.
        if (robot.hasOrder()) {
            if (isRackFeeder(robot) && robot.isForLoadAtTarget() && rackMap.isRackCode(robot.getCurrentLocation())) {
                // 렉 정면에 도착 — 아직 완료가 아니다. 몇 단에서 꺼내는지는 지어내지 않고
                // (없는 데이터를 시각효과로 만들지 않는다), 불투명한 정지로만 취출을 표현한다.
                robot.beginRetrieval(rackFetchTicks);
                return;
            }
            finishLeg(robot);
        } else if (robot.isChargingIntent()) {
            robot.clearOrder();
            setState(robot, RobotState.CHARGING);
        } else {
            setState(robot, RobotState.IDLE);
        }
    }

    /** 스텝 완료 — 싣기/내리기를 적재 상태에 반영하고 서버에 보고한 뒤, 다음 레그를 기다린다. */
    private void finishLeg(VirtualRobot robot) {
        robot.completeLeg();
        // 적재 상태가 바뀌었을 수 있으니 즉시 알린다 — 위치 채널에 실려 가므로
        // 지도에 파렛트가 바로 나타나거나 사라진다(하트비트를 기다리면 최대 10초 늦다).
        publishPosition(robot);
        publishTask(robot, "step-done", null);
    }

    private boolean isRackFeeder(VirtualRobot robot) {
        return "RACK_FEEDER".equals(robot.getRobotType());
    }

    private void tickIdle(VirtualRobot robot) {
        if (robot.getBattery() < properties.getLowBatteryThreshold()) {
            robot.startChargeRun(nodeMap.route(robot.position(), spot(dock(robot), robot.getCode())));
            publishStatus(robot); // now MOVING toward the dock
        } else if (properties.isRoam() && ThreadLocalRandom.current().nextDouble() < ROAM_PROBABILITY) {
            String node = nodeMap.randomRoamNode(ThreadLocalRandom.current(), floorOf(robot.getCode()));
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
     * 기준으로 나누면 <b>집이 아닌 도크</b>에 선 로봇이 남의 자리와 정확히 겹쳤다.
     *
     * <p><b>세로로 줄지어 선다(원형이 아니라).</b> 평면도는 "커넥터 선 위"에서만 렉과의 간격을
     * 보장한다(렉을 커넥터에서 1.75 이상 떨어뜨렸다). 노드 둘레로 흩으면 그 보장을 벗어나
     * 로봇이 선반 안에 서 있는 그림이 된다 — 실측으로 40샘플 중 22건이 그랬다. 그래서 노드가
     * 놓인 세로 연결로를 따라 앞뒤로 줄을 선다. 실제 AMR이 통로에 줄 서는 모습과도 같다.
     */
    private double[] spot(String node, String robotCode) {
        double[] p = nodeMap.resolve(node);
        int base = defIndex(robotCode);

        // 다 막혔을 때를 대비해 "그나마 제일 널널한 자리"를 함께 기억한다. 예전에는 노드
        // 정중앙으로 되돌아갔는데, 그 폴백이 간격을 무시해서 **자리가 부족할수록 오히려 겹쳤다**
        // (실측: AMR-03·06이 둘 다 4,21). 붐비면 조금 멀리 세울지언정 포개지 않는다.
        double[] roomiest = {p[0], p[1]};
        double roomiestGap = -1;

        for (int k = 0; k < PARK_OFFSETS.length; k++) {
            double offset = PARK_OFFSETS[(base + k) % PARK_OFFSETS.length];
            double[] candidate = {p[0], p[1] + offset};
            // 평면도 밖으로 밀려나면 지도에서 사라진다(실측: y=-0.8, y=26.8에 선 로봇이 있었다).
            if (candidate[1] < MAP_MARGIN || candidate[1] > NodeMap.MAX_Y - MAP_MARGIN) {
                continue;
            }
            // 통로 위에 세우면 지나가는 로봇을 막는 그림이 된다.
            if (onAisle(candidate[1])) {
                continue;
            }
            double gap = nearestOccupiedDistance(candidate, robotCode);
            if (gap >= ROBOT_CLEARANCE) {
                claimedSpot.put(robotCode, candidate);
                return candidate;
            }
            if (gap > roomiestGap) {
                roomiestGap = gap;
                roomiest = candidate;
            }
        }

        claimedSpot.put(robotCode, roomiest);
        return roomiest;
    }

    /**
     * 충전하러 갈 베이.
     *
     * <p>1층은 충전존에 베이가 넷이라 가까운 곳을 고르지만, 위층은 층마다 하나뿐이다.
     * {@code nearestDock}은 지상 베이만 알고 있어서(층이 다른 베이는 좌표가 겹쳐 구분되지 않는다)
     * 위층 로봇에게 물으면 <b>1층 베이</b>를 답한다 — 로봇이 층을 넘어 사라지는 그림이 된다.
     */
    private String dock(VirtualRobot robot) {
        if (floorOf(robot.getCode()) == 1) {
            return nodeMap.nearestDock(robot.getX(), robot.getY());
        }
        return homeByCode.getOrDefault(robot.getCode(), nodeMap.nearestDock(robot.getX(), robot.getY()));
    }

    /** 가로 통로 위인가 — 정차 자리로 쓰면 안 된다. */
    private boolean onAisle(double y) {
        return Math.abs(y - NodeMap.UPPER_AISLE_Y) < AISLE_KEEP_OUT
                || Math.abs(y - NodeMap.LOWER_AISLE_Y) < AISLE_KEEP_OUT;
    }

    /**
     * 그 자리에 설 수 있는가 — 다른 로봇과 지름만큼은 떨어져야 한다.
     *
     * <p><b>현재 위치가 아니라 "가려는 자리"와 견준다.</b> 주차 자리는 출발할 때 정해지는데,
     * 두 로봇이 비슷한 시점에 같은 노드로 향하면 서로를 아직 <i>예전 위치</i>로 보고 같은 자리를
     * 고른다 — 실측으로 AMR-03·06이 둘 다 (4, 21)에 섰다. 찜해 둔 자리를 함께 보면 안 겹친다.
     */
    private double nearestOccupiedDistance(double[] candidate, String robotCode) {
        int floor = floorOf(robotCode);
        double nearest = Double.MAX_VALUE;
        for (VirtualRobot other : robots) {
            if (other.getCode().equals(robotCode)) {
                continue;
            }
            // 층이 다르면 좌표가 같아도 겹친 것이 아니다 — 위아래로 떨어져 있다.
            if (floorOf(other.getCode()) != floor) {
                continue;
            }
            double[] taken = claimedSpot.getOrDefault(other.getCode(), other.position());
            nearest = Math.min(nearest, Math.hypot(taken[0] - candidate[0], taken[1] - candidate[1]));
        }
        return nearest;
    }

    private int floorOf(String robotCode) {
        return floorByCode.getOrDefault(robotCode, 1);
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
     *
     * <p><b>렉 목적지는 건드리지 않는다(P21).</b> {@code NodeMap}은 렉 좌표를 모른다 —
     * 그대로 부르면 해시 폴백 좌표로 덮어써 관제 서버가 계산해 보낸 접근점을 잃는다.
     * 렉은 애초에 한 존에 로봇이 1~2대뿐이라(design doc D10) 겹칠 정차 자리를 나눠 줄
     * 필요도 없다.
     */
    private List<double[]> parkAtOwnSlot(List<double[]> waypoints, String node, String robotCode) {
        if (waypoints.isEmpty() || node == null || node.isBlank() || rackMap.isRackCode(node)) {
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

    /** Handle a downlink command: {@code fleet/{robotCode}/command} — GOTO(레그) 또는 ORDER_DONE. */
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

        synchronized (lock) {
            VirtualRobot robot = byCode.get(robotCode);
            if (robot == null) {
                log.warn("Command for unknown robot '{}'. Ignoring.", robotCode);
                return;
            }
            switch (json.path("command").asText()) {
                case "GOTO" -> onGoto(robot, robotCode, json);
                case "ORDER_DONE" -> onOrderDone(robot, json);
                default -> { /* 모르는 명령은 무시 */ }
            }
        }
    }

    private void onGoto(VirtualRobot robot, String robotCode, JsonNode json) {
        String orderCode = json.path("orderCode").asText();
        int stepIndex = json.path("stepIndex").asInt(-1);
        String location = json.path("location").asText();
        List<double[]> waypoints = readWaypoints(json);
        if (waypoints.isEmpty()) {
            log.warn("GOTO without waypoints for {} (order {}). Ignoring.", robotCode, orderCode);
            return;
        }

        // 같은 주문의 다음 레그 — 스텝을 마치고 기다리던 로봇이 출발한다.
        if (orderCode.equals(robot.getCurrentOrderCode())) {
            if (stepIndex == robot.getCurrentStepIndex()) {
                return; // QoS1 중복 전달 — 이미 이 레그를 받았다.
            }
            robot.assignLeg(orderCode, stepIndex, location,
                    json.path("forLoad").asBoolean(false), json.path("forUnload").asBoolean(false),
                    parkAtOwnSlot(waypoints, location, robotCode));
            log.info("Robot {} got step {} of {} ({} waypoints)", robotCode, stepIndex, orderCode, waypoints.size());
            return;
        }

        // Accept unless the robot is already executing an order or charging. In particular a
        // roaming robot (MOVING with no order) must yield: roaming is cosmetic idle motion, and
        // rejecting the GOTO here would orphan the order the server already marked ALLOCATED.
        if (robot.hasOrder() || robot.getState() == RobotState.CHARGING) {
            log.warn("Robot {} busy ({}, hasOrder={}); ignoring GOTO for {}.",
                    robotCode, robot.getState(), robot.hasOrder(), orderCode);
            return;
        }
        robot.assignLeg(orderCode, stepIndex, location,
                json.path("forLoad").asBoolean(false), json.path("forUnload").asBoolean(false),
                parkAtOwnSlot(waypoints, location, robotCode));
        publishStatus(robot);           // now MOVING
        publishTask(robot, "started", null);
        log.info("Robot {} accepted order {} (step {} -> {})", robotCode, orderCode, stepIndex, location);
    }

    /**
     * 주문 종료 — <b>서버만이 안다.</b> 미봉인 주문은 스텝이 이어질 수 있어서, 로봇은
     * 자기가 마지막 스텝을 달렸는지 스스로 판단하지 않고 이 명령으로 풀려난다.
     */
    private void onOrderDone(VirtualRobot robot, JsonNode json) {
        if (!json.path("orderCode").asText().equals(robot.getCurrentOrderCode())) {
            return;
        }
        robot.clearOrder();
        setState(robot, RobotState.IDLE);
        publishPosition(robot); // 파렛트가 사라진 것을 즉시 알린다.
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
                        "laden", robot.isLaden()));
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
        payload.put("orderCode", robot.getCurrentOrderCode());
        payload.put("event", event);
        if ("step-done".equals(event)) {
            payload.put("stepIndex", robot.getCurrentStepIndex());
        }
        if (reason != null) {
            payload.put("reason", reason);
        }
        mqtt.publish("fleet/" + robot.getCode() + "/task", payload);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
