package com.pixelfleet.event.domain;

public enum FleetEventType {
    ROBOT_REGISTERED,
    ROBOT_STATUS_CHANGED,
    ROBOT_POSITION_UPDATED,
    ROBOT_BATTERY_LOW,
    ROBOT_OFFLINE,
    TASK_CREATED,
    TASK_ASSIGNED,
    TASK_STARTED,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_RETRIED,
    TASK_CANCELLED,
    /** P20-4: 레인그래프 엣지가 막힘 — 원인은 message에 담는다(대상 없음, TargetType.NONE). */
    LAYOUT_OBSTACLE_ADDED,
    LAYOUT_OBSTACLE_CLEARED
}
