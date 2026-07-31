package com.pixelfactory.quality.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param equipmentCode 홀드할 설비 코드(필수) — 호출자는 factory 내부 id를 모른다.
 * @param workOrderNo   함께 멈출 작업지시(선택)
 * @param referenceNo   근거 전표(MRB 번호 등) — 이벤트 메시지에 남는다
 */
public record QualityHoldRequest(
        @NotBlank String equipmentCode,
        String workOrderNo,
        String reason,
        String referenceNo
) {
}
