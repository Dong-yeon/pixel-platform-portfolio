package com.pixelwms.stock.dto;

/** 로케이션 × 품목 재고. 코드로 응답해 소비 측이 내부 id에 의존하지 않게 한다. */
public record StockResponse(Long id, String locationCode, String itemCode, Integer quantity) {
}
