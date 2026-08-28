package com.pixelfactory.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.telemetry.service.EquipmentTelemetryService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final EquipmentTelemetryService telemetryService;
    private final ObjectMapper objectMapper;

    public MqttMessageHandler(EquipmentTelemetryService telemetryService, ObjectMapper objectMapper) {
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
    }

    // Topic contract: factory/{lineCode}/{equipmentCode}/{kind} — see docs/mqtt-topics.md
    //
    // P15-1 — 실제 상태 반영·실적 누적·검사요청 로직은 EquipmentTelemetryService로 옮겼다.
    // 여기는 토픽·payload를 파싱해 그 서비스를 호출하는 얇은 어댑터로 남는다 — 관리자
    // REST 경로(ScenarioController)도 같은 서비스를 호출해 완전히 같은 로직을 탄다.
    @Transactional
    public void handle(String topic, String payload) throws Exception {
        String[] parts = topic.split("/");
        if (parts.length != 4 || !"factory".equals(parts[0])) {
            log.debug("Ignoring message on unexpected topic: {}", topic);
            return;
        }

        String equipmentCode = parts[2];
        String kind = parts[3];
        JsonNode json = objectMapper.readTree(payload);
        LocalDateTime occurredAt = resolveOccurredAt(json, equipmentCode);

        switch (kind) {
            case "status" -> handleStatus(equipmentCode, json, payload, occurredAt);
            case "cycle" -> handleCycle(equipmentCode, json, payload, occurredAt);
            default -> log.debug("Ignoring unsupported message kind '{}' on topic {}", kind, topic);
        }
    }

    /**
     * payload의 {@code ts}(설비가 보낸 발생시각)를 읽는다.
     *
     * <p>계약상 UTC ISO-8601(Instant)이다. <b>시스템 기본 시간대로 변환해서 저장한다</b> —
     * {@code createdAt}이 로컬 시각이므로 한쪽만 UTC로 넣으면 같은 테이블에 시차가 생기고,
     * 두 컬럼을 섞어 쓰는 순간 구간 길이가 조용히 틀어진다.
     *
     * <p>파싱이 안 되면 적재 시각으로 폴백한다. 이벤트를 버리는 것보다는 낫지만 그 행의
     * 구간은 파이프라인 지연만큼 틀어지므로 WARN을 남긴다(조용히 넘기면 안 된다).
     */
    private LocalDateTime resolveOccurredAt(JsonNode json, String equipmentCode) {
        String ts = json.path("ts").asText(null);

        if (ts == null || ts.isBlank()) {
            log.warn("Missing ts from {} — falling back to ingest time", equipmentCode);
            return LocalDateTime.now();
        }

        try {
            return LocalDateTime.ofInstant(Instant.parse(ts), ZoneId.systemDefault());
        } catch (DateTimeParseException e) {
            log.warn("Unparseable ts '{}' from {} — falling back to ingest time", ts, equipmentCode);
            return LocalDateTime.now();
        }
    }

    private void handleStatus(String equipmentCode, JsonNode json, String payload, LocalDateTime occurredAt) {
        EquipmentStatus status;
        try {
            status = EquipmentStatus.valueOf(json.path("status").asText());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown equipment status '{}' from {}", json.path("status").asText(), equipmentCode);
            return;
        }
        telemetryService.applyStatus(equipmentCode, status, occurredAt, payload);
    }

    private void handleCycle(String equipmentCode, JsonNode json, String payload, LocalDateTime occurredAt) {
        boolean defect = json.path("defect").asBoolean(false);
        telemetryService.applyCycle(equipmentCode, defect, occurredAt, payload);
    }
}
