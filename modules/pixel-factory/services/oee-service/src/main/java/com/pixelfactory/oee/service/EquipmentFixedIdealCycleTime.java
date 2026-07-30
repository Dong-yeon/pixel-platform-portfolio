package com.pixelfactory.oee.service;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.repository.EquipmentRepository;
import org.springframework.stereotype.Component;

/**
 * 현재 구현 — 설비 마스터의 고정값을 그대로 쓴다.
 *
 * <p>품번을 무시하므로 품종을 전환하면 P가 왜곡된다(D6). 그 왜곡은 숨지 않는다 —
 * P가 1.0을 넘으면 {@code performanceAnomaly} 플래그가 선다.
 */
@Component
public class EquipmentFixedIdealCycleTime implements IdealCycleTimeProvider {

    private final EquipmentRepository equipmentRepository;

    public EquipmentFixedIdealCycleTime(EquipmentRepository equipmentRepository) {
        this.equipmentRepository = equipmentRepository;
    }

    @Override
    public long idealCycleTimeMs(Long equipmentId, Long itemId) {
        return equipmentRepository.findById(equipmentId)
                .map(Equipment::getIdealCycleTimeMs)
                .orElse(0);
    }
}
