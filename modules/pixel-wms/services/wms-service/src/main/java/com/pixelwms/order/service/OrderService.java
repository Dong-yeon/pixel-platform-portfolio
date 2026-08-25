package com.pixelwms.order.service;

import com.pixelwms.fleet.FleetTaskClient;
import com.pixelwms.item.domain.Item;
import com.pixelwms.item.service.ItemService;
import com.pixelwms.order.domain.InboundOrder;
import com.pixelwms.order.domain.OutboundOrder;
import com.pixelwms.order.dto.InboundOrderCreateRequest;
import com.pixelwms.order.dto.InboundOrderResponse;
import com.pixelwms.order.dto.OutboundOrderCreateRequest;
import com.pixelwms.order.dto.OutboundOrderResponse;
import com.pixelwms.order.repository.InboundOrderRepository;
import com.pixelwms.order.repository.OutboundOrderRepository;
import com.pixelwms.stock.domain.Location;
import com.pixelwms.stock.domain.Pallet;
import com.pixelwms.stock.domain.Stock;
import com.pixelwms.stock.repository.LocationRepository;
import com.pixelwms.stock.service.StockService;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입출고 지시.
 *
 * <p><b>출고가 로봇을 움직이는 이유다.</b> 출고지시를 만들면 fleet에 운송 작업을 요청하고,
 * 재고는 지시 시점이 아니라 <b>운송 완료 통지</b>를 받았을 때 차감한다 — 지시를 냈다고
 * 물건이 옮겨진 것은 아니기 때문이다.
 *
 * <p><b>P23 — 파렛트 단위.</b> 입고는 항상 새 파렛트를 만들고, 출고는 항상 파렛트 하나를
 * 통째로 대상으로 한다(부분 피킹 없음 — EMMA 600K는 리프팅식이라 파렛트를 통째로만 옮긴다,
 * 설계 근거: docs/p23-pallet-unit-design.md D5). {@code palletCode}를 생략하면 그 로케이션
 * 안에서 FIFO(입고 빠른 순)로 자동 선택한다 — 기존(P23 이전) 호출부가 이 필드 없이도 그대로
 * 동작하는 이유다(P19의 {@code TaskController} 호환 어댑터와 같은 패턴).
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String DEFAULT_TASK_PRIORITY = "NORMAL";

    private final InboundOrderRepository inboundRepository;
    private final OutboundOrderRepository outboundRepository;
    private final LocationRepository locationRepository;
    private final ItemService itemService;
    private final StockService stockService;
    private final FleetTaskClient fleetTaskClient;
    private final ReplenishmentService replenishmentService;

    public OrderService(
            InboundOrderRepository inboundRepository,
            OutboundOrderRepository outboundRepository,
            LocationRepository locationRepository,
            ItemService itemService,
            StockService stockService,
            FleetTaskClient fleetTaskClient,
            ReplenishmentService replenishmentService
    ) {
        this.inboundRepository = inboundRepository;
        this.outboundRepository = outboundRepository;
        this.locationRepository = locationRepository;
        this.itemService = itemService;
        this.stockService = stockService;
        this.fleetTaskClient = fleetTaskClient;
        this.replenishmentService = replenishmentService;
    }

    // ---- 입고 ----

    /**
     * 입고는 운송을 거치지 않는 데모 단순화 — 지시 즉시 재고에 반영한다.
     *
     * <p>새 파렛트를 하나 만든다(P23). 총중량 상한(500kg)이나 로케이션의 파렛트 슬롯이
     * 꽉 찼으면 {@link StockService#receive}가 거절한다.
     */
    @Transactional
    public InboundOrderResponse createInbound(InboundOrderCreateRequest request) {
        if (inboundRepository.existsByOrderNo(request.orderNo())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 존재하는 지시번호입니다: " + request.orderNo());
        }
        Item item = itemService.requireItem(request.itemCode());
        Location location = requireLocation(request.locationCode());

        Pallet pallet = stockService.receive(location, item, request.quantity(), request.orderNo());

        InboundOrder order = inboundRepository.save(
                new InboundOrder(request.orderNo(), item.getId(), location.getId(), request.quantity(), pallet.getId()));
        order.complete(LocalDateTime.now());

        return toResponse(order, item.getItemCode(), location.getLocationCode(), pallet.getPltCode());
    }

    public List<InboundOrderResponse> getInboundOrders() {
        Map<Long, String> itemCodes = itemCodeById();
        Map<Long, String> locationCodes = locationCodeById();
        Map<Long, String> palletCodes = palletCodeById();
        return inboundRepository.findByOrderByIdDesc().stream()
                .map(o -> toResponse(o,
                        itemCodes.getOrDefault(o.getItemId(), "?"),
                        locationCodes.getOrDefault(o.getLocationId(), "?"),
                        o.getPalletId() == null ? null : palletCodes.get(o.getPalletId())))
                .toList();
    }

    // ---- 출고 ----

    /**
     * 출고지시 생성 → fleet에 운송 작업 요청.
     *
     * <p>파렛트를 고른다(지정됐으면 그 파렛트, 아니면 FIFO 자동 선택) — 그 파렛트의 수량이
     * 요청 수량과 정확히 일치해야 한다(D5, 부분 피킹 없음). 작업 생성이 실패하면 예외로
     * 트랜잭션이 되감겨 "운송 없는 출고지시"가 남지 않는다.
     */
    @Transactional
    public OutboundOrderResponse createOutbound(OutboundOrderCreateRequest request) {
        if (outboundRepository.existsByOrderNo(request.orderNo())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 존재하는 지시번호입니다: " + request.orderNo());
        }
        Item item = itemService.requireItem(request.itemCode());
        Location from = requireLocation(request.fromLocationCode());

        Pallet pallet = resolvePallet(request, from, item);
        Stock stock = stockService.requireStockOfPallet(pallet.getId());
        if (!stock.getItemId().equals(item.getId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "파렛트 " + pallet.getPltCode() + "에는 " + request.itemCode() + "이(가) 실려 있지 않습니다.");
        }
        if (!stock.getQuantity().equals(request.quantity())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "파렛트는 통째로만 옮길 수 있습니다. 파렛트 " + pallet.getPltCode() + "의 수량은 "
                            + stock.getQuantity() + "인데 요청 수량은 " + request.quantity() + "입니다.");
        }

        OutboundOrder order = outboundRepository.save(new OutboundOrder(
                request.orderNo(), item.getId(), from.getId(), pallet.getId(), request.toNodeCode(), request.quantity()));

        // 작업 코드는 지시번호에서 파생 — 완료 통지에서 지시를 되찾는 열쇠다.
        String taskCode = "WMS-" + order.getOrderNo();
        fleetTaskClient.createTask(taskCode, from.getNodeCode(), request.toNodeCode(), DEFAULT_TASK_PRIORITY,
                pallet.getPltCode());
        order.markInTransit(taskCode);
        stockService.reserveForTransit(pallet.getId());

        return toResponse(order, item.getItemCode(), from.getLocationCode(), pallet.getPltCode());
    }

    /**
     * 운송 완료 통지 처리 — 여기서 비로소 재고가 움직인다.
     *
     * <p>같은 통지가 두 번 와도 재고가 두 번 빠지지 않도록 완료 여부를 먼저 확인한다
     * (MQTT는 최소 1회 전달이라 중복이 정상이다). 파렛트를 통째로 소진한다(D5).
     */
    @Transactional
    public void handleTransportCompleted(String taskCode) {
        OutboundOrder order = outboundRepository.findByTaskCode(taskCode).orElse(null);
        if (order == null) {
            return; // WMS가 만들지 않은 작업(데모 생성기 등) — 우리 소관이 아니다.
        }
        if (order.isCompleted()) {
            log.debug("이미 처리된 운송 완료 통지입니다: {}", taskCode);
            return;
        }

        stockService.issuePallet(order.getPalletId(), order.getOrderNo());
        order.complete(LocalDateTime.now());
        log.info("운송 완료 → 재고 차감: {} (파렛트 소진, {}개)", order.getOrderNo(), order.getQuantity());

        // P26 — 이 출고로 방금 줄어든 로케이션이 안전재고 아래로 떨어졌는지 확인한다.
        // 출고 자체는 이미 성공했으므로, 여기서 보충 대상이 없어도 이 트랜잭션을 되감지 않는다.
        replenishmentService.checkAndReplenish(order.getFromLocationId(), order.getItemId());
    }

    public List<OutboundOrderResponse> getOutboundOrders() {
        Map<Long, String> itemCodes = itemCodeById();
        Map<Long, String> locationCodes = locationCodeById();
        Map<Long, String> palletCodes = palletCodeById();
        return outboundRepository.findByOrderByIdDesc().stream()
                .map(o -> toResponse(o,
                        itemCodes.getOrDefault(o.getItemId(), "?"),
                        locationCodes.getOrDefault(o.getFromLocationId(), "?"),
                        palletCodes.getOrDefault(o.getPalletId(), "?")))
                .toList();
    }

    // ---- 내부 ----

    /** palletCode가 지정됐으면 그 파렛트를, 아니면 FIFO로 자동 선택한다(D3). */
    private Pallet resolvePallet(OutboundOrderCreateRequest request, Location from, Item item) {
        if (request.palletCode() != null && !request.palletCode().isBlank()) {
            Pallet pallet = stockService.requireLoadedPallet(request.palletCode());
            if (!pallet.getLocationId().equals(from.getId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "파렛트 " + pallet.getPltCode() + "은(는) " + request.fromLocationCode() + "에 있지 않습니다.");
            }
            return pallet;
        }
        return stockService.findFifoPallet(from.getId(), item.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        "로케이션 " + request.fromLocationCode() + "에 " + request.itemCode()
                                + "을(를) 실은 파렛트가 없습니다."));
    }

    private Location requireLocation(String locationCode) {
        return locationRepository.findByLocationCode(locationCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "로케이션을 찾을 수 없습니다: " + locationCode));
    }

    private Map<Long, String> itemCodeById() {
        return itemService.getItems().stream()
                .collect(Collectors.toMap(i -> i.id(), i -> i.itemCode()));
    }

    private Map<Long, String> locationCodeById() {
        return locationRepository.findAll().stream()
                .collect(Collectors.toMap(Location::getId, Location::getLocationCode));
    }

    private Map<Long, String> palletCodeById() {
        return stockService.getAllPallets().stream()
                .collect(Collectors.toMap(Pallet::getId, Pallet::getPltCode));
    }

    private InboundOrderResponse toResponse(InboundOrder order, String itemCode, String locationCode, String palletCode) {
        return new InboundOrderResponse(
                order.getId(), order.getOrderNo(), itemCode, locationCode,
                order.getQuantity(), palletCode, order.getStatus(), order.getCompletedAt());
    }

    private OutboundOrderResponse toResponse(OutboundOrder order, String itemCode, String fromLocationCode, String palletCode) {
        return new OutboundOrderResponse(
                order.getId(), order.getOrderNo(), itemCode, fromLocationCode, palletCode, order.getToNodeCode(),
                order.getQuantity(), order.getStatus(), order.getTaskCode(), order.getCompletedAt());
    }
}
