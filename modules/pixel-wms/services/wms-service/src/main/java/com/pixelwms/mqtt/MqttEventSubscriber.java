package com.pixelwms.mqtt;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    /** 구독 호출을 콜백 스레드에서 떼어내기 위한 전용 스레드 — 이유는 connectComplete 주석 참고. */
    private final ExecutorService subscribeExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "mqtt-subscribe"));
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
        // 구독은 콜백 스레드가 아닌 다른 스레드에서 한다. subscribe()는 SUBACK을 기다리는
        // 블로킹 호출인데, 그 SUBACK을 처리할 주체가 바로 지금 막혀 있는 콜백 스레드라서 교착이 된다.
        //
        // **실측:** 이 자리에서 바로 부르면 "Failed to subscribe to fleet/tasks/# — 응답을
        // 기다리는 중 제한시간 초과"가 나고, 구독이 없으니 완료 통지가 한 건도 안 들어와
        // 출고지시가 전부 IN_TRANSIT에 멈춰 있었다(단일 구간 1층 주문까지). fleet control-service가
        // 같은 교착을 먼저 겪고 같은 방법으로 고쳤는데 여기만 빠져 있었다.
        subscribeExecutor.execute(() -> {
            try {
                client.subscribe(properties.getTopicFilter(), 1);
                log.info("Subscribed to {} on {} (reconnect={})", properties.getTopicFilter(), serverUri, reconnect);
            } catch (MqttException e) {
                log.error("Failed to subscribe to {}", properties.getTopicFilter(), e);
            }
        });
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
        subscribeExecutor.shutdownNow();
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
