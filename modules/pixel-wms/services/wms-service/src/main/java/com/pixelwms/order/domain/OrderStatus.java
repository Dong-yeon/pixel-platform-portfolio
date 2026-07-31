package com.pixelwms.order.domain;

/**
 * 입출고 지시 상태.
 *
 * <p>출고는 {@code CREATED → IN_TRANSIT → COMPLETED} 로 흐른다. IN_TRANSIT 은 fleet에 운송
 * 작업을 만들어 둔 상태이고, 재고 차감은 <b>운송이 실제로 끝났을 때</b>(COMPLETED) 일어난다 —
 * 지시를 냈다고 물건이 옮겨진 게 아니기 때문이다.
 */
public enum OrderStatus {
    CREATED,
    IN_TRANSIT,
    COMPLETED,
    CANCELLED
}
