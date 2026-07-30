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
        /** 설비에서 실제로 일어난 시각. 타임라인 정렬·구간 계산은 이 값을 쓴다. */
        LocalDateTime occurredAt,
        /** 서버 적재 시각. occurredAt과의 차이가 곧 파이프라인 지연이다(감사용). */
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
                event.getOccurredAt(),
                event.getCreatedAt()
        );
    }
}
