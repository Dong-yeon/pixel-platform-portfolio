package com.pixelfleet.event.service;

import com.pixelfleet.event.domain.EventSeverity;
import com.pixelfleet.event.domain.FleetEvent;
import com.pixelfleet.event.domain.FleetEventType;
import com.pixelfleet.event.domain.SourceType;
import com.pixelfleet.event.domain.TargetType;
import com.pixelfleet.event.repository.FleetEventRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single write path for fleet state changes. Domain services call {@link #record}
 * whenever something happens; nothing mutates fleet state without leaving an event.
 */
@Service
public class FleetEventService {

    private final FleetEventRepository fleetEventRepository;

    public FleetEventService(FleetEventRepository fleetEventRepository) {
        this.fleetEventRepository = fleetEventRepository;
    }

    @Transactional
    public FleetEvent record(
            FleetEventType eventType,
            SourceType sourceType,
            Long sourceId,
            TargetType targetType,
            Long targetId,
            Long taskId,
            EventSeverity severity,
            String message,
            String payloadJson
    ) {
        FleetEvent event = new FleetEvent(
                eventType, sourceType, sourceId, targetType, targetId, taskId, severity, message, payloadJson);
        // TODO(Phase 2): after persisting, push this event to subscribed dashboards over WebSocket.
        return fleetEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<FleetEvent> recent() {
        return fleetEventRepository.findTop100ByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public List<FleetEvent> forTask(Long taskId) {
        return fleetEventRepository.findByTaskIdOrderByIdDesc(taskId);
    }
}
