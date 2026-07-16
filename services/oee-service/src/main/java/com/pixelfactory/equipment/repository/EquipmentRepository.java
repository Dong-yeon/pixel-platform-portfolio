package com.pixelfactory.equipment.repository;

import com.pixelfactory.equipment.domain.Equipment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByEquipmentCode(String equipmentCode);
}
