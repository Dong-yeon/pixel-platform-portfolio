package com.pixelwms.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param toNodeCode 도착지 — factory 평면도 노드 코드(SHIPPING, STATION-A1 …).
 *                   fleet은 모르는 코드도 거부하지 않고 해시 좌표로 보내 버리므로 정확해야 한다.
 */
public record OutboundOrderCreateRequest(
        @NotBlank String orderNo,
        @NotBlank String itemCode,
        @NotBlank String fromLocationCode,
        @NotBlank String toNodeCode,
        @NotNull @Min(1) Integer quantity
) {
}
