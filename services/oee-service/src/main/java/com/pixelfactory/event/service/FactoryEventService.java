package com.pixelfactory.event.service;

import com.pixelfactory.event.domain.EventSeverity;
import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.SourceType;
import com.pixelfactory.event.domain.TargetType;
import com.pixelfactory.event.dto.FactoryEventCreateRequest;
import com.pixelfactory.event.dto.FactoryEventResponse;
import com.pixelfactory.event.repository.FactoryEventRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FactoryEventService {

    private static final int DEFAULT_RECENT_LIMIT = 30;
    private static final int MAX_RECENT_LIMIT = 100;

    private final FactoryEventRepository factoryEventRepository;

    public FactoryEventService(FactoryEventRepository factoryEventRepository) {
        this.factoryEventRepository = factoryEventRepository;
    }

    @Transactional
    public FactoryEventResponse create(FactoryEventCreateRequest request) {
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
                request.payloadJson()
        );

        return FactoryEventResponse.from(factoryEventRepository.save(event));
    }

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
            String payloadJson
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
                payloadJson
        );

        return FactoryEventResponse.from(factoryEventRepository.save(event));
    }

    public List<FactoryEventResponse> getRecent(Integer limit) {
        int safeLimit = normalizeLimit(limit);

        return factoryEventRepository.findByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit))
                .stream()
                .map(FactoryEventResponse::from)
                .toList();
    }

    public List<FactoryEventResponse> getByWorkOrder(Long workOrderId) {
        return factoryEventRepository.findByWorkOrderIdOrderByCreatedAtDesc(workOrderId)
                .stream()
                .map(FactoryEventResponse::from)
                .toList();
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
