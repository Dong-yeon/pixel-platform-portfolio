package com.pixelqms.inspection.dto;

import com.pixelqms.inspection.domain.InspectionResult;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 검사 판정.
 *
 * @param result FAILED면 부적합(NCR)이 함께 만들어진다.
 */
public record InspectionCompleteRequest(
        @NotNull InspectionResult result,
        @Min(0) Integer inspectedQty,
        @Min(0) Integer defectQty,
        String note,
        /** 불합격일 때 불량 유형 코드(DIM, SURFACE …). */
        String defectCode
) {
}
