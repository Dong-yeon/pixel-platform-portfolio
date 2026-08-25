package com.pixelwms.stock.service;

import com.pixelwms.item.domain.Item;
import com.pixelwms.stock.domain.Location;
import com.pixelwms.stock.domain.MovementType;
import com.pixelwms.stock.domain.Pallet;
import com.pixelwms.stock.domain.PalletStatus;
import com.pixelwms.stock.domain.Stock;
import com.pixelwms.stock.domain.StockMovement;
import com.pixelwms.stock.dto.PalletResponse;
import com.pixelwms.stock.dto.StockResponse;
import com.pixelwms.stock.repository.PalletRepository;
import com.pixelwms.stock.repository.StockMovementRepository;
import com.pixelwms.stock.repository.StockRepository;
import com.pixelwms.item.repository.ItemRepository;
import com.pixelwms.stock.repository.LocationRepository;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 변경의 유일한 통로 (P23 — 파렛트 단위).
 *
 * <p><b>수량만 고쳐 쓰지 않는다.</b> 모든 증감은 {@link StockMovement} 이력과 함께 기록해
 * "왜 줄었는지"에 답할 수 있게 한다(이벤트 소싱).
 *
 * <p><b>파렛트가 재고의 물리 단위다</b>(설계 근거: docs/p23-pallet-unit-design.md). 입고는
 * 항상 새 파렛트를 만들고, 출고는 항상 파렛트 하나를 통째로 소진한다 — EMMA 600K가 부분
 * 피킹을 못 하는 리프팅식 로봇이기 때문이다(D5).
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private static final int DEFAULT_MOVEMENT_LIMIT = 50;

    /** 파렛트 총중량 상한(사양서 §"물품 사양" — "총중량 500kg 미만"). D4. */
    private static final BigDecimal MAX_PALLET_WEIGHT_KG = BigDecimal.valueOf(500);

    private final StockRepository stockRepository;
    private final PalletRepository palletRepository;
    private final PalletCodeGenerator palletCodeGenerator;
    private final StockMovementRepository movementRepository;
    private final LocationRepository locationRepository;
    private final ItemRepository itemRepository;

    public StockService(
            StockRepository stockRepository,
            PalletRepository palletRepository,
            PalletCodeGenerator palletCodeGenerator,
            StockMovementRepository movementRepository,
            LocationRepository locationRepository,
            ItemRepository itemRepository
    ) {
        this.stockRepository = stockRepository;
        this.palletRepository = palletRepository;
        this.palletCodeGenerator = palletCodeGenerator;
        this.movementRepository = movementRepository;
        this.locationRepository = locationRepository;
        this.itemRepository = itemRepository;
    }

    public List<StockResponse> getStocks() {
        Map<Long, String> itemCodes = itemRepository.findAll().stream()
                .collect(Collectors.toMap(i -> i.getId(), i -> i.getItemCode()));
        Map<Long, Pallet> palletsById = palletRepository.findAll().stream()
                .collect(Collectors.toMap(Pallet::getId, p -> p));
        Map<Long, String> locationCodes = locationRepository.findAll().stream()
                .collect(Collectors.toMap(l -> l.getId(), l -> l.getLocationCode()));

        return stockRepository.findAll().stream()
                .map(s -> {
                    Pallet pallet = palletsById.get(s.getPalletId());
                    String locationCode = pallet == null ? "?" : locationCodes.getOrDefault(pallet.getLocationId(), "?");
                    String pltCode = pallet == null ? "?" : pallet.getPltCode();
                    return new StockResponse(
                            s.getId(), locationCode, pltCode,
                            itemCodes.getOrDefault(s.getItemId(), "?"), s.getQuantity());
                })
                .toList();
    }

    public List<StockMovement> getRecentMovements() {
        return movementRepository.findByOrderByOccurredAtDesc(PageRequest.of(0, DEFAULT_MOVEMENT_LIMIT));
    }

    /** 코드 맵을 만드는 소비 측(OrderService)을 위한 전체 파렛트 조회. */
    public List<Pallet> getAllPallets() {
        return palletRepository.findAll();
    }

    /**
     * 파렛트 목록 — 로케이션 안에 몇 장이 있는지 확인용(P23-3). {@code locationCode}가
     * 없으면 전체를 준다. RETIRED(소진된) 파렛트도 포함한다 — 이력 확인 목적.
     */
    public List<PalletResponse> getPallets(String locationCode) {
        Map<Long, String> locationCodes = locationRepository.findAll().stream()
                .collect(Collectors.toMap(l -> l.getId(), l -> l.getLocationCode()));
        Map<Long, String> itemCodes = itemRepository.findAll().stream()
                .collect(Collectors.toMap(i -> i.getId(), i -> i.getItemCode()));
        Map<Long, Stock> stockByPallet = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getPalletId, s -> s));

        return palletRepository.findAll().stream()
                .filter(p -> locationCode == null || locationCode.isBlank()
                        || locationCode.equals(locationCodes.get(p.getLocationId())))
                .map(p -> {
                    Stock stock = stockByPallet.get(p.getId());
                    return new PalletResponse(
                            p.getPltCode(),
                            locationCodes.getOrDefault(p.getLocationId(), "?"),
                            p.getStatus(),
                            p.getWeightKg(),
                            stock == null ? null : itemCodes.getOrDefault(stock.getItemId(), "?"),
                            stock == null ? null : stock.getQuantity(),
                            stock == null ? null : stock.getLotNo());
                })
                .toList();
    }

    /**
     * 입고 — 새 파렛트를 하나 만들고 그 위에 재고를 싣는다.
     *
     * <p>총중량이 상한(500kg)을 넘거나, 로케이션의 파렛트 슬롯이 이미 찼으면 거절한다
     * (D4·D7). 단위중량이 없는 품목은 중량 검증을 건너뛴다 — 없는 데이터로 억지로 막지
     * 않는다.
     */
    @Transactional
    public Pallet receive(Location location, Item item, int quantity, String referenceNo) {
        assertWeightWithinLimit(item, quantity);
        assertSlotAvailable(location);

        LocalDateTime now = LocalDateTime.now();
        BigDecimal weightKg = item.getUnitWeightKg() == null
                ? null
                : item.getUnitWeightKg().multiply(BigDecimal.valueOf(quantity));

        Pallet pallet = palletRepository.save(
                new Pallet(palletCodeGenerator.next(), location.getId(), weightKg));
        Stock stock = stockRepository.save(
                new Stock(pallet.getId(), item.getId(), quantity, pallet.getPltCode(), now));

        recordMovement(item.getId(), location.getId(), pallet.getId(), quantity, MovementType.INBOUND, referenceNo);
        return pallet;
    }

    /**
     * 출고 대상 파렛트를 고른다 — 같은 로케이션·품목의 파렛트 중 {@code inboundDt}가 가장
     * 빠른 것(FIFO, D3). 없으면 빈 값.
     */
    public Optional<Pallet> findFifoPallet(Long locationId, Long itemId) {
        List<Long> candidateIds = palletRepository.findByLocationIdAndStatus(locationId, PalletStatus.LOADED)
                .stream().map(Pallet::getId).toList();
        if (candidateIds.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Pallet> palletsById = palletRepository.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(Pallet::getId, p -> p));
        return stockRepository.findByPalletIdInAndItemId(candidateIds, itemId).stream()
                .min(Comparator.comparing(Stock::getInboundDt))
                .map(s -> palletsById.get(s.getPalletId()));
    }

    public Pallet requireLoadedPallet(String pltCode) {
        Pallet pallet = palletRepository.findByPltCode(pltCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "파렛트를 찾을 수 없습니다: " + pltCode));
        if (pallet.getStatus() != PalletStatus.LOADED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "파렛트가 출고 가능한 상태가 아닙니다: " + pltCode + " (" + pallet.getStatus() + ")");
        }
        return pallet;
    }

    public Stock requireStockOfPallet(Long palletId) {
        return stockRepository.findByPalletId(palletId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        "파렛트에 실린 재고가 없습니다: id=" + palletId));
    }

    /** 출고지시가 fleet에 운송 작업을 넘기는 시점 — 파렛트를 예약해 다른 출고에 다시 안 쓰이게 한다. */
    @Transactional
    public void reserveForTransit(Long palletId) {
        Pallet pallet = requirePallet(palletId);
        pallet.markInTransit();
    }

    /**
     * 운송 완료 — 파렛트를 통째로 소진한다(부분 잔량이 남지 않는다, D5). 재고 행을 지우고
     * 파렛트를 은퇴시킨다(회수·재사용은 범위 밖).
     */
    @Transactional
    public void issuePallet(Long palletId, String referenceNo) {
        Pallet pallet = requirePallet(palletId);
        Stock stock = requireStockOfPallet(palletId);

        recordMovement(stock.getItemId(), pallet.getLocationId(), pallet.getId(),
                -stock.getQuantity(), MovementType.OUTBOUND, referenceNo);
        stockRepository.delete(stock);
        pallet.markRetired();
    }

    private Pallet requirePallet(Long palletId) {
        return palletRepository.findById(palletId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "파렛트를 찾을 수 없습니다: id=" + palletId));
    }

    private void assertWeightWithinLimit(Item item, int quantity) {
        if (item.getUnitWeightKg() == null) {
            return;
        }
        BigDecimal palletWeight = item.getUnitWeightKg().multiply(BigDecimal.valueOf(quantity));
        if (palletWeight.compareTo(MAX_PALLET_WEIGHT_KG) >= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "파렛트 총중량이 규격을 초과합니다: " + palletWeight + "kg (상한 " + MAX_PALLET_WEIGHT_KG + "kg 미만)");
        }
    }

    private void assertSlotAvailable(Location location) {
        if (location.getMaxPallet() == null) {
            return;
        }
        long occupied = palletRepository.countByLocationIdAndStatusNot(location.getId(), PalletStatus.RETIRED);
        if (occupied >= location.getMaxPallet()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "로케이션의 파렛트 슬롯이 가득 찼습니다: " + location.getLocationCode()
                            + " (" + occupied + "/" + location.getMaxPallet() + ")");
        }
    }

    private void recordMovement(Long itemId, Long locationId, Long palletId, int delta,
                                MovementType type, String referenceNo) {
        movementRepository.save(
                new StockMovement(itemId, locationId, palletId, delta, type, referenceNo, LocalDateTime.now()));
    }
}
