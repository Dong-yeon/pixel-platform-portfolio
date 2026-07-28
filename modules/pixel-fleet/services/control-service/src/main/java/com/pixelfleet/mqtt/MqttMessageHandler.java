package com.pixelfleet.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfleet.robot.domain.RobotStatus;
import com.pixelfleet.robot.service.RobotService;
import com.pixelfleet.task.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Routes inbound MQTT telemetry to the domain services. This is the only bridge
 * between the ROS 2 world (via the MQTT broker) and the control server; the topic
 * and payload contract lives in docs/mqtt-topics.md.
 *
 * Topic: fleet/{robotCode}/{kind}   kind ∈ {status, position, battery, task}
 */
@Service
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final RobotService robotService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public MqttMessageHandler(RobotService robotService, TaskService taskService, ObjectMapper objectMapper) {
        this.robotService = robotService;
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    public void handle(String topic, String payload) throws Exception {
        String[] parts = topic.split("/");
        if (parts.length != 3 || !"fleet".equals(parts[0])) {
            log.debug("Ignoring message on unexpected topic: {}", topic);
            return;
        }

        String robotCode = parts[1];
        String kind = parts[2];
        JsonNode json = objectMapper.readTree(payload);

        try {
            switch (kind) {
                case "status" -> handleStatus(robotCode, json, payload);
                case "position" -> handlePosition(robotCode, json, payload);
                case "battery" -> handleBattery(robotCode, json, payload);
                case "task" -> handleTask(json);
                default -> log.debug("Ignoring unsupported message kind '{}' on topic {}", kind, topic);
            }
        } catch (com.pixelfleet.common.exception.BusinessException e) {
            // e.g. telemetry for a robot/task that isn't registered yet — log, don't crash the consumer.
            log.warn("Could not apply telemetry from topic {}: {}", topic, e.getMessage());
        }
    }

    private void handleStatus(String robotCode, JsonNode json, String payload) {
        RobotStatus status;
        try {
            status = RobotStatus.valueOf(json.path("status").asText());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown robot status '{}' from {}", json.path("status").asText(), robotCode);
            return;
        }
        robotService.changeStatus(robotCode, status, payload);
    }

    private void handlePosition(String robotCode, JsonNode json, String payload) {
        double x = json.path("x").asDouble();
        double y = json.path("y").asDouble();
        robotService.updatePosition(robotCode, x, y, payload);
    }

    private void handleBattery(String robotCode, JsonNode json, String payload) {
        int percent = json.path("percent").asInt();
        robotService.updateBattery(robotCode, percent, payload);
    }

    private void handleTask(JsonNode json) {
        String taskCode = json.path("taskCode").asText();
        String event = json.path("event").asText();
        switch (event) {
            case "started" -> taskService.markStarted(taskCode);
            case "completed" -> taskService.markCompleted(taskCode);
            case "failed" -> taskService.markFailed(taskCode, json.path("reason").asText("unknown"));
            default -> log.debug("Ignoring unsupported task event '{}' for {}", event, taskCode);
        }
    }
}
