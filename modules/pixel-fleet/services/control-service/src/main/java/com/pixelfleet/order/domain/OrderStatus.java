package com.pixelfleet.order.domain;

import java.util.Set;

/**
 * 주문 생애주기 — M4의 상태 모델을 따른다.
 *
 * <pre>
 *   TO_BE_ALLOCATED ─▶ ALLOCATED ─▶ EXECUTING ─▶ DONE            (봉인 주문)
 *         ▲                │             ├─────▶ PENDING          (미봉인: 스텝 소진 대기)
 *         │   (자동 재시도) │             │          │ add-steps → EXECUTING
 *         └────────────────┴─────────────┘          │ complete-order → DONE
 *   TO_BE_ALLOCATED / ALLOCATED / EXECUTING / PENDING ─▶ CANCELLED (조작자 cancel, P19)
 * </pre>
 *
 * <p><b>FAILED 상태가 없다.</b> M4처럼 실패는 상태가 아니라 {@code fault} 플래그다 —
 * 자동 재시도 예산이 남아 있으면 TO_BE_ALLOCATED로 되돌아가고, 소진되면 상태를
 * 얼린 채 fault를 세워 사람의 retry-failed를 기다린다. 실패를 종료 상태로 만들면
 * 되살릴 때 별도의 전이가 필요해지는데, 플래그면 같은 기계에 그대로 다시 들어간다.
 */
public enum OrderStatus {
    /** 배차 대기. */
    TO_BE_ALLOCATED,
    /** 로봇이 정해져 접근 중(시작 보고 전). */
    ALLOCATED,
    /** 스텝을 실행하는 중. */
    EXECUTING,
    /** 미봉인 주문이 스텝을 소진하고 다음 스텝(add-steps)이나 봉인을 기다린다. 로봇은 잡힌 채다. */
    PENDING,
    DONE,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case TO_BE_ALLOCATED -> Set.of(ALLOCATED, CANCELLED).contains(next);
            case ALLOCATED -> Set.of(EXECUTING, TO_BE_ALLOCATED, CANCELLED).contains(next);
            // CANCELLED 추가(P19 나머지 작업): 취소는 실패가 아니라 정직한 전이다 —
            // faultOut/재시도 경로로 우회하면 failureNum이 거짓으로 오르고 TASK_FAILED가
            // 잘못 찍힌다.
            case EXECUTING -> Set.of(DONE, PENDING, TO_BE_ALLOCATED, CANCELLED).contains(next);
            case PENDING -> Set.of(EXECUTING, DONE, CANCELLED).contains(next);
            case DONE, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == DONE || this == CANCELLED;
    }
}
