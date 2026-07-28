package com.pixelfleet.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket for pushing live fleet state to dashboards.
 *
 * <p>Handshake endpoint {@code /ws} (SockJS). Server broadcasts on the {@code /topic/*}
 * destinations via an in-memory simple broker — clients only subscribe, they never publish,
 * so no client-inbound destinations are needed here.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Dev: allow any origin so the (separately served) React app can connect.
        // TODO: restrict allowed origins and authenticate the STOMP CONNECT before non-local use.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
