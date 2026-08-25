package com.pixelwms.order.controller;

import com.pixelwms.order.dto.InboundOrderCreateRequest;
import com.pixelwms.order.dto.InboundOrderResponse;
import com.pixelwms.order.dto.OutboundOrderCreateRequest;
import com.pixelwms.order.dto.OutboundOrderResponse;
import com.pixelwms.order.dto.ReplenishmentOrderResponse;
import com.pixelwms.order.service.OrderService;
import com.pixelwms.order.service.ReplenishmentService;
import com.pixelplatform.core.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 입출고 지시. <b>출고지시가 AMR을 움직인다</b> — 생성 시 fleet에 운송 작업이 만들어지고,
 * 운송이 끝나면 재고가 차감된다.
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;
    private final ReplenishmentService replenishmentService;

    public OrderController(OrderService orderService, ReplenishmentService replenishmentService) {
        this.orderService = orderService;
        this.replenishmentService = replenishmentService;
    }

    @GetMapping("/inbound-orders")
    public ApiResponse<List<InboundOrderResponse>> getInboundOrders() {
        return ApiResponse.ok(orderService.getInboundOrders());
    }

    @PostMapping("/inbound-orders")
    public ApiResponse<InboundOrderResponse> createInbound(@Valid @RequestBody InboundOrderCreateRequest request) {
        return ApiResponse.ok(orderService.createInbound(request));
    }

    @GetMapping("/outbound-orders")
    public ApiResponse<List<OutboundOrderResponse>> getOutboundOrders() {
        return ApiResponse.ok(orderService.getOutboundOrders());
    }

    @PostMapping("/outbound-orders")
    public ApiResponse<OutboundOrderResponse> createOutbound(@Valid @RequestBody OutboundOrderCreateRequest request) {
        return ApiResponse.ok(orderService.createOutbound(request));
    }

    /** 보충 지시(P26) — 생성은 안전재고 감지가 스스로 한다. 조회만 연다(관찰용). */
    @GetMapping("/replenishment-orders")
    public ApiResponse<List<ReplenishmentOrderResponse>> getReplenishmentOrders() {
        return ApiResponse.ok(replenishmentService.getReplenishmentOrders());
    }
}
