package com.pixelwms.stock.repository;

import com.pixelwms.stock.domain.Stock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByLocationIdAndItemId(Long locationId, Long itemId);

    List<Stock> findByLocationId(Long locationId);
}
