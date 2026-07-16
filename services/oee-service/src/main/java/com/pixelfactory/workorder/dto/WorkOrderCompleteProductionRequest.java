package com.pixelfactory.workorder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WorkOrderCompleteProductionRequest(
        @NotNull @Min(0) Integer producedQty,
        @NotNull @Min(0) Integer defectQty
) {
}
