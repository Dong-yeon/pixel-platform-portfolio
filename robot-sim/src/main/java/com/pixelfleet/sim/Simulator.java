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
                double[] home = nodeMap.resolve(def.getHome());
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
            publishTask(robot, "completed", null);
            robot.abortTask();
            setState(robot, RobotState.IDLE);
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
            robot.startChargeRun(nodeMap.resolve(dock));
            publishStatus(robot); // now MOVING toward the dock
        } else if (properties.isRoam() && ThreadLocalRandom.current().nextDouble() < ROAM_PROBABILITY) {
            robot.startRoam(nodeMap.resolve(nodeMap.randomRoamNode(ThreadLocalRandom.current())));
            publishStatus(robot); // now MOVING to a roam target
        }
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
            if (robot.getState() != RobotState.IDLE) {
                log.warn("Robot {} is {} (not IDLE); ignoring GOTO.", robotCode, robot.getState());
                return;
            }
            String taskCode = json.path("taskCode").asText();
            double[] origin = nodeMap.resolve(json.path("origin").asText());
            double[] destination = nodeMap.resolve(json.path("destination").asText());

            robot.assignTask(taskCode, origin, destination);
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

    private void publishPosition(VirtualRobot robot) {
        mqtt.publish("fleet/" + robot.getCode() + "/position",
                Map.of("x", round(robot.getX()), "y", round(robot.getY())));
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
