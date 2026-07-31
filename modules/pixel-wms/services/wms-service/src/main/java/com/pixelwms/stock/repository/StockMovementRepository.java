package com.pixelwms.stock.repository;

import com.pixelwms.stock.domain.StockMovement;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByOrderByOccurredAtDesc(Pageable pageable);
}
