package com.pixelfleet.traffic;

import com.pixelfleet.event.domain.EventSeverity;
import com.pixelfleet.event.domain.FleetEventType;
import com.pixelfleet.event.domain.SourceType;
import com.pixelfleet.event.domain.TargetType;
import com.pixelfleet.event.service.FleetEventService;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 장애물 발생·해제를 받아 {@link ObstacleStore}에 반영하고, 이벤트로 남기고, 대기 중인
 * 주문들이 새로 경로를 계산하도록 알린다 (P20-4).
 *
 * <p>MQTT 토픽 {@code fleet/layout/{buildingCode}/obstacle}로 들어온다({@code
 * MqttMessageHandler} 참고) — 시뮬레이터든 다른 무엇이든, 이 서비스는 <b>누가 보냈는지
 * 신경 쓰지 않는다</b>(컴포저블 원칙: MQTT 계약만 본다).
 */
@Service
public class ObstacleService {

    private static final Logger log = LoggerFactory.getLogger(ObstacleService.class);

    /** validUntil이 없거나 못 읽으면 이 정도만 막아 둔다 — 영구 폐쇄를 기본값으로 두지 않는다. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(2);
    /** 페이로드가 터무니없이 긴 시한을 불러도 상한을 둔다. */
    private static final Duration MAX_TTL = Duration.ofMinutes(10);

    private final ObstacleStore store;
    private final FleetEventService fleetEventService;
    private final ApplicationEventPublisher eventPublisher;

    public ObstacleService(ObstacleStore store, FleetEventService fleetEventService,
                            ApplicationEventPublisher eventPublisher) {
        this.store = store;
        this.fleetEventService = fleetEventService;
        this.eventPublisher = eventPublisher;
    }

    public void block(String fromNode, String toNode, String reason, String validUntilIso) {
        String edgeId = LaneGraph.canonicalEdgeId(fromNode, toNode);
        Duration ttl = resolveTtl(validUntilIso);
        store.block(edgeId, reason, ttl);

        fleetEventService.record(
                FleetEventType.LAYOUT_OBSTACLE_ADDED,
                SourceType.SYSTEM, null,
                TargetType.NONE, null,
                null, EventSeverity.WARNING,
                "엣지 " + edgeId + " 막힘 (" + (reason == null ? "사유 없음" : reason) + ", "
                        + ttl.toSeconds() + "초)", null);

        eventPublisher.publishEvent(new LayoutObstacleChanged());
        log.info("Layout: edge {} blocked ({}s) — {}", edgeId, ttl.toSeconds(), reason);
    }

    public void clear(String fromNode, String toNode, String reason) {
        String edgeId = LaneGraph.canonicalEdgeId(fromNode, toNode);
        store.clear(edgeId);

        fleetEventService.record(
                FleetEventType.LAYOUT_OBSTACLE_CLEARED,
                SourceType.SYSTEM, null,
                TargetType.NONE, null,
                null, EventSeverity.INFO,
                "엣지 " + edgeId + " 해제" + (reason == null ? "" : " (" + reason + ")"), null);

        eventPublisher.publishEvent(new LayoutObstacleChanged());
        log.info("Layout: edge {} cleared — {}", edgeId, reason);
    }

    private Duration resolveTtl(String validUntilIso) {
        if (validUntilIso == null || validUntilIso.isBlank()) {
            return DEFAULT_TTL;
        }
        try {
            Duration ttl = Duration.between(Instant.now(), Instant.parse(validUntilIso));
            if (ttl.isNegative() || ttl.isZero()) {
                return Duration.ofSeconds(5); // 이미 지난 시한 — 사실상 즉시 풀리지만 0 이하는 Redis가 거부한다
            }
            return ttl.compareTo(MAX_TTL) > 0 ? MAX_TTL : ttl;
        } catch (Exception e) {
            log.warn("장애물 validUntil을 못 읽었다({}) — 기본 시한({}초)으로 둔다.",
                    validUntilIso, DEFAULT_TTL.toSeconds());
            return DEFAULT_TTL;
        }
    }
}
