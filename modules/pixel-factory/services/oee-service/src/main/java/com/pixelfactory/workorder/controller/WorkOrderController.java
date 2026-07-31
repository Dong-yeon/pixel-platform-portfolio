package com.pixelfactory.workorder.controller;

import com.pixelplatform.core.common.response.ApiResponse;
import com.pixelfactory.auth.CurrentUserProvider;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import com.pixelfactory.workorder.dto.WorkOrderCompleteProductionRequest;
import com.pixelfactory.workorder.dto.WorkOrderCreateRequest;
import com.pixelfactory.workorder.dto.WorkOrderHoldRequest;
import com.pixelfactory.workorder.dto.WorkOrderResponse;
import com.pixelfactory.workorder.service.WorkOrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final CurrentUserProvider currentUserProvider;

    public WorkOrderController(WorkOrderService workOrderService, CurrentUserProvider currentUserProvider) {
        this.workOrderService = workOrderService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ApiResponse<WorkOrderResponse> create(@Valid @RequestBody WorkOrderCreateRequest request) {
        return ApiResponse.ok(workOrderService.create(request));
    }

    @GetMapping
    public ApiResponse<List<WorkOrderResponse>> search(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) Long assignedUserId,
            @RequestParam(required = false) String lotNo
    ) {
        return ApiResponse.ok(workOrderService.search(status, assignedUserId, lotNo));
    }

    @GetMapping("/my")
    public ApiResponse<List<WorkOrderResponse>> getMyWorkOrders() {
        // 인증된 사용자(username)를 userId로 해석해 배정된 작업지시만 돌려준다.
        return ApiResponse.ok(workOrderService.getMyWorkOrders(currentUserProvider.requireUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkOrderResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(workOrderService.get(id));
    }

    @PatchMapping("/{id}/start")
    public ApiResponse<WorkOrderResponse> start(@PathVariable Long id) {
        return ApiResponse.ok(workOrderService.start(id));
    }

    @PatchMapping("/{id}/complete-production")
    public ApiResponse<WorkOrderResponse> completeProduction(
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderCompleteProductionRequest request
    ) {
        return ApiResponse.ok(workOrderService.completeProduction(id, request));
    }

    @PatchMapping("/{id}/hold")
    public ApiResponse<WorkOrderResponse> hold(
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderHoldRequest request
    ) {
        return ApiResponse.ok(workOrderService.hold(id, request));
    }

    @PatchMapping("/{id}/close")
    public ApiResponse<WorkOrderResponse> close(@PathVariable Long id) {
        return ApiResponse.ok(workOrderService.close(id));
    }
}
