package com.pixelfleet.event.service;

import com.pixelfleet.event.domain.EventSeverity;
import com.pixelfleet.event.domain.FleetEvent;
import com.pixelfleet.event.domain.FleetEventType;
import com.pixelfleet.event.domain.SourceType;
import com.pixelfleet.event.domain.TargetType;
import com.pixelfleet.event.dto.FleetEventResponse;
import com.pixelfleet.event.repository.FleetEventRepository;
import com.pixelfleet.realtime.FleetEventRecordedEvent;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single write path for fleet state changes. Domain services call {@link #record}
 * whenever something happens; nothing mutates fleet state without leaving an event.
 * Each recorded event is published for post-commit push to dashboards.
 */
@Service
public class FleetEventService {

    private final FleetEventRepository fleetEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FleetEventService(
            FleetEventRepository fleetEventRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.fleetEventRepository = fleetEventRepository;
        this.eventPublisher = eventPublisher;
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
        FleetEvent saved = fleetEventRepository.save(event);
        // Broadcast to dashboards after the transaction commits (see RealtimeBroadcaster).
        eventPublisher.publishEvent(new FleetEventRecordedEvent(FleetEventResponse.from(saved)));
        return saved;
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
