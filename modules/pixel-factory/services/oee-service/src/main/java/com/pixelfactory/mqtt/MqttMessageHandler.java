package com.pixelfactory.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.equipment.service.EquipmentService;
import com.pixelfactory.event.domain.EventSeverity;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.SourceType;
import com.pixelfactory.event.domain.TargetType;
import com.pixelfactory.event.service.FactoryEventService;
import com.pixelfactory.quality.QualityEvents;
import com.pixelfactory.quality.QualityProperties;
import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import com.pixelfactory.workorder.repository.WorkOrderRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    /**
     * 이미 검사를 요청한 작업지시 — 임계를 넘은 뒤 사이클마다 다시 요청하지 않기 위한 것이다.
     *
     * <p>메모리에만 둔다. 재기동하면 한 번 더 요청될 수 있지만, QMS가 같은 작업지시의 검사를
     * 중복 생성하지 않으므로(수신 측 멱등) 문제되지 않는다.
     */
    private final Set<String> inspectionRequested = ConcurrentHashMap.newKeySet();

    private final EquipmentService equipmentService;
    private final FactoryEventService factoryEventService;
    private final WorkOrderRepository workOrderRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final QualityProperties qualityProperties;

    public MqttMessageHandler(
            EquipmentService equipmentService,
            FactoryEventService factoryEventService,
            WorkOrderRepository workOrderRepository,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            QualityProperties qualityProperties
    ) {
        this.equipmentService = equipmentService;
        this.factoryEventService = factoryEventService;
        this.workOrderRepository = workOrderRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.qualityProperties = qualityProperties;
    }

    // Topic contract: factory/{lineCode}/{equipmentCode}/{kind} — see docs/mqtt-topics.md
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
        Long equipmentId = equipmentService.findByCode(equipmentCode)
                .map(Equipment::getId)
                .orElse(null);

        if (equipmentId == null) {
            log.warn("Received event for unknown equipment '{}'. Recording without target id.", equipmentCode);
        }

        LocalDateTime occurredAt = resolveOccurredAt(json, equipmentCode);

        switch (kind) {
            case "status" -> handleStatus(equipmentCode, equipmentId, json, payload, occurredAt);
            case "cycle" -> handleCycle(equipmentCode, equipmentId, json, payload, occurredAt);
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

    private void handleStatus(
            String equipmentCode,
            Long equipmentId,
            JsonNode json,
            String payload,
            LocalDateTime occurredAt
    ) {
        EquipmentStatus status;
        try {
            status = EquipmentStatus.valueOf(json.path("status").asText());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown equipment status '{}' from {}", json.path("status").asText(), equipmentCode);
            return;
        }

        if (equipmentId != null) {
            equipmentService.changeStatus(equipmentId, status);
        }

        EventSeverity severity = switch (status) {
            case DOWN -> EventSeverity.ERROR;
            case QUALITY_HOLD -> EventSeverity.WARNING;
            default -> EventSeverity.INFO;
        };

        factoryEventService.record(
                FactoryEventType.EQUIPMENT_STATUS_CHANGED,
                SourceType.EQUIPMENT,
                equipmentId,
                TargetType.EQUIPMENT,
                equipmentId,
                null,
                null,
                severity,
                "Equipment " + equipmentCode + " changed to " + status,
                payload,
                occurredAt
        );
    }

    private void handleCycle(
            String equipmentCode,
            Long equipmentId,
            JsonNode json,
            String payload,
            LocalDateTime occurredAt
    ) {
        boolean defect = json.path("defect").asBoolean(false);

        // 사이클 1회 = 생산 1개. 그 설비에서 진행 중인 작업지시가 있으면 실적을 올린다.
        // (진행 중 작업지시가 없으면 설비만 돌고 실적은 잡히지 않는다 — 실제 현장과 같다.)
        Long workOrderId = null;
        if (equipmentId != null) {
            WorkOrder workOrder = workOrderRepository
                    .findFirstByEquipmentIdAndStatusOrderByIdAsc(equipmentId, WorkOrderStatus.IN_PROGRESS)
                    .orElse(null);
            if (workOrder != null && workOrder.recordCycle(defect)) {
                workOrderId = workOrder.getId();
                requestInspectionIfDefectThresholdExceeded(equipmentCode, workOrder);
            }
        }

        factoryEventService.record(
                FactoryEventType.CYCLE_COMPLETED,
                SourceType.EQUIPMENT,
                equipmentId,
                TargetType.EQUIPMENT,
                equipmentId,
                workOrderId,
                null,
                defect ? EventSeverity.WARNING : EventSeverity.INFO,
                (defect ? "Defect cycle completed: " : "Cycle completed: ") + equipmentCode,
                payload,
                occurredAt
        );
    }

    /**
     * 누적 불량이 임계를 넘으면 검사를 요청한다 — 작업지시당 한 번.
     *
     * <p>factory는 <b>누가 검사하는지 모른다</b>. 커밋 후 토픽에 신호를 던지고, 품질 시스템이
     * 구독해 검사를 만든다. QMS를 내려도 생산은 그대로 돈다(신호만 아무도 안 들을 뿐).
     */
    private void requestInspectionIfDefectThresholdExceeded(String equipmentCode, WorkOrder workOrder) {
        if (workOrder.getDefectQty() < qualityProperties.getDefectThreshold()) {
            return;
        }
        if (!inspectionRequested.add(workOrder.getWorkOrderNo())) {
            return; // 이미 요청했다.
        }

        eventPublisher.publishEvent(new QualityEvents.InspectionRequested(
                equipmentCode,
                workOrder.getWorkOrderNo(),
                workOrder.getLotNo(),
                workOrder.getDefectQty()
        ));
    }
}
