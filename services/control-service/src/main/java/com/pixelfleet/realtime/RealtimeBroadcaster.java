package com.pixelfleet.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes committed fleet state to subscribed dashboards over STOMP.
 *
 * <p>Listens for internal application events {@code AFTER_COMMIT}, so a rolled-back
 * transaction never leaks a phantom update to clients. Keeps the messaging dependency out
 * of the domain services (they only publish plain application events).
 */
@Component
public class RealtimeBroadcaster {

    public static final String TOPIC_ROBOTS = "/topic/robots";
    public static final String TOPIC_EVENTS = "/topic/events";

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRobotStateChanged(RobotStateChangedEvent event) {
        messagingTemplate.convertAndSend(TOPIC_ROBOTS, event.robot());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFleetEventRecorded(FleetEventRecordedEvent event) {
        messagingTemplate.convertAndSend(TOPIC_EVENTS, event.event());
    }
}
