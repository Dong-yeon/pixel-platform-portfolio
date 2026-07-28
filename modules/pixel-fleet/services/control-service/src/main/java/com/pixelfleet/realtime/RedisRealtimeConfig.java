package com.pixelfleet.realtime;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Wires the Redis Pub/Sub subscriber to the realtime channels. The container runs the
 * subscriber on a background thread and delivers messages published by any instance.
 */
@Configuration
public class RedisRealtimeConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RealtimeRedisSubscriber subscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                subscriber,
                List.of(
                        new ChannelTopic(RealtimePublisher.CHANNEL_ROBOTS),
                        new ChannelTopic(RealtimePublisher.CHANNEL_EVENTS)));
        return container;
    }
}
