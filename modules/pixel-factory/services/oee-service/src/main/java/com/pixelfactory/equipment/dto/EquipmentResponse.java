package com.pixelfactory.equipment.dto;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;

/**
 * 설비 응답.
 *
 * <p>평면도 좌표({@code posX}/{@code posY})를 함께 싣는다. 대시보드가 좌표를 하드코딩하지
 * 않고 이 값으로 그리므로, 설비를 옮기면 <b>실시간 채널로 위치까지 함께 갱신</b>된다
 * (별도 layout 재조회가 필요 없다).
 */
public record EquipmentResponse(
        Long id,
        String equipmentCode,
        String name,
        Long lineId,
        Integer idealCycleTimeMs,
        EquipmentStatus status,
        Double posX,
        Double posY
) {
    public static EquipmentResponse from(Equipment equipment) {
        return new EquipmentResponse(
                equipment.getId(),
                equipment.getEquipmentCode(),
                equipment.getName(),
                equipment.getLineId(),
                equipment.getIdealCycleTimeMs(),
                equipment.getStatus(),
                equipment.getPosX(),
                equipment.getPosY()
        );
    }
}
