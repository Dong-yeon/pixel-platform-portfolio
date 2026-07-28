package com.pixelfleet.realtime;

import com.pixelfleet.event.dto.FleetEventResponse;

/**
 * Internal application event: a FleetEvent was persisted. Broadcast to dashboards after
 * the transaction commits so the timeline only ever shows committed events.
 */
public record FleetEventRecordedEvent(FleetEventResponse event) {
}
