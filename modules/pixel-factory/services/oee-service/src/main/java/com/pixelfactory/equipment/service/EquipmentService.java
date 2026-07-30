package com.pixelfactory.equipment.service;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.equipment.dto.EquipmentResponse;
import com.pixelfactory.equipment.dto.ProductionLineResponse;
import com.pixelfactory.equipment.repository.EquipmentRepository;
import com.pixelfactory.equipment.repository.ProductionLineRepository;
import com.pixelfactory.realtime.FactoryRealtimeEvents;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EquipmentService {

    private final ProductionLineRepository productionLineRepository;
    private final EquipmentRepository equipmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EquipmentService(
            ProductionLineRepository productionLineRepository,
            EquipmentRepository equipmentRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.productionLineRepository = productionLineRepository;
        this.equipmentRepository = equipmentRepository;
        this.eventPublisher = eventPublisher;
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
                .ifPresent(equipment -> {
                    equipment.changeStatus(status);
                    // 도메인은 메시징을 모른다 — "바뀌었다"만 알리고, 실제 push는
                    // RealtimeBroadcaster가 커밋 후에 한다.
                    eventPublisher.publishEvent(new FactoryRealtimeEvents.EquipmentStatusChanged(
                            EquipmentResponse.from(equipment)));
                });
    }
}
