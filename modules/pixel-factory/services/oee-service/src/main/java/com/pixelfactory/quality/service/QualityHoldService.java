package com.pixelfactory.quality.service;

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
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 품질 홀드 — <b>외부 품질 시스템이 현장을 멈추고 다시 돌리는 창구</b>.
 *
 * <p>factory는 누가 호출하는지 모른다(QMS일 수도, 다른 무엇일 수도). 설비를
 * {@code QUALITY_HOLD}로, 작업지시를 {@code ON_HOLD}로 바꾸고 그 사실을 이벤트로 남긴다.
 * 지도의 설비가 주황으로 변했다가 판정 후 돌아오는 것이 이 메서드들의 결과다.
 *
 * <p>설비/작업지시는 <b>코드로 지목</b>한다 — 호출자가 factory의 내부 id를 알 수 없기 때문이다.
 */
@Service
@Transactional
public class QualityHoldService {

    private static final Logger log = LoggerFactory.getLogger(QualityHoldService.class);

    private final EquipmentService equipmentService;
    private final WorkOrderRepository workOrderRepository;
    private final FactoryEventService factoryEventService;

    public QualityHoldService(
            EquipmentService equipmentService,
            WorkOrderRepository workOrderRepository,
            FactoryEventService factoryEventService
    ) {
        this.equipmentService = equipmentService;
        this.workOrderRepository = workOrderRepository;
        this.factoryEventService = factoryEventService;
    }

    /** 심의가 열렸다 — 현장을 멈춘다. */
    public void hold(String equipmentCode, String workOrderNo, String reason, String referenceNo) {
        Equipment equipment = requireEquipment(equipmentCode);
        equipmentService.changeStatus(equipment.getId(), EquipmentStatus.QUALITY_HOLD);

        WorkOrder workOrder = findWorkOrder(workOrderNo);
        if (workOrder != null && workOrder.getStatus() != WorkOrderStatus.ON_HOLD) {
            workOrder.hold(reason);
        }

        factoryEventService.record(
                FactoryEventType.EQUIPMENT_STATUS_CHANGED,
                SourceType.INSPECTION, null,
                TargetType.EQUIPMENT, equipment.getId(),
                workOrder != null ? workOrder.getId() : null,
                workOrder != null ? workOrder.getLotNo() : null,
                EventSeverity.WARNING,
                "품질 홀드: " + equipmentCode + (referenceNo != null ? " (" + referenceNo + ")" : ""),
                "{\"equipmentStatus\":\"QUALITY_HOLD\"}",
                LocalDateTime.now()
        );
        log.info("품질 홀드 적용: 설비 {} / 작업지시 {}", equipmentCode, workOrderNo);
    }

    /**
     * 판정이 끝났다 — 현장을 다시 돌린다.
     *
     * <p>설비를 {@code RUNNING}으로 되돌린다. 시뮬레이터는 상태가 <b>바뀔 때만</b> 발행하므로
     * IDLE로 두면 다음 고장/복귀가 올 때까지 회색으로 남아 "풀렸다"가 화면에 드러나지 않는다.
     */
    public void release(String equipmentCode, String workOrderNo, String decision, String referenceNo) {
        Equipment equipment = requireEquipment(equipmentCode);
        equipmentService.changeStatus(equipment.getId(), EquipmentStatus.RUNNING);

        WorkOrder workOrder = findWorkOrder(workOrderNo);
        if (workOrder != null && workOrder.getStatus() == WorkOrderStatus.ON_HOLD) {
            // ON_HOLD → IN_PROGRESS 는 허용된 전이다(작업지시 상태머신).
            workOrder.start(LocalDateTime.now());
        }

        factoryEventService.record(
                FactoryEventType.EQUIPMENT_STATUS_CHANGED,
                SourceType.INSPECTION, null,
                TargetType.EQUIPMENT, equipment.getId(),
                workOrder != null ? workOrder.getId() : null,
                workOrder != null ? workOrder.getLotNo() : null,
                EventSeverity.SUCCESS,
                "품질 홀드 해제: " + equipmentCode
                        + (decision != null ? " — 판정 " + decision : "")
                        + (referenceNo != null ? " (" + referenceNo + ")" : ""),
                "{\"equipmentStatus\":\"RUNNING\"}",
                LocalDateTime.now()
        );
        log.info("품질 홀드 해제: 설비 {} / 작업지시 {} / 판정 {}", equipmentCode, workOrderNo, decision);
    }

    /** 검사가 시작됐다 — 미사용이던 {@code INSPECTION_STARTED}를 여기서 쓴다. */
    public void recordInspectionStarted(String equipmentCode, String workOrderNo, String lotNo, String inspectionNo) {
        recordInspectionEvent(FactoryEventType.INSPECTION_STARTED, EventSeverity.INFO,
                equipmentCode, workOrderNo, lotNo, "검사 시작: " + inspectionNo);
    }

    /** 검사 판정이 나왔다 — {@code INSPECTION_PASSED}/{@code INSPECTION_FAILED}. */
    public void recordInspectionResult(String equipmentCode, String workOrderNo, String lotNo,
                                       String inspectionNo, boolean passed) {
        recordInspectionEvent(
                passed ? FactoryEventType.INSPECTION_PASSED : FactoryEventType.INSPECTION_FAILED,
                passed ? EventSeverity.SUCCESS : EventSeverity.WARNING,
                equipmentCode, workOrderNo, lotNo,
                (passed ? "검사 합격: " : "검사 불합격: ") + inspectionNo);
    }

    private void recordInspectionEvent(FactoryEventType type, EventSeverity severity,
                                       String equipmentCode, String workOrderNo, String lotNo, String message) {
        Long equipmentId = equipmentCode == null ? null
                : equipmentService.findByCode(equipmentCode).map(Equipment::getId).orElse(null);
        WorkOrder workOrder = findWorkOrder(workOrderNo);

        factoryEventService.record(
                type,
                SourceType.INSPECTION, null,
                equipmentId != null ? TargetType.EQUIPMENT : TargetType.NONE, equipmentId,
                workOrder != null ? workOrder.getId() : null,
                lotNo != null ? lotNo : (workOrder != null ? workOrder.getLotNo() : null),
                severity,
                message,
                null,
                LocalDateTime.now()
        );
    }

    private Equipment requireEquipment(String equipmentCode) {
        return equipmentService.findByCode(equipmentCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "설비를 찾을 수 없습니다: " + equipmentCode));
    }

    /** 작업지시는 선택 사항이다 — 설비만 홀드하는 경우도 있다. */
    private WorkOrder findWorkOrder(String workOrderNo) {
        if (workOrderNo == null || workOrderNo.isBlank()) {
            return null;
        }
        return workOrderRepository.findByWorkOrderNo(workOrderNo).orElse(null);
    }
}
