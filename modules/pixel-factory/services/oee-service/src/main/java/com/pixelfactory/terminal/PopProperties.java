package com.pixelfactory.terminal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * POP 단말 설정.
 *
 * <p>{@code presenceTimeoutMinutes}: 마지막 조작 후 이 시간이 지나면 그 단말의 작업자 배지를
 * "떠났다"로 보고 presence에서 뺀다(P12, stale 처리). 작업자 위치는 저장값이 아니라
 * 최근 TERMINAL 이벤트에서 파생하므로, 종료 이벤트가 없어도 이 타임아웃으로 정리된다.
 */
@ConfigurationProperties(prefix = "pop")
public class PopProperties {

    /** presence 타임아웃(분). 기본 30. */
    private long presenceTimeoutMinutes = 30;

    public long getPresenceTimeoutMinutes() {
        return presenceTimeoutMinutes;
    }

    public void setPresenceTimeoutMinutes(long presenceTimeoutMinutes) {
        this.presenceTimeoutMinutes = presenceTimeoutMinutes;
    }
}
