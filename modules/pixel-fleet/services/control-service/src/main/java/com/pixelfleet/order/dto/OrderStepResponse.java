package com.pixelfleet.order.dto;

import com.pixelfleet.order.domain.OrderStep;
import com.pixelfleet.order.domain.StepStatus;

public record OrderStepResponse(
        int stepIndex,
        String locationNode,
        boolean forLoad,
        boolean forUnload,
        StepStatus status
) {

    public static OrderStepResponse from(OrderStep step) {
        return new OrderStepResponse(
                step.getStepIndex(),
                step.getLocationNode(),
                step.isForLoad(),
                step.isForUnload(),
                step.getStatus()
        );
    }
}
