package com.pixelwms.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InboundOrderCreateRequest(
        @NotBlank String orderNo,
        @NotBlank String itemCode,
        @NotBlank String locationCode,
        @NotNull @Min(1) Integer quantity
) {
}
