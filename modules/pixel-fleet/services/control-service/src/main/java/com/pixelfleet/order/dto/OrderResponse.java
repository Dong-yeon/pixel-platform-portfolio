package com.pixelfleet.order.dto;

import com.pixelfleet.order.domain.FleetOrder;
import com.pixelfleet.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * M4 모양 주문 뷰 — {@code task.dto.TaskResponse}(호환 어댑터)와 달리 눕히지 않고
 * 주문의 실제 모양(스텝 리스트, suspended/fault 플래그)을 그대로 노출한다.
 */
public record OrderResponse(
        Long id,
        String orderCode,
        String externalId,
        int priority,
        OrderStatus status,
        Long assignedRobotId,
        short floorNo,
        int currentStepIndex,
        boolean loaded,
        boolean stepFixed,
        boolean suspended,
        boolean fault,
        int failureNum,
        String failureReason,
        LocalDateTime assignedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<OrderStepResponse> steps,
        /** 물리 단위 식별자(P23 D6, P24 D2) — WMS라면 파렛트 코드. 없으면 null. */
        String materialId
) {

    public static OrderResponse from(FleetOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getExternalId(),
                order.getPriority(),
                order.getStatus(),
                order.getAssignedRobotId(),
                order.getFloorNo(),
                order.getCurrentStepIndex(),
                order.isLoaded(),
                order.isStepFixed(),
                order.isSuspended(),
                order.isFault(),
                order.getFailureNum(),
                order.getFailureReason(),
                order.getAssignedAt(),
                order.getStartedAt(),
                order.getFinishedAt(),
                order.getSteps().stream().map(OrderStepResponse::from).toList(),
                order.getMaterialId()
        );
    }
}
