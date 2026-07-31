package com.pixelfactory.workorder.dto;

import com.pixelfactory.master.domain.Part;
import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import java.time.LocalDateTime;

/**
 * @param partCode  무엇을 만드는지 — 화면이 지시번호만 보고 있던 걸 고친다.
 * @param modelCode 어느 차종용인지. 공용 부품이면 null.
 */
public record WorkOrderResponse(
        Long id,
        String workOrderNo,
        Long partId,
        String partCode,
        String partName,
        String modelCode,
        Long processId,
        Long equipmentId,
        Long assignedUserId,
        String lotNo,
        Integer plannedQty,
        Integer producedQty,
        Integer defectQty,
        WorkOrderStatus status,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String holdReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 품번을 못 붙일 때(마스터가 지워졌다면)도 작업지시는 보여야 하므로 null을 허용한다. */
    public static WorkOrderResponse from(WorkOrder workOrder, Part part, String modelCode) {
        return new WorkOrderResponse(
                workOrder.getId(),
                workOrder.getWorkOrderNo(),
                workOrder.getPartId(),
                part == null ? null : part.getPartCode(),
                part == null ? null : part.getName(),
                modelCode,
                workOrder.getProcessId(),
                workOrder.getEquipmentId(),
                workOrder.getAssignedUserId(),
                workOrder.getLotNo(),
                workOrder.getPlannedQty(),
                workOrder.getProducedQty(),
                workOrder.getDefectQty(),
                workOrder.getStatus(),
                workOrder.getPlannedStartAt(),
                workOrder.getPlannedEndAt(),
                workOrder.getStartedAt(),
                workOrder.getCompletedAt(),
                workOrder.getHoldReason(),
                workOrder.getCreatedAt(),
                workOrder.getUpdatedAt()
        );
    }

    public static WorkOrderResponse from(WorkOrder workOrder) {
        return from(workOrder, null, null);
    }
}
