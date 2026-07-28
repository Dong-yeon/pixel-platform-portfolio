package com.pixelfleet.sim.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfleet.sim.config.SimProperties;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thin Paho wrapper for the simulator: publishes telemetry and subscribes to the
 * downlink command topic ({@code fleet/+/command}). Command handling is delegated to
 * the consumer passed at {@link #connect}, so this class doesn't depend on the Simulator.
 */
@Component
public class SimMqttClient implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(SimMqttClient.class);
    private static final String COMMAND_TOPIC_FILTER = "fleet/+/command";

    private final SimProperties properties;
    private final ObjectMapper objectMapper;
    private MqttClient client;
    private BiConsumer<String, String> commandConsumer;

    public SimMqttClient(SimProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void connect(BiConsumer<String, String> commandConsumer) {
        this.commandConsumer = commandConsumer;
        try {
            client = new MqttClient(
                    properties.getBrokerUrl(), properties.getClientId(), new MemoryPersistence());
            client.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(5);
            client.connect(options);
        } catch (MqttException e) {
            log.warn("Could not connect to MQTT broker {}. Start it (docker compose up mosquitto) "
                    + "and restart the simulator.", properties.getBrokerUrl(), e);
        }
    }

    /** Publish a JSON object payload to a topic at QoS 1. Silently no-ops if disconnected. */
    public void publish(String topic, Object payload) {
        if (client == null || !client.isConnected()) {
            return;
        }
        try {
            MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(payload));
            message.setQos(1);
            client.publish(topic, message);
        } catch (Exception e) {
            log.error("Failed to publish to {}", topic, e);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        try {
            client.subscribe(COMMAND_TOPIC_FILTER, 1);
            log.info("Simulator connected to {} and subscribed to {} (reconnect={})",
                    serverUri, COMMAND_TOPIC_FILTER, reconnect);
        } catch (MqttException e) {
            log.error("Failed to subscribe to {}", COMMAND_TOPIC_FILTER, e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        if (commandConsumer == null) {
            return;
        }
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            commandConsumer.accept(topic, payload);
        } catch (Exception e) {
            log.error("Error handling command. topic={}, payload={}", topic, payload, e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost. Automatic reconnect is enabled.", cause);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Nothing to do.
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
            log.warn("Error while disconnecting simulator MQTT client.", e);
        }
    }
}
