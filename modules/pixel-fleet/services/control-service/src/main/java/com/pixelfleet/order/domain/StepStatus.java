package com.pixelfleet.order.domain;

/** 스텝 생애주기 — M4 그대로 4종. 전이는 주문 쪽 가드가 함께 지킨다. */
public enum StepStatus {
    EXECUTABLE,
    EXECUTING,
    DONE,
    CANCELLED
}
