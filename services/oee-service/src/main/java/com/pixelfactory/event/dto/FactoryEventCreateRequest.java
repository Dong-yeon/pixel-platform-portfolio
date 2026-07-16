package com.pixelfactory.event.dto;

import com.pixelfactory.event.domain.EventSeverity;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.SourceType;
import com.pixelfactory.event.domain.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FactoryEventCreateRequest(
        @NotNull FactoryEventType eventType,
        @NotNull SourceType sourceType,
        Long sourceId,
        @NotNull TargetType targetType,
        Long targetId,
        Long workOrderId,
        String lotNo,
        @NotNull EventSeverity severity,
        @NotBlank @Size(max = 500) String message,
        String payloadJson
) {
}
