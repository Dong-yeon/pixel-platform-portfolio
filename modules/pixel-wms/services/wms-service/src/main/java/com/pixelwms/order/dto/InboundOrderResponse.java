package com.pixelwms.order.dto;

import com.pixelwms.order.domain.OrderStatus;
import java.time.LocalDateTime;

public record InboundOrderResponse(
        Long id,
        String orderNo,
        String itemCode,
        String locationCode,
        Integer quantity,
        OrderStatus status,
        LocalDateTime completedAt
) {
}
