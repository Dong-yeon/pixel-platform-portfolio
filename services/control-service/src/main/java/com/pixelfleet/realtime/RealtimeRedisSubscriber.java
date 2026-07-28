package com.pixelfleet.realtime;

import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Receives fan-out messages from Redis Pub/Sub and forwards them to this instance's local
 * STOMP broker, so every connected dashboard on this instance gets the update. The body is
 * already JSON, so it is forwarded as-is (the client parses it).
 */
@Component
public class RealtimeRedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeRedisSubscriber(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String destination = RealtimePublisher.CHANNEL_ROBOTS.equals(channel) ? "/topic/robots" : "/topic/events";
        messagingTemplate.convertAndSend(destination, body);
    }
}
