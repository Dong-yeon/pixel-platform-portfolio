package com.pixelfactory.telemetry.service;

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
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설비 상태·사이클 관측 하나를 실제로 반영한다 — 설비 상태 변경, 작업지시 실적 누적,
 * 불량임계 초과 시 검사요청까지.
 *
 * <p><b>P15-1 — {@code MqttMessageHandler}에서 추출.</b> 원래 이 로직은 MQTT 핸들러 안에만
 * 있어서 "지금 이 설비를 고장 내라"를 강제할 방법이 시뮬레이터 프로세스 재시작 말고는
 * 없었다(시뮬레이터는 순수 발행 전용, 명령 채널 없음). 여기로 옮기면 MQTT 경로(실제
 * 텔레메트리)와 관리자 REST 경로({@code ScenarioController}, 데모 이벤트 주입) 둘 다
 * <b>완전히 같은 로직</b>을 탄다 — 시뮬레이터를 흉내 내는 두 번째 코드가 생기지 않는다.
 *
 * <p>관측 출처(실제 MQTT vs 주입)는 이 서비스가 신경 쓰지 않는다 — 호출자가 {@code payloadJson}에
 * 그 사실을 남긴다(주입 경로는 {@code "injected": true}를 넣어 타임라인에서 구분되게 한다).
 */
@Service
public class EquipmentTelemetryService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentTelemetryService.class);

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
    private final ApplicationEventPublisher eventPublisher;
    private final QualityProperties qualityProperties;

    public EquipmentTelemetryService(
            EquipmentService equipmentService,
            FactoryEventService factoryEventService,
            WorkOrderRepository workOrderRepository,
            ApplicationEventPublisher eventPublisher,
            QualityProperties qualityProperties
    ) {
        this.equipmentService = equipmentService;
        this.factoryEventService = factoryEventService;
        this.workOrderRepository = workOrderRepository;
        this.eventPublisher = eventPublisher;
        this.qualityProperties = qualityProperties;
    }

    @Transactional
    public void applyStatus(String equipmentCode, EquipmentStatus status, LocalDateTime occurredAt, String payloadJson) {
        Long equipmentId = equipmentService.findByCode(equipmentCode)
                .map(Equipment::getId)
                .orElse(null);

        if (equipmentId == null) {
            log.warn("Received status event for unknown equipment '{}'. Recording without target id.", equipmentCode);
        } else {
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
                payloadJson,
                occurredAt
        );
    }

    /**
     * @return 진행 중(IN_PROGRESS) 작업지시에 실제로 실적이 반영됐으면 {@code true}.
     *         작업지시가 없거나(설비만 돌고 실적은 안 잡힘) 이미 계획 수량을 채웠으면
     *         {@code false} — 호출자가 "무슨 일이 있었는지"를 구분하고 싶을 때 쓴다.
     */
    @Transactional
    public boolean applyCycle(String equipmentCode, boolean defect, LocalDateTime occurredAt, String payloadJson) {
        Long equipmentId = equipmentService.findByCode(equipmentCode)
                .map(Equipment::getId)
                .orElse(null);

        if (equipmentId == null) {
            log.warn("Received cycle event for unknown equipment '{}'. Recording without target id.", equipmentCode);
        }

        // 사이클 1회 = 생산 1개. 그 설비에서 진행 중인 작업지시가 있으면 실적을 올린다.
        // (진행 중 작업지시가 없으면 설비만 돌고 실적은 잡히지 않는다 — 실제 현장과 같다.)
        Long workOrderId = null;
        boolean recorded = false;
        if (equipmentId != null) {
            WorkOrder workOrder = workOrderRepository
                    .findFirstByEquipmentIdAndStatusOrderByIdAsc(equipmentId, WorkOrderStatus.IN_PROGRESS)
                    .orElse(null);
            if (workOrder != null && workOrder.recordCycle(defect)) {
                workOrderId = workOrder.getId();
                recorded = true;
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
                payloadJson,
                occurredAt
        );
        return recorded;
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
