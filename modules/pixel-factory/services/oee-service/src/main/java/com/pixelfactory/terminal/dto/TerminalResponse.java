package com.pixelfactory.terminal.dto;

import com.pixelfactory.terminal.domain.PopTerminal;

/** POP 단말 마스터. */
public record TerminalResponse(
        Long id,
        String terminalCode,
        String name,
        Long lineId,
        double posX,
        double posY
) {

    public static TerminalResponse from(PopTerminal terminal) {
        return new TerminalResponse(
                terminal.getId(),
                terminal.getTerminalCode(),
                terminal.getName(),
                terminal.getLineId(),
                terminal.getPosX(),
                terminal.getPosY()
        );
    }
}
