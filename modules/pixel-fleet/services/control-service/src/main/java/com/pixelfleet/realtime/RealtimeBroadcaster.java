package com.pixelfleet.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Broadcasts newly-recorded fleet events to dashboards, but only AFTER the transaction
 * commits — so a rolled-back event never reaches clients. Publishes to Redis Pub/Sub
 * ({@link RealtimePublisher}) so all instances relay it. (Robot live-state updates are
 * published directly from RobotService: they write to Redis, not a DB transaction.)
 */
@Component
public class RealtimeBroadcaster {

    private final RealtimePublisher realtimePublisher;

    public RealtimeBroadcaster(RealtimePublisher realtimePublisher) {
        this.realtimePublisher = realtimePublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFleetEventRecorded(FleetEventRecordedEvent event) {
        realtimePublisher.publishEvent(event.event());
    }
}
