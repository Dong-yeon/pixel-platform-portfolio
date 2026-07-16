package com.pixelfactory.equipment.service;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.equipment.dto.EquipmentResponse;
import com.pixelfactory.equipment.dto.ProductionLineResponse;
import com.pixelfactory.equipment.repository.EquipmentRepository;
import com.pixelfactory.equipment.repository.ProductionLineRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EquipmentService {

    private final ProductionLineRepository productionLineRepository;
    private final EquipmentRepository equipmentRepository;

    public EquipmentService(
            ProductionLineRepository productionLineRepository,
            EquipmentRepository equipmentRepository
    ) {
        this.productionLineRepository = productionLineRepository;
        this.equipmentRepository = equipmentRepository;
    }

    public List<ProductionLineResponse> getLines() {
        return productionLineRepository.findAll()
                .stream()
                .map(ProductionLineResponse::from)
                .toList();
    }

    public List<EquipmentResponse> getEquipments() {
        return equipmentRepository.findAll()
                .stream()
                .map(EquipmentResponse::from)
                .toList();
    }

    public Optional<Equipment> findByCode(String equipmentCode) {
        return equipmentRepository.findByEquipmentCode(equipmentCode);
    }

    @Transactional
    public void changeStatus(Long equipmentId, EquipmentStatus status) {
        equipmentRepository.findById(equipmentId)
                .ifPresent(equipment -> equipment.changeStatus(status));
    }
}
