package com.pixelfleet.order.controller;

import com.pixelfleet.order.dto.CreateOrderRequest;
import com.pixelfleet.order.dto.OrderResponse;
import com.pixelfleet.order.service.OrderCodeGenerator;
import com.pixelfleet.order.service.OrderService;
import com.pixelfleet.order.service.OrderService.StepSpec;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M4 모양 주문 API — 생성 + 조작자 동사(cancel/suspend/complete/retry-failed) 진입점.
 *
 * <p><b>P24 — 생성 엔드포인트를 추가했다.</b> P19는 이 자리를 비워 두고 스텝 2개짜리
 * {@code TaskController}(호환 어댑터)에만 생성을 맡겼었다. 그 어댑터는 <b>지우지 않는다</b> —
 * 대시보드의 수동 작업 생성 UI가 여전히 그 경로를 쓴다(설계 근거: docs/p24-*.md D4). 여기
 * 새로 연 {@code POST}는 WMS처럼 스텝을 직접 조립해 보내는 소비자를 위한 것이다.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final OrderCodeGenerator orderCodeGenerator;

    public OrderController(OrderService orderService, OrderCodeGenerator orderCodeGenerator) {
        this.orderService = orderService;
        this.orderCodeGenerator = orderCodeGenerator;
    }

    /**
     * M4형 주문 생성 — 스텝 배열을 그대로 받는다(P24 D1). {@code orderCode}는 fleet이
     * 자체 발급하고, 호출부가 보낸 {@code externalId}로만 완료/실패 통지를 받는다.
     */
    @PostMapping
    public ApiResponse<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        List<StepSpec> steps = request.steps().stream()
                .map(s -> new StepSpec(s.location(), s.forLoad(), s.forUnload()))
                .toList();
        var order = orderService.create(
                orderCodeGenerator.next(),
                request.externalId(),
                steps,
                request.priority() != null ? request.priority() : 1,
                request.stepFixed() == null || request.stepFixed(),
                request.materialId());
        return ApiResponse.ok(OrderResponse.from(order));
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> list() {
        return ApiResponse.ok(orderService.findRecent().stream().map(OrderResponse::from).toList());
    }

    @GetMapping("/{orderCode}")
    public ApiResponse<OrderResponse> get(@PathVariable String orderCode) {
        return ApiResponse.ok(OrderResponse.from(orderService.getByCode(orderCode)));
    }

    public record ReasonRequest(String reason) {
    }

    @PostMapping("/{orderCode}/suspend")
    public ApiResponse<OrderResponse> suspend(@PathVariable String orderCode,
                                               @RequestBody(required = false) ReasonRequest body) {
        String reason = body != null ? body.reason() : null;
        return ApiResponse.ok(OrderResponse.from(orderService.suspend(orderCode, reason)));
    }

    @PostMapping("/{orderCode}/unsuspend")
    public ApiResponse<OrderResponse> unsuspend(@PathVariable String orderCode) {
        return ApiResponse.ok(OrderResponse.from(orderService.unsuspend(orderCode)));
    }

    @PostMapping("/{orderCode}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable String orderCode,
                                              @RequestBody(required = false) ReasonRequest body) {
        String reason = body != null ? body.reason() : null;
        return ApiResponse.ok(OrderResponse.from(orderService.cancel(orderCode, reason)));
    }

    public record CancelBatchRequest(List<String> orderCodes, String reason) {
    }

    public record CancelResult(String orderCode, boolean success, String message) {
    }

    public record CancelBatchResponse(List<CancelResult> results) {
    }

    /**
     * 컨트롤러 레벨 루프 — 감싸는 {@code @Transactional}이 없다. 항목 하나의 실패가
     * (설령 여기서 잡더라도) 나머지를 rollback-only로 물들이지 않도록, 각 {@code cancel}
     * 호출이 독립된 최상위 트랜잭션(Spring 프록시)으로 커밋/롤백되게 한다.
     */
    @PostMapping("/cancel-batch")
    public ApiResponse<CancelBatchResponse> cancelBatch(@RequestBody CancelBatchRequest request) {
        List<CancelResult> results = new ArrayList<>();
        for (String orderCode : request.orderCodes()) {
            try {
                orderService.cancel(orderCode, request.reason());
                results.add(new CancelResult(orderCode, true, null));
            } catch (BusinessException e) {
                results.add(new CancelResult(orderCode, false, e.getMessage()));
            } catch (Exception e) {
                log.error("cancel-batch: unexpected error cancelling {}", orderCode, e);
                results.add(new CancelResult(orderCode, false, "예기치 않은 오류"));
            }
        }
        return ApiResponse.ok(new CancelBatchResponse(results));
    }

    @PostMapping("/{orderCode}/complete")
    public ApiResponse<OrderResponse> complete(@PathVariable String orderCode) {
        return ApiResponse.ok(OrderResponse.from(orderService.completeOrder(orderCode)));
    }

    public record NoteRequest(String note) {
    }

    @PostMapping("/{orderCode}/retry-failed")
    public ApiResponse<OrderResponse> retryFailed(@PathVariable String orderCode,
                                                   @RequestBody(required = false) NoteRequest body) {
        String note = body != null ? body.note() : null;
        return ApiResponse.ok(OrderResponse.from(orderService.retryFailed(orderCode, note)));
    }
}
