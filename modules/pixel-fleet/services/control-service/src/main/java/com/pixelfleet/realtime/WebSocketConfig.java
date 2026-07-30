package com.pixelfleet.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket for pushing live fleet state to dashboards.
 *
 * <p><b>핸드셰이크 경로는 {@code /ws/fleet}이다.</b> 예전엔 {@code /ws}를 통째로 썼고
 * 게이트웨이도 {@code /ws/**}를 전부 fleet으로 보냈다 — 그러면 factory가 WebSocket을 열
 * 자리가 없다. 모듈이 늘어날 것을 전제로 모듈명을 경로에 넣었다(factory는 {@code /ws/factory}).
 *
 * <p>Server broadcasts on the {@code /topic/*} destinations via an in-memory simple broker —
 * clients only subscribe, they never publish, so no client-inbound destinations are needed here.
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
        registry.addEndpoint("/ws/fleet").setAllowedOriginPatterns("*").withSockJS();
    }
}
