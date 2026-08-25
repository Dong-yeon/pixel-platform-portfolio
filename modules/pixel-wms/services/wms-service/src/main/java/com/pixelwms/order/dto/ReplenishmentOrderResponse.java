package com.pixelwms.order.dto;

import com.pixelwms.order.domain.OrderStatus;
import java.time.LocalDateTime;

public record ReplenishmentOrderResponse(
        Long id,
        String orderNo,
        String itemCode,
        String fromLocationCode,
        String toLocationCode,
        String palletCode,
        Integer quantity,
        OrderStatus status,
        String taskCode,
        LocalDateTime completedAt
) {
}
