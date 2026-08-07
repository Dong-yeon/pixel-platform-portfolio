package com.pixelfleet.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
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
 * <p>Server broadcasts on the {@code /topic/*} destinations via an in-memory simple broker.
 * Clients mostly only subscribe — the one exception is the WS request/response envelope
 * (P19 나머지 작업, see {@code com.pixelfleet.realtime.ws.FleetQueryController}), which
 * clients reach by publishing to {@code /app/query} (covered by the {@code /app} prefix
 * below, no extra config needed here).
 *
 * <p>오리진은 {@code dashboard.origin}(P16, 게이트웨이 globalcors와 값 공유), CONNECT 프레임
 * 인증은 {@link StompAuthChannelInterceptor} — {@code /ws/**}는 HTTP 필터 체인에서는 여전히
 * permitAll이지만(SockJS 핸드셰이크 자체는 헤더를 못 실음), 실제 게이트는 여기다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String dashboardOrigin;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    public WebSocketConfig(
            @Value("${dashboard.origin}") String dashboardOrigin,
            StompAuthChannelInterceptor stompAuthChannelInterceptor
    ) {
        this.dashboardOrigin = dashboardOrigin;
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/fleet").setAllowedOriginPatterns(dashboardOrigin).withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
