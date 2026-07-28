package com.pixelfleet.robot.livestate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import com.pixelfleet.robot.domain.RobotLiveState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed store for robot live state. One JSON value per robot under
 * {@code fleet:robot:{code}}. Reads/writes here replace the per-tick Postgres UPDATE the
 * robots table used to take on every position report.
 */
@Component
public class RobotLiveStateStore {

    private static final String KEY_PREFIX = "fleet:robot:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RobotLiveStateStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<RobotLiveState> find(String robotCode) {
        String json = redis.opsForValue().get(KEY_PREFIX + robotCode);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, RobotLiveState.class));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "라이브 상태 역직렬화 실패: " + robotCode);
        }
    }

    /** Current live state, or a default OFFLINE state if the robot has not reported yet. */
    public RobotLiveState findOrOffline(String robotCode) {
        return find(robotCode).orElseGet(() -> RobotLiveState.offline(robotCode));
    }

    public void save(RobotLiveState state) {
        try {
            redis.opsForValue().set(KEY_PREFIX + state.robotCode(), objectMapper.writeValueAsString(state));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "라이브 상태 직렬화 실패: " + state.robotCode());
        }
    }

    public List<RobotLiveState> findAll(List<String> robotCodes) {
        return robotCodes.stream().map(this::findOrOffline).toList();
    }
}
