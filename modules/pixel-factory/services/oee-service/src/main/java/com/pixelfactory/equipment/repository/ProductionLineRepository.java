package com.pixelfactory.equipment.repository;

import com.pixelfactory.equipment.domain.ProductionLine;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionLineRepository extends JpaRepository<ProductionLine, Long> {

    Optional<ProductionLine> findByLineCode(String lineCode);
}
