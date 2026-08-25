package com.pixelwms.stock.dto;

import com.pixelwms.stock.domain.PalletStatus;
import java.math.BigDecimal;

/** 파렛트 + 그 위에 실린 재고(있으면) — 로케이션 안에 몇 장이 있는지 확인용(P23). */
public record PalletResponse(
        String pltCode,
        String locationCode,
        PalletStatus status,
        BigDecimal weightKg,
        String itemCode,
        Integer quantity,
        String lotNo
) {
}
