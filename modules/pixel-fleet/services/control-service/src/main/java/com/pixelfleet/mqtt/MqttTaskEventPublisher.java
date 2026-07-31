package com.pixelfleet.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfleet.task.event.TaskLifecycleChanged;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.HashMap;
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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 운송 작업 생명주기를 브로커에 알린다 — <b>모듈 간 통지 경로</b>.
 *
 * <p>이전에는 작업 완료가 fleet DB와 대시보드 WebSocket에만 남아, 다른 서비스(WMS 등)가
 * 알 방법이 폴링뿐이었다. 토픽에 발행해 두면 fleet은 여전히 <b>구독자를 모른 채</b>
 * 관심 있는 모듈이 붙을 수 있다(컴포저블).
 *
 * <p>토픽: {@code fleet/tasks/{taskCode}/{event}} — 4마디다. 로봇 텔레메트리는 3마디
 * ({@code fleet/{robotCode}/{kind}})라서, {@code fleet/#}를 구독하는 자기 자신의 핸들러가
 * 이 메시지를 조용히 무시한다(마디 수가 다르면 걸러진다).
 *
 * <p><b>커밋 후에 보낸다.</b> 트랜잭션이 되감기는데 통지가 먼저 나가면, 받은 쪽은 일어나지
 * 않은 완료를 근거로 재고를 차감한다.
 */
@Component
public class MqttTaskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttTaskEventPublisher.class);

    private final MqttProperties properties;
    private final ObjectMapper objectMapper;
    private MqttClient client;

    public MqttTaskEventPublisher(MqttProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        if (!properties.isEnabled()) {
            log.info("MQTT task event publisher is disabled (mqtt.enabled=false).");
            return;
        }
        try {
            client = new MqttClient(
                    properties.getBrokerUrl(), properties.getClientId() + "-task-pub", new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(5);
            client.connect(options);
            log.info("MQTT task event publisher connected to {}", properties.getBrokerUrl());
        } catch (MqttException e) {
            log.warn("Could not connect MQTT task event publisher to {}. Task notifications will not be sent.",
                    properties.getBrokerUrl(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskLifecycleChanged(TaskLifecycleChanged event) {
        if (client == null || !client.isConnected()) {
            log.warn("Task event publisher not connected; skipping {} for task {}.",
                    event.event(), event.taskCode());
            return;
        }
        String topic = "fleet/tasks/" + event.taskCode() + "/" + event.event();
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("taskCode", event.taskCode());
            payload.put("event", event.event());
            payload.put("reason", event.reason());   // null 허용이라 Map.of를 쓰지 않는다
            payload.put("ts", Instant.now().toString());

            MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(payload));
            // QoS 1 — 재고 차감 같은 후속 처리가 걸려 있어 유실보다 중복이 낫다(수신 측이 멱등).
            message.setQos(1);
            client.publish(topic, message);
            log.debug("Published task lifecycle {} for {}", event.event(), event.taskCode());
        } catch (Exception e) {
            log.error("Failed to publish task lifecycle {} for {}", event.event(), event.taskCode(), e);
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
            log.warn("Error while disconnecting MQTT task event publisher.", e);
        }
    }
}
