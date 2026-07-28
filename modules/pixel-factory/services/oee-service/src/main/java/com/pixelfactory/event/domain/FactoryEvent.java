package com.pixelfactory.event.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "factory_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FactoryEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FactoryEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SourceType sourceType;

    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TargetType targetType;

    private Long targetId;

    private Long workOrderId;

    @Column(length = 50)
    private String lotNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventSeverity severity;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(columnDefinition = "text")
    private String payloadJson;

    public FactoryEvent(
            FactoryEventType eventType,
            SourceType sourceType,
            Long sourceId,
            TargetType targetType,
            Long targetId,
            Long workOrderId,
            String lotNo,
            EventSeverity severity,
            String message,
            String payloadJson
    ) {
        this.eventType = eventType;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.workOrderId = workOrderId;
        this.lotNo = lotNo;
        this.severity = severity;
        this.message = message;
        this.payloadJson = payloadJson;
    }
}
