package com.pixelwms.stock.domain;

public enum MovementType {
    /** 입고 — 창고로 들어옴. */
    INBOUND,
    /** 출고 — 창고에서 나감(운송 완료 시점에 차감). */
    OUTBOUND,
    /** 조정 — 실사 등 수기 보정. */
    ADJUSTMENT
}
