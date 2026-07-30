package com.pixelfleet.mqtt;

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
            // cleanSession=false — 서비스가 내려가 있는 동안 브로커가 QoS 1 메시지를 큐에
            // 쌓아 재접속 때 밀어 준다(다운타임 동안의 로봇 텔레메트리 유실 방지).
            // clientId 가 고정이어야 세션이 이어지므로 같은 id 로 두 인스턴스를 띄울 수 없다 —
            // 로컬에서 여러 개 띄우려면 MQTT_CLIENT_ID 로 구분할 것.
            options.setCleanSession(false);
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
        // 구독은 콜백 스레드가 아닌 다른 스레드에서 한다.
        // subscribe()는 SUBACK을 기다리는 블로킹 호출인데, 그 SUBACK을 처리할 주체가 바로
        // 지금 막혀 있는 콜백 스레드라서 교착이 된다. cleanSession=false 로 바꾸면 접속 직후
        // 백로그가 동시에 밀려와 콜백 큐가 먼저 차고, 수신이 통째로 멈춘다(factory 에서 실측).
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
            log.warn("Error while disconnecting MQTT client.", e);
        }
    }
}
