package com.pixelfactory.workorder.domain;

public enum WorkOrderStatus {
    CREATED,
    ASSIGNED,
    MATERIAL_REQUESTED,
    MATERIAL_MOVING,
    READY,
    IN_PROGRESS,
    INSPECTION_WAITING,
    COMPLETED,
    ON_HOLD,
    CANCELLED
}
