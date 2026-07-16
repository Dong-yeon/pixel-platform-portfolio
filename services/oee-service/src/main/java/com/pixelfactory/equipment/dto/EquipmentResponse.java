package com.pixelfactory.equipment.dto;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;

public record EquipmentResponse(
        Long id,
        String equipmentCode,
        String name,
        Long lineId,
        Integer idealCycleTimeMs,
        EquipmentStatus status
) {
    public static EquipmentResponse from(Equipment equipment) {
        return new EquipmentResponse(
                equipment.getId(),
                equipment.getEquipmentCode(),
                equipment.getName(),
                equipment.getLineId(),
                equipment.getIdealCycleTimeMs(),
                equipment.getStatus()
        );
    }
}
