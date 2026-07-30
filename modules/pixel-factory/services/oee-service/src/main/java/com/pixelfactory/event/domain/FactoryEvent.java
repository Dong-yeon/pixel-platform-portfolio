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
import java.time.LocalDateTime;
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

    /**
     * 설비에서 실제로 일어난 시각. {@code createdAt}(서버 적재 시각)과 구분한다.
     *
     * <p>브로커 지연이나 서비스 다운 후 밀린 메시지 처리로 둘이 벌어질 수 있고,
     * OEE는 상태 구간의 <b>길이</b>로 계산하므로 그 차이가 곧 지표 오차가 된다.
     * 구간 계산은 반드시 이 값을 쓴다.
     *
     * <p><b>createdAt과 같은 시간대(시스템 기본)로 저장한다.</b> 설비가 보내는 ts는
     * UTC(Instant)인데 한쪽만 UTC로 넣으면 같은 테이블에 9시간 차가 생기고,
     * 두 컬럼을 섞어 쓰는 순간 구간 길이가 조용히 틀어진다.
     */
    @Column(nullable = false)
    private LocalDateTime occurredAt;

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
            String payloadJson,
            LocalDateTime occurredAt
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
        this.occurredAt = occurredAt;
    }
}
