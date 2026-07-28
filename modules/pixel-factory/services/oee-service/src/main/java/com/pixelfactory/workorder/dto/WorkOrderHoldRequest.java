package com.pixelfactory.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkOrderHoldRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
