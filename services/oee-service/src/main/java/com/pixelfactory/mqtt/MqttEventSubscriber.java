package com.pixelfactory.mqtt;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
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

@Component
public class MqttEventSubscriber implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttEventSubscriber.class);

    private final MqttProperties properties;
    private final MqttMessageHandler messageHandler;
    private MqttClient client;

    public MqttEventSubscriber(MqttProperties properties, MqttMessageHandler messageHandler) {
        this.properties = properties;
        this.messageHandler = messageHandler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        if (!properties.isEnabled()) {
            log.info("MQTT subscriber is disabled (mqtt.enabled=false).");
            return;
        }

        try {
            client = new MqttClient(properties.getBrokerUrl(), properties.getClientId(), new MemoryPersistence());
            client.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(5);

            client.connect(options);
        } catch (MqttException e) {
            // Paho only auto-reconnects after the first successful connect, so a broker that is
            // down at startup means no factory events until the application is restarted.
            log.warn("Could not connect to MQTT broker {}. Factory events will not be received. "
                    + "Start the broker (docker compose up mosquitto) and restart the application.",
                    properties.getBrokerUrl(), e);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        try {
            client.subscribe(properties.getTopicFilter(), 1);
            log.info("Subscribed to {} on {} (reconnect={})", properties.getTopicFilter(), serverUri, reconnect);
        } catch (MqttException e) {
            log.error("Failed to subscribe to {}", properties.getTopicFilter(), e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost. Automatic reconnect is enabled.", cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            messageHandler.handle(topic, payload);
        } catch (Exception e) {
            // Never propagate: an exception here would shut down the Paho client connection.
            log.error("Failed to handle MQTT message. topic={}, payload={}", topic, payload, e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Subscriber only — nothing to do.
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
            log.warn("Error while disconnecting MQTT client.", e);
        }
    }
}
