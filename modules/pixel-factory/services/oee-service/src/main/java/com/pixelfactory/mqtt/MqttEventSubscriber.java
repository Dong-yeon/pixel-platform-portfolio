package com.pixelfactory.mqtt;

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
            // cleanSession=false — 브로커가 이 clientId 의 세션을 유지하고, 서비스가 내려가
            // 있는 동안 QoS 1 메시지를 큐에 쌓아 재접속 때 밀어 준다. true 였을 때는
            // 다운타임 동안의 사이클·상태가 통째로 사라져 OEE 구간에 구멍이 났다.
            //
            // 전제 두 가지: (1) 브로커에 persistence 가 켜져 있어야 브로커 재시작도 견딘다,
            // (2) clientId 가 고정이어야 세션이 이어진다 — 그래서 같은 id 로 두 인스턴스를
            //     띄울 수 없다. 로컬에서 여러 개 띄우려면 MQTT_CLIENT_ID 로 구분할 것.
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

    /**
     * 구독은 <b>콜백 스레드가 아닌 다른 스레드</b>에서 해야 한다.
     *
     * <p>{@code connectComplete}은 Paho 콜백 스레드에서 호출되고, {@code subscribe()}는
     * SUBACK을 기다리는 블로킹 호출이다. 그 SUBACK을 처리해야 할 주체가 바로 지금 막혀 있는
     * 콜백 스레드라서 서로를 기다리는 교착이 된다.
     *
     * <p>cleanSession=true 일 때는 접속 직후 밀려올 메시지가 없어 SUBACK이 먼저 도착해
     * 우연히 넘어갔다. false 로 바꾸자 브로커가 큐에 쌓인 백로그를 동시에 밀어넣어
     * 콜백 큐가 먼저 차고, <b>수신이 1건 처리 후 완전히 멈췄다</b>(실측으로 잡았다).
     */
    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
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
