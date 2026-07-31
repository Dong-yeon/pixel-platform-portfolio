package com.pixelfactory.terminal.controller;

import com.pixelfactory.terminal.dto.TerminalPresenceResponse;
import com.pixelfactory.terminal.dto.TerminalResponse;
import com.pixelfactory.terminal.service.TerminalService;
import com.pixelplatform.core.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POP 단말 마스터·현황 조회.
 *
 * <p>{@code presence}(파생 위치)는 통합 지도가 키오스크 배지를 그리는 데 쓴다 — 저장값이 아니라
 * 최근 TERMINAL 이벤트에서 계산한다. 둘 다 인증만 요구한다(민감 조작 없음).
 */
@RestController
@RequestMapping("/api/terminals")
public class TerminalController {

    private final TerminalService terminalService;

    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @GetMapping
    public ApiResponse<List<TerminalResponse>> getTerminals() {
        return ApiResponse.ok(terminalService.getTerminals());
    }

    @GetMapping("/presence")
    public ApiResponse<List<TerminalPresenceResponse>> getPresence() {
        return ApiResponse.ok(terminalService.getPresence());
    }
}
