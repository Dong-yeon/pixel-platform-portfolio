package com.pixelfactory.terminal.dto;

import com.pixelfactory.workorder.dto.WorkOrderResponse;
import java.util.List;

/**
 * POP 화면 초기 데이터 — 단말 정보 + 현재 로그인 작업자에게 배정된 작업지시.
 */
public record PopBoardResponse(
        TerminalResponse terminal,
        List<WorkOrderResponse> workOrders
) {
}
