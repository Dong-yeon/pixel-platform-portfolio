package com.pixelfactory.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfactory.mqtt.MqttProperties;
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
 * 품질 신호를 브로커에 알린다 — factory가 밖으로 내보내는 첫 발행이다.
 *
 * <p>토픽: {@code factory/quality/inspection-requested} — 3마디다. 설비 텔레메트리는 4마디
 * ({@code factory/{lineCode}/{equipmentCode}/{kind}})라서, {@code factory/#}를 구독하는
 * 자기 자신의 핸들러가 이 메시지를 조용히 무시한다(마디 수가 다르면 걸러진다).
 *
 * <p><b>커밋 후에 보낸다.</b> 트랜잭션이 되감기는데 신호가 먼저 나가면, 받은 쪽은 일어나지
 * 않은 불량을 근거로 검사를 만든다.
 */
@Component
public class MqttQualityEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttQualityEventPublisher.class);
    private static final String TOPIC_INSPECTION_REQUESTED = "factory/quality/inspection-requested";

    private final MqttProperties properties;
    private final ObjectMapper objectMapper;
    private MqttClient client;

    public MqttQualityEventPublisher(MqttProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        if (!properties.isEnabled()) {
            log.info("MQTT quality publisher is disabled (mqtt.enabled=false).");
            return;
        }
        try {
            client = new MqttClient(
                    properties.getBrokerUrl(), properties.getClientId() + "-quality-pub", new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(5);
            client.connect(options);
            log.info("MQTT quality publisher connected to {}", properties.getBrokerUrl());
        } catch (MqttException e) {
            log.warn("Could not connect MQTT quality publisher to {}. Inspection requests will not be sent.",
                    properties.getBrokerUrl(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInspectionRequested(QualityEvents.InspectionRequested event) {
        if (client == null || !client.isConnected()) {
            log.warn("Quality publisher not connected; skipping inspection request for {}.", event.workOrderNo());
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("equipmentCode", event.equipmentCode());
            payload.put("workOrderNo", event.workOrderNo());
            payload.put("lotNo", event.lotNo());   // null 허용이라 Map.of를 쓰지 않는다
            payload.put("defectQty", event.defectQty());
            payload.put("ts", Instant.now().toString());

            MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(payload));
            message.setQos(1);
            client.publish(TOPIC_INSPECTION_REQUESTED, message);
            log.info("검사 요청 발행: {} (불량 {}개)", event.workOrderNo(), event.defectQty());
        } catch (Exception e) {
            log.error("Failed to publish inspection request for {}", event.workOrderNo(), e);
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
            log.warn("Error while disconnecting MQTT quality publisher.", e);
        }
    }
}
