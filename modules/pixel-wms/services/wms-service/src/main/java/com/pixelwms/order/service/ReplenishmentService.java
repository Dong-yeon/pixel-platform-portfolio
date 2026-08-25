package com.pixelwms.order.service;

import com.pixelwms.fleet.FleetTaskClient;
import com.pixelwms.item.repository.ItemRepository;
import com.pixelwms.order.domain.OrderStatus;
import com.pixelwms.order.domain.ReplenishmentOrder;
import com.pixelwms.order.dto.ReplenishmentOrderResponse;
import com.pixelwms.order.repository.ReplenishmentOrderRepository;
import com.pixelwms.stock.domain.Location;
import com.pixelwms.stock.domain.Pallet;
import com.pixelwms.stock.domain.Stock;
import com.pixelwms.stock.repository.LocationRepository;
import com.pixelwms.stock.service.StockService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 안전재고 미달 보충 (P26).
 *
 * <p><b>트리거는 출고 완료 시점 하나뿐이다</b>({@link OrderService#handleTransportCompleted}가
 * 부른다) — 지금 재고가 줄어드는 유일한 경로라 주기적 스윕이 필요 없다(설계 근거:
 * docs/p26-safety-stock-replenishment-design.md D4).
 *
 * <p>파렛트 선택(FIFO)·fleet 호출(M4형)·파렛트 예약까지 {@link StockService}·
 * {@link FleetTaskClient}가 P23/P24에서 이미 만든 것을 그대로 재사용한다 — 이 클래스가
 * 새로 하는 일은 "부족한지 판단"과 "완료 시 파렛트를 은퇴 대신 이동시키는 것"뿐이다.
 */
@Service
@Transactional(readOnly = true)
public class ReplenishmentService {

    private static final Logger log = LoggerFactory.getLogger(ReplenishmentService.class);
    /** 보충은 고객 출고와 달리 급하지 않다 — 창고 내부 housekeeping. */
    private static final String DEFAULT_TASK_PRIORITY = "LOW";
    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(OrderStatus.CREATED, OrderStatus.IN_TRANSIT);

    private final ReplenishmentOrderRepository repository;
    private final ReplenishmentCodeGenerator codeGenerator;
    private final LocationRepository locationRepository;
    private final ItemRepository itemRepository;
    private final StockService stockService;
    private final FleetTaskClient fleetTaskClient;

    public ReplenishmentService(
            ReplenishmentOrderRepository repository,
            ReplenishmentCodeGenerator codeGenerator,
            LocationRepository locationRepository,
            ItemRepository itemRepository,
            StockService stockService,
            FleetTaskClient fleetTaskClient
    ) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.locationRepository = locationRepository;
        this.itemRepository = itemRepository;
        this.stockService = stockService;
        this.fleetTaskClient = fleetTaskClient;
    }

    /**
     * 안전재고 판정 — 로케이션이 모니터링 대상이 아니거나 아직 충분하거나 이미 보충
     * 진행 중이면 조용히 넘어간다. 도너가 전혀 없어도 예외를 던지지 않는다(D8) — 이건
     * 방금 성공한 출고 트랜잭션을 되감을 이유가 아니다.
     */
    @Transactional
    public void checkAndReplenish(Long depletedLocationId, Long itemId) {
        Location location = locationRepository.findById(depletedLocationId).orElse(null);
        if (location == null || location.getSafetyStockQty() == null) {
            return; // 모니터링 대상 아님
        }

        int currentQty = stockService.totalQuantityAt(depletedLocationId, itemId);
        if (currentQty >= location.getSafetyStockQty()) {
            return; // 아직 충분
        }

        if (repository.existsByItemIdAndToLocationIdAndStatusIn(itemId, depletedLocationId, ACTIVE_STATUSES)) {
            log.debug("이미 진행 중인 보충이 있습니다: locationId={}, itemId={}", depletedLocationId, itemId);
            return;
        }

        Pallet donor = stockService.findDonorPallet(depletedLocationId, itemId).orElse(null);
        if (donor == null) {
            log.warn("안전재고 미달이지만 보충할 파렛트가 없습니다: {} (재고 {}/{})",
                    location.getLocationCode(), currentQty, location.getSafetyStockQty());
            return;
        }

        Stock donorStock = stockService.requireStockOfPallet(donor.getId());
        Location donorLocation = locationRepository.findById(donor.getLocationId()).orElseThrow();

        String orderNo = codeGenerator.next();
        ReplenishmentOrder order = repository.save(new ReplenishmentOrder(
                orderNo, itemId, donor.getLocationId(), depletedLocationId, donor.getId(), donorStock.getQuantity()));

        String taskCode = "WMS-" + orderNo;
        fleetTaskClient.createTask(taskCode, donorLocation.getNodeCode(), location.getNodeCode(),
                DEFAULT_TASK_PRIORITY, donor.getPltCode());
        order.markInTransit(taskCode);
        stockService.reserveForTransit(donor.getId());

        log.info("보충 지시 생성: {} (파렛트 {} — {} → {})",
                orderNo, donor.getPltCode(), donorLocation.getLocationCode(), location.getLocationCode());
    }

    /**
     * 운송 완료 통지 — 파렛트를 은퇴시키지 않고 도착 로케이션으로 옮긴다(D6). 출고와
     * 같은 이유로 멱등이다(MQTT 최소 1회 전달).
     */
    @Transactional
    public void handleTransportCompleted(String taskCode) {
        ReplenishmentOrder order = repository.findByTaskCode(taskCode).orElse(null);
        if (order == null) {
            return; // 우리가 만든 작업이 아니다
        }
        if (order.isCompleted()) {
            log.debug("이미 처리된 보충 완료 통지입니다: {}", taskCode);
            return;
        }

        stockService.relocatePallet(order.getPalletId(), order.getToLocationId());
        order.complete(LocalDateTime.now());
        log.info("보충 완료: {} (파렛트가 도착 로케이션으로 이동)", order.getOrderNo());
    }

    public List<ReplenishmentOrderResponse> getReplenishmentOrders() {
        Map<Long, String> itemCodes = itemRepository.findAll().stream()
                .collect(Collectors.toMap(i -> i.getId(), i -> i.getItemCode()));
        Map<Long, String> locationCodes = locationRepository.findAll().stream()
                .collect(Collectors.toMap(Location::getId, Location::getLocationCode));
        Map<Long, String> palletCodes = stockService.getAllPallets().stream()
                .collect(Collectors.toMap(Pallet::getId, Pallet::getPltCode));

        return repository.findByOrderByIdDesc().stream()
                .map(o -> new ReplenishmentOrderResponse(
                        o.getId(), o.getOrderNo(),
                        itemCodes.getOrDefault(o.getItemId(), "?"),
                        locationCodes.getOrDefault(o.getFromLocationId(), "?"),
                        locationCodes.getOrDefault(o.getToLocationId(), "?"),
                        palletCodes.getOrDefault(o.getPalletId(), "?"),
                        o.getQuantity(), o.getStatus(), o.getTaskCode(), o.getCompletedAt()))
                .toList();
    }
}
