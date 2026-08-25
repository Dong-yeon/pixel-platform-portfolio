package com.pixelwms.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param palletCode   선택 — 특정 파렛트를 지정한다. 생략하면 {@code fromLocationCode}
 *                     안에서 {@code itemCode}를 실은 파렛트 중 FIFO(입고 빠른 순)로
 *                     자동 선택한다(D3). 기존(P23 이전) 호출부는 이 필드 없이 그대로 동작한다.
 * @param quantity     선택된(또는 지정된) 파렛트의 수량과 **정확히 일치**해야 한다 —
 *                     EMMA 600K는 파렛트를 통째로만 옮긴다(D5, 부분 피킹 없음). 일치하지
 *                     않으면 거절한다.
 * @param toNodeCode   도착지 — factory 평면도 노드 코드(SHIPPING, STATION-A1 …).
 *                     fleet은 모르는 코드도 거부하지 않고 해시 좌표로 보내 버리므로 정확해야 한다.
 */
public record OutboundOrderCreateRequest(
        @NotBlank String orderNo,
        @NotBlank String itemCode,
        @NotBlank String fromLocationCode,
        String palletCode,
        @NotBlank String toNodeCode,
        @NotNull @Min(1) Integer quantity
) {
}
