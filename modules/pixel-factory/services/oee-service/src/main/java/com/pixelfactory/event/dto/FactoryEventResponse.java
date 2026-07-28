package com.pixelfactory.event.dto;

import com.pixelfactory.event.domain.EventSeverity;
import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.SourceType;
import com.pixelfactory.event.domain.TargetType;
import java.time.LocalDateTime;

public record FactoryEventResponse(
        Long id,
        FactoryEventType eventType,
        SourceType sourceType,
        Long sourceId,
        TargetType targetType,
        Long targetId,
        Long workOrderId,
        String lotNo,
        EventSeverity severity,
        String message,
        String payloadJson,
        LocalDateTime createdAt
) {

    public static FactoryEventResponse from(FactoryEvent event) {
        return new FactoryEventResponse(
                event.getId(),
                event.getEventType(),
                event.getSourceType(),
                event.getSourceId(),
                event.getTargetType(),
                event.getTargetId(),
                event.getWorkOrderId(),
                event.getLotNo(),
                event.getSeverity(),
                event.getMessage(),
                event.getPayloadJson(),
                event.getCreatedAt()
        );
    }
}
