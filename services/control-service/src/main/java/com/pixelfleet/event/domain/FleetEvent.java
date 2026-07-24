package com.pixelfleet.event.domain;

import com.pixelfleet.common.entity.BaseEntity;
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

/**
 * Immutable record of a single state change in the fleet. Every robot telemetry
 * update and every task lifecycle transition is persisted here — this table is the
 * single source of truth from which the live dashboard and history are derived.
 */
@Getter
@Entity
@Table(name = "fleet_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FleetEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FleetEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SourceType sourceType;

    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TargetType targetType;

    private Long targetId;

    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventSeverity severity;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(columnDefinition = "text")
    private String payloadJson;

    public FleetEvent(
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
        this.eventType = eventType;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.taskId = taskId;
        this.severity = severity;
        this.message = message;
        this.payloadJson = payloadJson;
    }
}
