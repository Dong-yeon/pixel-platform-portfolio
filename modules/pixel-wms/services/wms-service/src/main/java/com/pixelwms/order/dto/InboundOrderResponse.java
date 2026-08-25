package com.pixelwms.order.dto;

import com.pixelwms.order.domain.OrderStatus;
import java.time.LocalDateTime;

public record InboundOrderResponse(
        Long id,
        String orderNo,
        String itemCode,
        String locationCode,
        Integer quantity,
        /** 이 입고로 새로 만들어진 파렛트(P23). */
        String palletCode,
        OrderStatus status,
        LocalDateTime completedAt
) {
}
