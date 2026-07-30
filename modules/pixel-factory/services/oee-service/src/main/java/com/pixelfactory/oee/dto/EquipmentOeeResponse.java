package com.pixelfactory.oee.dto;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.oee.domain.OeeResult;
import java.time.LocalDateTime;

/**
 * 설비 1대의 OEE. 비율과 함께 <b>원값(계획·실가동 분, 생산·불량 수)</b>을 같이 낸다 —
 * 숫자만 보고는 왜 그런지 알 수 없다.
 *
 * @param performanceAnomaly P가 1.0을 넘었다는 신호. 표준CT가 실제보다 크다는 뜻이다.
 */
public record EquipmentOeeResponse(
        String equipmentCode,
        String name,
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
        long defectQty
) {

    public static EquipmentOeeResponse of(
            Equipment equipment,
            LocalDateTime from,
            LocalDateTime to,
            OeeResult result
    ) {
        return new EquipmentOeeResponse(
                equipment.getEquipmentCode(),
                equipment.getName(),
                from,
                to,
                result.availability(),
                result.performance(),
                result.quality(),
                result.oee(),
                result.performanceAnomaly(),
                result.plannedTime().toMinutes(),
                result.operatingTime().toMinutes(),
                result.producedQty(),
                result.defectQty()
        );
    }
}
