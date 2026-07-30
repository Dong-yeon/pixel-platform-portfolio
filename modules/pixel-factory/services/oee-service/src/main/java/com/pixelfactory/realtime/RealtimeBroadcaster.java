package com.pixelfactory.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 변경만 대시보드로 밀어 준다.
 *
 * <p>{@code AFTER_COMMIT}인 이유: 커밋 전에 쏘면 롤백된 상태가 화면에 남는다. 특히 MQTT
 * 핸들러는 상태 변경과 이벤트 적재를 한 트랜잭션에서 하므로, 중간에 실패하면 DB에는 없고
 * 화면에만 있는 상태가 된다.
 *
 * <p><b>fleet과 다르게 Redis Pub/Sub을 쓰지 않는다.</b> fleet은 여러 인스턴스로 늘어날 것을
 * 전제로 Redis로 팬아웃한 뒤 각 인스턴스가 자기 STOMP 클라이언트에 재발행한다. factory는
 * Redis를 아예 쓰지 않고(라이브 상태를 Postgres 마스터에 둔다) 단일 인스턴스 전제라
 * {@link SimpMessagingTemplate}로 바로 발행하는 게 맞다 — 쓰지도 않는 의존을 대칭성만 위해
 * 끌어오면 인프라만 늘고 얻는 게 없다.
 *
 * <p>factory를 여러 인스턴스로 늘리는 순간 이 선택은 깨진다(자기 인스턴스에 붙은 클라이언트만
 * 갱신됨). 그때는 fleet과 같은 팬아웃이 필요하다.
 */
@Component
public class RealtimeBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEquipmentStatusChanged(FactoryRealtimeEvents.EquipmentStatusChanged event) {
        messagingTemplate.convertAndSend(WebSocketConfig.TOPIC_EQUIPMENTS, event.equipment());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFactoryEventRecorded(FactoryRealtimeEvents.FactoryEventRecorded event) {
        messagingTemplate.convertAndSend(WebSocketConfig.TOPIC_EVENTS, event.event());
    }
}
