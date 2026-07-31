package com.pixelqms.mrb.domain;

/**
 * MRB 심의 상태.
 *
 * <pre>
 * RAISED → UNDER_REVIEW → DECIDED → CLOSED
 * </pre>
 *
 * <p>되돌아가는 전이는 없다 — 심의는 기록이라 취소가 아니라 판정으로 끝낸다.
 */
public enum MrbStatus {
    RAISED,
    UNDER_REVIEW,
    DECIDED,
    CLOSED;

    public boolean canTransitionTo(MrbStatus next) {
        return switch (this) {
            case RAISED -> next == UNDER_REVIEW;
            case UNDER_REVIEW -> next == DECIDED;
            case DECIDED -> next == CLOSED;
            case CLOSED -> false;
        };
    }
}
