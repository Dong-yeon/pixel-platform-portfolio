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

    public OrderService(
            InboundOrderRepository inboundRepository,
            OutboundOrderRepository outboundRepository,
            LocationRepository locationRepository,
            ItemService itemService,
            StockService stockService,
            FleetTaskClient fleetTaskClient
    ) {
        this.inboundRepository = inboundRepository;
        this.outboundRepository = outboundRepository;
        this.locationRepository = locationRepository;
        this.itemService = itemService;
        this.stockService = stockService;
        this.fleetTaskClient = fleetTaskClient;
    }

    // ---- 입고 ----

    /** 입고는 운송을 거치지 않는 데모 단순화 — 지시 즉시 재고에 반영한다. */
    @Transactional
    public InboundOrderResponse createInbound(InboundOrderCreateRequest request) {
        if (inboundRepository.existsByOrderNo(request.orderNo())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 존재하는 지시번호입니다: " + request.orderNo());
        }
        Item item = itemService.requireItem(request.itemCode());
        Location location = requireLocation(request.locationCode());

        InboundOrder order = inboundRepository.save(
                new InboundOrder(request.orderNo(), item.getId(), location.getId(), request.quantity()));

        stockService.receive(location.getId(), item.getId(), request.quantity(), order.getOrderNo());
        order.complete(LocalDateTime.now());

        return toResponse(order, item.getItemCode(), location.getLocationCode());
    }

    public List<InboundOrderResponse> getInboundOrders() {
        Map<Long, String> itemCodes = itemCodeById();
        Map<Long, String> locationCodes = locationCodeById();
        return inboundRepository.findByOrderByIdDesc().stream()
                .map(o -> toResponse(o,
                        itemCodes.getOrDefault(o.getItemId(), "?"),
                        locationCodes.getOrDefault(o.getLocationId(), "?")))
                .toList();
    }

    // ---- 출고 ----

    /**
     * 출고지시 생성 → fleet에 운송 작업 요청.
     *
     * <p>재고가 모자라면 운송을 만들지 않는다(로봇을 헛되이 보내지 않는다). 작업 생성이
     * 실패하면 예외로 트랜잭션이 되감겨 "운송 없는 출고지시"가 남지 않는다.
     */
    @Transactional
    public OutboundOrderResponse createOutbound(OutboundOrderCreateRequest request) {
        if (outboundRepository.existsByOrderNo(request.orderNo())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 존재하는 지시번호입니다: " + request.orderNo());
        }
        Item item = itemService.requireItem(request.itemCode());
        Location from = requireLocation(request.fromLocationCode());

        int available = stockService.availableQuantity(from.getId(), item.getId());
        if (available < request.quantity()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "재고가 부족합니다. 가용 " + available + ", 요청 " + request.quantity());
        }

        OutboundOrder order = outboundRepository.save(new OutboundOrder(
                request.orderNo(), item.getId(), from.getId(), request.toNodeCode(), request.quantity()));

        // 작업 코드는 지시번호에서 파생 — 완료 통지에서 지시를 되찾는 열쇠다.
        String taskCode = "WMS-" + order.getOrderNo();
        fleetTaskClient.createTask(taskCode, from.getNodeCode(), request.toNodeCode(), DEFAULT_TASK_PRIORITY);
        order.markInTransit(taskCode);

        return toResponse(order, item.getItemCode(), from.getLocationCode());
    }

    /**
     * 운송 완료 통지 처리 — 여기서 비로소 재고가 움직인다.
     *
     * <p>같은 통지가 두 번 와도 재고가 두 번 빠지지 않도록 완료 여부를 먼저 확인한다
     * (MQTT는 최소 1회 전달이라 중복이 정상이다).
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

        stockService.issue(order.getFromLocationId(), order.getItemId(), order.getQuantity(), order.getOrderNo());
        order.complete(LocalDateTime.now());
        log.info("운송 완료 → 재고 차감: {} ({}개)", order.getOrderNo(), order.getQuantity());
    }

    public List<OutboundOrderResponse> getOutboundOrders() {
        Map<Long, String> itemCodes = itemCodeById();
        Map<Long, String> locationCodes = locationCodeById();
        return outboundRepository.findByOrderByIdDesc().stream()
                .map(o -> toResponse(o,
                        itemCodes.getOrDefault(o.getItemId(), "?"),
                        locationCodes.getOrDefault(o.getFromLocationId(), "?")))
                .toList();
    }

    // ---- 내부 ----

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

    private InboundOrderResponse toResponse(InboundOrder order, String itemCode, String locationCode) {
        return new InboundOrderResponse(
                order.getId(), order.getOrderNo(), itemCode, locationCode,
                order.getQuantity(), order.getStatus(), order.getCompletedAt());
    }

    private OutboundOrderResponse toResponse(OutboundOrder order, String itemCode, String fromLocationCode) {
        return new OutboundOrderResponse(
                order.getId(), order.getOrderNo(), itemCode, fromLocationCode, order.getToNodeCode(),
                order.getQuantity(), order.getStatus(), order.getTaskCode(), order.getCompletedAt());
    }
}
