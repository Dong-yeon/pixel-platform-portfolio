package com.pixelwms.item.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 품목 + 공정별 표준CT.
 *
 * <p>factory의 OEE 계산기가 설비 고정값 대신 이 값을 쓰게 하는 게 D6의 목표다.
 */
public record ItemResponse(
        Long id,
        String itemCode,
        String name,
        String unit,
        /** 낱개 단위중량(kg, P23 D4) — 없으면 파렛트 총중량 검증을 안 하는 품목이다. */
        BigDecimal unitWeightKg,
        List<StandardCycleTime> standardCycleTimes
) {

    public record StandardCycleTime(String processCode, Integer standardCycleTimeMs) {
    }
}
