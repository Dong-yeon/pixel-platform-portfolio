package com.pixelfactory.realtime;

import com.pixelfactory.equipment.dto.EquipmentResponse;
import com.pixelfactory.event.dto.FactoryEventResponse;

/**
 * 내부 애플리케이션 이벤트들 — 도메인 서비스가 메시징 인프라를 직접 부르지 않게 하는 경계다.
 *
 * <p>도메인은 "이런 일이 있었다"만 발행하고, 실제 push는 {@link RealtimeBroadcaster}가
 * <b>트랜잭션 커밋 후</b>에 한다. 커밋 전에 쏘면 롤백된 상태가 화면에 남는다.
 */
public final class FactoryRealtimeEvents {

    private FactoryRealtimeEvents() {
    }

    /** 설비 상태가 바뀌었다. */
    public record EquipmentStatusChanged(EquipmentResponse equipment) {}

    /** FactoryEvent가 적재됐다. */
    public record FactoryEventRecorded(FactoryEventResponse event) {}
}
