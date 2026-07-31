package com.pixelfactory.terminal.dto;

import java.time.LocalDateTime;

/**
 * 파생 위치(presence) — "이 단말에서 지금 누가, 어떤 작업지시를 하고 있는가".
 *
 * <p>저장값이 아니라 최근 TERMINAL 소스 이벤트에서 파생한다. 타임아웃 경과·작업지시 종료 건은
 * 애초에 목록에 포함되지 않는다. {@code lastActivityAt}은 클라이언트가 "흐리게" 판정에 쓴다.
 */
public record TerminalPresenceResponse(
        String terminalCode,
        String operatorName,
        String workOrderNo,
        LocalDateTime lastActivityAt
) {
}
