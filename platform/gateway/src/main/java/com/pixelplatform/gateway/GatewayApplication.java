package com.pixelplatform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Pixel Platform API Gateway — 플랫폼의 단일 진입점이자 인증 관문.
 *
 * <p>대시보드와 외부 클라이언트는 이 게이트웨이(9000)만 바라보고, 게이트웨이가
 * 경로 접두사로 모듈을 골라 전달한다. 라우트는 application.yml에 선언한다.
 *
 * <pre>
 *   /api/auth/**     →  인증 담당 모듈 (AUTH_MODULE_URI, 기본 pixel-factory)
 *   /api/factory/**  →  pixel-factory (9001)
 *   /api/fleet/**    →  pixel-fleet   (9002)
 *   /ws/**           →  pixel-fleet   (9002, STOMP/SockJS)
 * </pre>
 *
 * <p>인증은 {@link com.pixelplatform.gateway.auth.AuthenticationGlobalFilter}가 맡는다.
 * 발급은 모듈이, <b>검증은 게이트웨이가</b> 한다 — 게이트웨이는 사용자 저장소를 갖지 않는다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
