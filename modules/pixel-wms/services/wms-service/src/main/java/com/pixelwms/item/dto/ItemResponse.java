package com.pixelwms.item.dto;

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
        List<StandardCycleTime> standardCycleTimes
) {

    public record StandardCycleTime(String processCode, Integer standardCycleTimeMs) {
    }
}
