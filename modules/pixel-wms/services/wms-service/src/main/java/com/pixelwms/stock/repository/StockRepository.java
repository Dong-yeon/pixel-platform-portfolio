package com.pixelwms.stock.repository;

import com.pixelwms.stock.domain.Stock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByPalletId(Long palletId);

    /** FIFO 후보 조회 1단계 — 로케이션의 LOADED 파렛트 id 목록과 조합해서 쓴다(StockService). */
    List<Stock> findByPalletIdInAndItemId(List<Long> palletIds, Long itemId);
}
