package com.pixelfleet.order.controller;

import com.pixelfleet.order.dto.OrderResponse;
import com.pixelfleet.order.service.OrderService;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.response.ApiResponse;
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
 * M4 모양 주문 API — 조작자 동사(cancel/suspend/complete/retry-failed) 전용 진입점.
 *
 * <p>생성은 여기 없다. {@code TaskController}(호환 어댑터)가 계속 전담한다 — 이 계획은
 * M4형 {@code orders/create}(스텝 배열 입력)를 범위에 넣지 않았다. 이 컨트롤러는
 * {@code orderCode}로 기존 주문을 조작하는 동사만 다룬다.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
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
