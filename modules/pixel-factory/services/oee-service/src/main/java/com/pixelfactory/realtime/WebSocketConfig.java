package com.pixelfactory.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 대시보드로 공장 실시간 상태를 밀어 주는 STOMP-over-WebSocket.
 *
 * <p><b>핸드셰이크 경로는 {@code /ws/factory}다</b> — fleet은 {@code /ws/fleet}을 쓴다.
 * 예전엔 fleet이 {@code /ws}를 통째로 쓰고 게이트웨이도 {@code /ws/**}를 전부 fleet으로
 * 보냈는데, 그러면 factory가 WebSocket을 열 자리가 없다. 모듈이 늘어날 것을 전제로
 * 모듈명을 경로에 넣었다.
 *
 * <p>서버는 {@code /topic/factory/*}로만 발행하고 클라이언트는 구독만 한다 —
 * 클라이언트가 보내는 목적지는 없다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String TOPIC_EQUIPMENTS = "/topic/factory/equipments";
    public static final String TOPIC_EVENTS = "/topic/factory/events";
    public static final String TOPIC_OEE = "/topic/factory/oee";

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Dev: 대시보드가 별도 오리진(9200)에서 서비스되므로 모든 오리진을 허용한다.
        // TODO: 배포 전 오리진 제한 + STOMP CONNECT 프레임 인증(게이트웨이가 /ws는 통과시킨다).
        registry.addEndpoint("/ws/factory").setAllowedOriginPatterns("*").withSockJS();
    }
}
