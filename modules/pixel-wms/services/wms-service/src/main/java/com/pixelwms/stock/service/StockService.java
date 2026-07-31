package com.pixelwms.stock.service;

import com.pixelwms.stock.domain.MovementType;
import com.pixelwms.stock.domain.Stock;
import com.pixelwms.stock.domain.StockMovement;
import com.pixelwms.stock.dto.StockResponse;
import com.pixelwms.stock.repository.LocationRepository;
import com.pixelwms.stock.repository.StockMovementRepository;
import com.pixelwms.stock.repository.StockRepository;
import com.pixelwms.item.repository.ItemRepository;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 변경의 유일한 통로.
 *
 * <p><b>수량만 고쳐 쓰지 않는다.</b> 모든 증감은 {@link StockMovement} 이력과 함께 기록해
 * "왜 줄었는지"에 답할 수 있게 한다(이벤트 소싱).
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private static final int DEFAULT_MOVEMENT_LIMIT = 50;

    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final LocationRepository locationRepository;
    private final ItemRepository itemRepository;

    public StockService(
            StockRepository stockRepository,
            StockMovementRepository movementRepository,
            LocationRepository locationRepository,
            ItemRepository itemRepository
    ) {
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.locationRepository = locationRepository;
        this.itemRepository = itemRepository;
    }

    public List<StockResponse> getStocks() {
        Map<Long, String> itemCodes = itemRepository.findAll().stream()
                .collect(Collectors.toMap(i -> i.getId(), i -> i.getItemCode()));
        Map<Long, String> locationCodes = locationRepository.findAll().stream()
                .collect(Collectors.toMap(l -> l.getId(), l -> l.getLocationCode()));

        return stockRepository.findAll().stream()
                .map(s -> new StockResponse(
                        s.getId(),
                        locationCodes.getOrDefault(s.getLocationId(), "?"),
                        itemCodes.getOrDefault(s.getItemId(), "?"),
                        s.getQuantity()))
                .toList();
    }

    public List<StockMovement> getRecentMovements() {
        return movementRepository.findByOrderByOccurredAtDesc(PageRequest.of(0, DEFAULT_MOVEMENT_LIMIT));
    }

    /** 입고 — 없는 (로케이션,품목) 조합이면 재고 행을 새로 만든다. */
    @Transactional
    public void receive(Long locationId, Long itemId, int quantity, String referenceNo) {
        Stock stock = stockRepository.findByLocationIdAndItemId(locationId, itemId)
                .orElseGet(() -> stockRepository.save(new Stock(locationId, itemId, 0)));
        stock.add(quantity);
        recordMovement(itemId, locationId, quantity, MovementType.INBOUND, referenceNo);
    }

    /** 출고 — 재고가 없거나 모자라면 거절한다. */
    @Transactional
    public void issue(Long locationId, Long itemId, int quantity, String referenceNo) {
        Stock stock = stockRepository.findByLocationIdAndItemId(locationId, itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        "해당 로케이션에 품목 재고가 없습니다."));
        stock.subtract(quantity);
        recordMovement(itemId, locationId, -quantity, MovementType.OUTBOUND, referenceNo);
    }

    /** 가용 수량 확인 — 출고지시를 받을 수 있는지 먼저 본다. */
    public int availableQuantity(Long locationId, Long itemId) {
        return stockRepository.findByLocationIdAndItemId(locationId, itemId)
                .map(Stock::getQuantity)
                .orElse(0);
    }

    private void recordMovement(Long itemId, Long locationId, int delta, MovementType type, String referenceNo) {
        movementRepository.save(new StockMovement(itemId, locationId, delta, type, referenceNo, LocalDateTime.now()));
    }
}
