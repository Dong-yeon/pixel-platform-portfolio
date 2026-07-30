package com.pixelfactory.oee.dto;

import com.pixelfactory.oee.domain.OeeResult;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 라인 OEE + 설비별 내역.
 *
 * <p><b>라인 값은 설비 값의 평균이 아니다</b> — 라인 단위 카운트로 다시 계산한 것이다
 * (근거는 {@code OeeService#ofLine}). 설비별 내역을 함께 실어 어느 설비가 끌어내렸는지
 * 바로 보이게 한다.
 */
public record LineOeeResponse(
        String lineCode,
        LocalDateTime from,
        LocalDateTime to,
        double availability,
        double performance,
        double quality,
        double oee,
        boolean performanceAnomaly,
        long plannedMinutes,
        long operatingMinutes,
        long producedQty,
        long defectQty,
        List<EquipmentOeeResponse> equipments
) {

    public LineOeeResponse(
            String lineCode,
            LocalDateTime from,
            LocalDateTime to,
            OeeResult result,
            List<EquipmentOeeResponse> equipments
    ) {
        this(
                lineCode, from, to,
                result.availability(), result.performance(), result.quality(), result.oee(),
                result.performanceAnomaly(),
                result.plannedTime().toMinutes(), result.operatingTime().toMinutes(),
                result.producedQty(), result.defectQty(),
                equipments
        );
    }
}
