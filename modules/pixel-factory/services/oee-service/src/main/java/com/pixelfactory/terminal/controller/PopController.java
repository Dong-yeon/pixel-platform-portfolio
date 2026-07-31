package com.pixelfactory.terminal.controller;

import com.pixelfactory.auth.CurrentUserProvider;
import com.pixelfactory.terminal.dto.PopBoardResponse;
import com.pixelfactory.terminal.service.TerminalService;
import com.pixelfactory.workorder.dto.WorkOrderCompleteProductionRequest;
import com.pixelfactory.workorder.dto.WorkOrderResponse;
import com.pixelfactory.workorder.service.OperationSource;
import com.pixelfactory.workorder.service.WorkOrderService;
import com.pixelplatform.core.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POP(Point of Production) 단말 조작 — 현장 작업자 전용.
 *
 * <p>조작(착수/실적/종료)은 <b>단말</b>을 출처로 이벤트에 남긴다({@link OperationSource#terminal}),
 * 그래야 "그 사람의 가장 최근 TERMINAL 소스 이벤트 = 현재 위치"라는 파생값이 성립해 지도 배지가 뜬다.
 *
 * <p>{@code OPERATOR}/{@code ADMIN}만 접근한다(역할 강제). 게이트웨이가 인증만 보므로 역할 제한은 모듈 몫이다.
 */
@RestController
@RequestMapping("/api/pop")
@PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
public class PopController {

    private final TerminalService terminalService;
    private final WorkOrderService workOrderService;
    private final CurrentUserProvider currentUserProvider;

    public PopController(
            TerminalService terminalService,
            WorkOrderService workOrderService,
            CurrentUserProvider currentUserProvider
    ) {
        this.terminalService = terminalService;
        this.workOrderService = workOrderService;
        this.currentUserProvider = currentUserProvider;
    }

    /** 단말 정보 + 로그인 작업자에게 배정된 작업지시. */
    @GetMapping("/{terminalCode}")
    public ApiResponse<PopBoardResponse> board(@PathVariable String terminalCode) {
        return ApiResponse.ok(terminalService.getBoard(terminalCode, currentUserProvider.requireUserId()));
    }

    @PostMapping("/{terminalCode}/work-orders/{id}/start")
    public ApiResponse<WorkOrderResponse> start(@PathVariable String terminalCode, @PathVariable Long id) {
        Long terminalId = terminalService.requireTerminalId(terminalCode);
        return ApiResponse.ok(workOrderService.start(id, OperationSource.terminal(terminalId)));
    }

    @PostMapping("/{terminalCode}/work-orders/{id}/complete-production")
    public ApiResponse<WorkOrderResponse> completeProduction(
            @PathVariable String terminalCode,
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderCompleteProductionRequest request
    ) {
        Long terminalId = terminalService.requireTerminalId(terminalCode);
        return ApiResponse.ok(workOrderService.completeProduction(id, request, OperationSource.terminal(terminalId)));
    }

    @PostMapping("/{terminalCode}/work-orders/{id}/close")
    public ApiResponse<WorkOrderResponse> close(@PathVariable String terminalCode, @PathVariable Long id) {
        Long terminalId = terminalService.requireTerminalId(terminalCode);
        return ApiResponse.ok(workOrderService.close(id, OperationSource.terminal(terminalId)));
    }
}
