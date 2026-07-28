package com.pixelplatform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Pixel Platform API Gateway — 플랫폼의 단일 진입점.
 *
 * <p>대시보드와 외부 클라이언트는 이 게이트웨이(9000)만 바라보고, 게이트웨이가
 * 경로 접두사로 모듈을 골라 전달한다. 라우트는 application.yml에 선언한다.
 *
 * <pre>
 *   /api/factory/**  →  pixel-factory (9001)
 *   /api/fleet/**    →  pixel-fleet   (9002)
 *   /ws/**           →  pixel-fleet   (9002, STOMP/SockJS)
 * </pre>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
