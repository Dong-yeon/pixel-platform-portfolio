package com.pixelfactory.quality.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 검사 시작/판정 통지. 미사용이던 {@code INSPECTION_STARTED/PASSED/FAILED}를 채운다.
 *
 * @param passed 판정 통지일 때만 의미가 있다(시작 통지는 null).
 */
public record InspectionEventRequest(
        String equipmentCode,
        String workOrderNo,
        String lotNo,
        @NotBlank String inspectionNo,
        Boolean passed
) {
}
