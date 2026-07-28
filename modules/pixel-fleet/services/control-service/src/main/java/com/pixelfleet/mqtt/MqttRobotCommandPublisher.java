package com.pixelfleet.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfleet.command.RobotCommandPublisher;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * MQTT adapter for the robot downlink. Publish-only client, separate from the
 * inbound subscriber, so a failure on one path doesn't take down the other.
 */
@Component
public class MqttRobotCommandPublisher implements RobotCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttRobotCommandPublisher.class);

    private final MqttProperties properties;
    private final ObjectMapper objectMapper;
    private MqttClient client;

    public MqttRobotCommandPublisher(MqttProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        if (!properties.isEnabled()) {
            log.info("MQTT command publisher is disabled (mqtt.enabled=false).");
            return;
        }
        try {
            client = new MqttClient(
                    properties.getBrokerUrl(), properties.getClientId() + "-pub", new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(5);
            client.connect(options);
            log.info("MQTT command publisher connected to {}", properties.getBrokerUrl());
        } catch (MqttException e) {
            log.warn("Could not connect MQTT command publisher to {}. Dispatch commands will not be sent.",
                    properties.getBrokerUrl(), e);
        }
    }

    @Override
    public void sendGoto(
            String robotCode,
            String taskCode,
            String origin,
            String destination,
            java.util.List<double[]> waypoints) {
        if (client == null || !client.isConnected()) {
            log.warn("Downlink not connected; skipping GOTO for robot {} (task {}).", robotCode, taskCode);
            return;
        }
        String topic = "fleet/" + robotCode + "/command";
        try {
            byte[] payload = objectMapper.writeValueAsBytes(Map.of(
                    "command", "GOTO",
                    "taskCode", taskCode,
                    "origin", origin,
                    "destination", destination,
                    // 서버가 정한 경로. 로봇은 이 점들을 순서대로 지난다(구간 점유 통제를 위해).
                    "waypoints", waypoints));
            MqttMessage message = new MqttMessage(payload);
            message.setQos(1);
            client.publish(topic, message);
            log.debug("Sent GOTO to {} for task {} ({} -> {})", robotCode, taskCode, origin, destination);
        } catch (Exception e) {
            log.error("Failed to publish GOTO to {} for task {}", robotCode, taskCode, e);
        }
    }

    @PreDestroy
    public void disconnect() {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException e) {
            log.warn("Error while disconnecting MQTT command publisher.", e);
        }
    }
}
