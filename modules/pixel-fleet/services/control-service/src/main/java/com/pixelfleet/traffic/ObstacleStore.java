package com.pixelfleet.traffic;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 지금 막혀 있는 엣지 — <b>라이브 상태다, 정적 토폴로지가 아니다</b> (P20-4).
 *
 * <p>factory의 {@code layout_edges}는 건드리지 않는다 — "지금 이 엣지가 막혀 있는가"는
 * 로봇 위치처럼 자주 바뀌고 재시작하면 사라져도 되는 사실이라, {@link
 * com.pixelfleet.robot.livestate.RobotLiveStateStore}와 같은 이유로 fleet의 Redis에만
 * 둔다(설계 근거: {@code docs/p20-layout-routing-design.md} D4). DB per module을 지키면서
 * fleet이 factory 테이블에 쓸 필요가 없게 한다.
 *
 * <p>만료는 Redis TTL에 맡긴다 — CLEARED 이벤트가 유실돼도 시간이 지나면 스스로 풀린다
 * (MQTT는 최소 1회 전달이라 유실 가능성을 항상 고려한다 — 이 프로젝트의 다른 곳과 같은 원칙).
 */
@Component
public class ObstacleStore {

    private static final String KEY_PREFIX = "fleet:obstacle:";

    private final StringRedisTemplate redis;

    public ObstacleStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void block(String edgeId, String reason, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + edgeId, reason == null ? "" : reason, ttl);
    }

    public void clear(String edgeId) {
        redis.delete(KEY_PREFIX + edgeId);
    }

    public boolean isBlocked(String edgeId) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + edgeId));
    }

    /** 모니터링용 — 지금 막혀 있는 엣지 id 전부. */
    public Set<String> snapshot() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        return keys.stream().map(k -> k.substring(KEY_PREFIX.length())).collect(Collectors.toSet());
    }
}
