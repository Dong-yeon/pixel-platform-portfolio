package com.pixelfleet.event.dto;

import com.pixelfleet.event.domain.EventSeverity;
import com.pixelfleet.event.domain.FleetEvent;
import com.pixelfleet.event.domain.FleetEventType;
import com.pixelfleet.event.domain.SourceType;
import com.pixelfleet.event.domain.TargetType;
import java.time.LocalDateTime;

public record FleetEventResponse(
        Long id,
        FleetEventType eventType,
        SourceType sourceType,
        Long sourceId,
        TargetType targetType,
        Long targetId,
        Long taskId,
        EventSeverity severity,
        String message,
        LocalDateTime occurredAt
) {

    public static FleetEventResponse from(FleetEvent e) {
        return new FleetEventResponse(
                e.getId(),
                e.getEventType(),
                e.getSourceType(),
                e.getSourceId(),
                e.getTargetType(),
                e.getTargetId(),
                e.getTaskId(),
                e.getSeverity(),
                e.getMessage(),
                e.getCreatedAt()
        );
    }
}
