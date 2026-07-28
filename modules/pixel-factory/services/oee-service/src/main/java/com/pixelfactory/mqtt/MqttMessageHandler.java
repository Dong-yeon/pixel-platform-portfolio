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
import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import com.pixelfactory.workorder.repository.WorkOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final EquipmentService equipmentService;
    private final FactoryEventService factoryEventService;
    private final WorkOrderRepository workOrderRepository;
    private final ObjectMapper objectMapper;

    public MqttMessageHandler(
            EquipmentService equipmentService,
            FactoryEventService factoryEventService,
            WorkOrderRepository workOrderRepository,
            ObjectMapper objectMapper
    ) {
        this.equipmentService = equipmentService;
        this.factoryEventService = factoryEventService;
        this.workOrderRepository = workOrderRepository;
        this.objectMapper = objectMapper;
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

        switch (kind) {
            case "status" -> handleStatus(equipmentCode, equipmentId, json, payload);
            case "cycle" -> handleCycle(equipmentCode, equipmentId, json, payload);
            default -> log.debug("Ignoring unsupported message kind '{}' on topic {}", kind, topic);
        }
    }

    private void handleStatus(String equipmentCode, Long equipmentId, JsonNode json, String payload) {
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
                payload
        );
    }

    private void handleCycle(String equipmentCode, Long equipmentId, JsonNode json, String payload) {
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
                payload
        );
    }
}
