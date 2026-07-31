package com.pixelfactory.quality.dto;

import jakarta.validation.constraints.NotBlank;

public record QualityReleaseRequest(
        @NotBlank String equipmentCode,
        String workOrderNo,
        /** 심의 판정(USE_AS_IS/REWORK/SCRAP/RETURN) — 이벤트에 남겨 왜 풀렸는지 보이게 한다. */
        String decision,
        String referenceNo
) {
}
