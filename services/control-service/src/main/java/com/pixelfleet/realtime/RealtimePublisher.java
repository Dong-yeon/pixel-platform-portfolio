package com.pixelfleet.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfleet.event.dto.FleetEventResponse;
import com.pixelfleet.robot.dto.RobotResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fans live updates out to <em>all</em> control-service instances via Redis Pub/Sub.
 * Every instance's {@link RealtimeRedisSubscriber} then forwards to its own STOMP clients,
 * so the dashboard gets updates no matter which instance produced them (horizontal scale-out).
 */
@Component
public class RealtimePublisher {

    public static final String CHANNEL_ROBOTS = "fleet:realtime:robots";
    public static final String CHANNEL_EVENTS = "fleet:realtime:events";

    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RealtimePublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void publishRobot(RobotResponse robot) {
        send(CHANNEL_ROBOTS, robot);
    }

    public void publishEvent(FleetEventResponse event) {
        send(CHANNEL_EVENTS, event);
    }

    private void send(String channel, Object payload) {
        try {
            redis.convertAndSend(channel, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to publish to Redis channel {}", channel, e);
        }
    }
}
