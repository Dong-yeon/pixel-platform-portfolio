package com.pixelwms.order.dto;

import com.pixelwms.order.domain.OrderStatus;
import java.time.LocalDateTime;

public record OutboundOrderResponse(
        Long id,
        String orderNo,
        String itemCode,
        String fromLocationCode,
        String toNodeCode,
        Integer quantity,
        OrderStatus status,
        /** fleet 운송 작업 코드 — 이 작업이 끝나면 재고가 차감된다. */
        String taskCode,
        LocalDateTime completedAt
) {
}
