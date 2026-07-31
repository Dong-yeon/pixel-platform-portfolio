package com.pixelfactory.workorder.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record WorkOrderCreateRequest(
        @NotBlank String workOrderNo,
        /** 무엇을 만드는가 — {@code parts.id}(V10에서 실체를 갖게 됐다). */
        @NotNull Long partId,
        @NotNull Long processId,
        @NotNull Long equipmentId,
        @NotNull Long assignedUserId,
        @NotBlank String lotNo,
        @NotNull @Min(1) Integer plannedQty,
        @NotNull @FutureOrPresent LocalDateTime plannedStartAt,
        @NotNull LocalDateTime plannedEndAt
) {
}
