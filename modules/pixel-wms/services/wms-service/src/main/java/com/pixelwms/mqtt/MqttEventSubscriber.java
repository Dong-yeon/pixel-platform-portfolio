package com.pixelwms.mqtt;

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

/**
 * fleet의 운송 작업 통지를 구독한다.
 *
 * <p><b>왜 REST 콜백이 아닌가.</b> fleet이 WMS의 주소를 알아야 하면 컴포저블이 깨진다.
 * fleet은 토픽에 던지고, 관심 있는 모듈이 각자 붙는다.
 */
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
            // cleanSession=false — WMS가 잠깐 내려가 있는 동안의 완료 통지도 재접속 후 받는다.
            // 재고 차감이 걸린 메시지라 유실되면 전표가 영원히 IN_TRANSIT으로 남는다.
            options.setCleanSession(false);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(5);
            client.connect(options);
            log.info("MQTT subscriber connected to {}", properties.getBrokerUrl());
        } catch (MqttException e) {
            log.warn("Could not connect MQTT subscriber to {}. Transport notifications will not arrive.",
                    properties.getBrokerUrl(), e);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        try {
            client.subscribe(properties.getTopicFilter(), 1);
            log.info("Subscribed to {} (reconnect={})", properties.getTopicFilter(), reconnect);
        } catch (MqttException e) {
            log.error("Failed to subscribe to {}", properties.getTopicFilter(), e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            messageHandler.handle(topic, payload);
        } catch (Exception e) {
            // 메시지 하나가 구독 전체를 끊지 않게 한다.
            log.error("Failed to handle message from {}: {}", topic, payload, e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost. Automatic reconnect is enabled.", cause);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 구독 전용 클라이언트 — 발행하지 않는다.
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
            log.warn("Error while disconnecting MQTT subscriber.", e);
        }
    }
}
