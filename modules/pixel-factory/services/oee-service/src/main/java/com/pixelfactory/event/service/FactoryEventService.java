package com.pixelfactory.event.service;

import com.pixelfactory.event.domain.EventSeverity;
import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.SourceType;
import com.pixelfactory.event.domain.TargetType;
import com.pixelfactory.event.dto.FactoryEventCreateRequest;
import com.pixelfactory.event.dto.FactoryEventResponse;
import com.pixelfactory.event.repository.FactoryEventRepository;
import com.pixelfactory.realtime.FactoryRealtimeEvents;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FactoryEventService {

    private static final int DEFAULT_RECENT_LIMIT = 30;
    private static final int MAX_RECENT_LIMIT = 100;

    private final FactoryEventRepository factoryEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FactoryEventService(
            FactoryEventRepository factoryEventRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.factoryEventRepository = factoryEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public FactoryEventResponse create(FactoryEventCreateRequest request) {
        // 수동 등록(REST)은 발생시각을 따로 받지 않는다 — 지금 일어난 일로 본다.
        FactoryEvent event = new FactoryEvent(
                request.eventType(),
                request.sourceType(),
                request.sourceId(),
                request.targetType(),
                request.targetId(),
                request.workOrderId(),
                request.lotNo(),
                request.severity(),
                request.message(),
                request.payloadJson(),
                LocalDateTime.now()
        );

        return publish(FactoryEventResponse.from(factoryEventRepository.save(event)));
    }

    /**
     * 설비 이벤트를 기록한다.
     *
     * @param occurredAt 설비에서 실제로 일어난 시각. 호출자가 payload의 ts에서 뽑아 넘긴다.
     *                   서버 적재 시각(createdAt)은 감사용으로 따로 남고, OEE 구간 계산은
     *                   이 값을 쓴다.
     */
    @Transactional
    public FactoryEventResponse record(
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
        FactoryEvent event = new FactoryEvent(
                eventType,
                sourceType,
                sourceId,
                targetType,
                targetId,
                workOrderId,
                lotNo,
                severity,
                message,
                payloadJson,
                occurredAt
        );

        return publish(FactoryEventResponse.from(factoryEventRepository.save(event)));
    }

    public List<FactoryEventResponse> getRecent(Integer limit) {
        int safeLimit = normalizeLimit(limit);

        // 타임라인은 발생시각 기준이다. 적재 시각으로 정렬하면 밀렸다 한꺼번에 들어온
        // 메시지가 처리 순서대로 붙어 실제 일어난 순서와 뒤바뀐다.
        return factoryEventRepository.findByOrderByOccurredAtDesc(PageRequest.of(0, safeLimit))
                .stream()
                .map(FactoryEventResponse::from)
                .toList();
    }

    public List<FactoryEventResponse> getByWorkOrder(Long workOrderId) {
        return factoryEventRepository.findByWorkOrderIdOrderByOccurredAtDesc(workOrderId)
                .stream()
                .map(FactoryEventResponse::from)
                .toList();
    }

    /**
     * 적재된 이벤트를 실시간 타임라인으로 알린다.
     *
     * <p>여기서 직접 push하지 않고 애플리케이션 이벤트만 발행한다 — 도메인 서비스가 메시징
     * 인프라에 의존하지 않게 하고, 실제 발행은 트랜잭션 커밋 후로 미루기 위해서다
     * ({@link com.pixelfactory.realtime.RealtimeBroadcaster}).
     */
    private FactoryEventResponse publish(FactoryEventResponse response) {
        eventPublisher.publishEvent(new FactoryRealtimeEvents.FactoryEventRecorded(response));
        return response;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_RECENT_LIMIT;
        }

        if (limit < 1) {
            return DEFAULT_RECENT_LIMIT;
        }

        return Math.min(limit, MAX_RECENT_LIMIT);
    }
}
