package com.pixelfactory.workorder.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record WorkOrderCreateRequest(
        @NotBlank String workOrderNo,
        @NotNull Long itemId,
        @NotNull Long processId,
        @NotNull Long equipmentId,
        @NotNull Long assignedUserId,
        @NotBlank String lotNo,
        @NotNull @Min(1) Integer plannedQty,
        @NotNull @FutureOrPresent LocalDateTime plannedStartAt,
        @NotNull LocalDateTime plannedEndAt
) {
}
