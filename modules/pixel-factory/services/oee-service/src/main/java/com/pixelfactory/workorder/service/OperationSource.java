package com.pixelfactory.workorder.service;

import com.pixelfactory.event.domain.SourceType;

/**
 * 작업지시 조작의 출처.
 *
 * <p>기본(REST)은 작업지시 자신이 출처다. POP 단말에서 조작하면 <b>단말</b>을 출처로 남겨
 * "그 사람의 가장 최근 TERMINAL 소스 이벤트 = 현재 위치"라는 파생값을 성립시킨다(P12, presence).
 */
public record OperationSource(SourceType type, Long sourceId) {

    public static OperationSource terminal(Long terminalId) {
        return new OperationSource(SourceType.TERMINAL, terminalId);
    }
}
