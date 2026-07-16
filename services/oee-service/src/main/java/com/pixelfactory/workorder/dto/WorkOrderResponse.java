package com.pixelfactory.workorder.dto;

import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import java.time.LocalDateTime;

public record WorkOrderResponse(
        Long id,
        String workOrderNo,
        Long itemId,
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

    public static WorkOrderResponse from(WorkOrder workOrder) {
        return new WorkOrderResponse(
                workOrder.getId(),
                workOrder.getWorkOrderNo(),
                workOrder.getItemId(),
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
}
