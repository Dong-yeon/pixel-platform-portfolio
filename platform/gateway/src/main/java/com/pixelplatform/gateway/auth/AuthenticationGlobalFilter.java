package com.pixelplatform.gateway.auth;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 플랫폼 인증 관문 — 인증되지 않은 요청은 모듈에 닿기 전에 게이트웨이에서 끊는다.
 *
 * <p>하는 일은 세 가지다.
 * <ol>
 *   <li><b>검증</b> — {@code /api/**}의 Bearer 토큰을 확인하고, 없거나 유효하지 않으면
 *       여기서 401을 낸다. 인증 정책이 한 곳에 모이고, 모듈은 미인증 트래픽을 보지 않는다.</li>
 *   <li><b>신원 전달</b> — 통과한 요청에 {@code X-Auth-User} / {@code X-Auth-Role}을 붙인다.
 *       모듈이 토큰을 다시 파싱하지 않고도 누구의 요청인지 알 수 있다.</li>
 *   <li><b>스푸핑 차단</b> — 클라이언트가 보낸 {@code X-Auth-*}는 <b>항상 지운다</b>.
 *       이걸 빠뜨리면 아무나 헤더만 붙여 관리자로 행세할 수 있다 —
 *       헤더 기반 신원 전달에서 가장 흔한 구멍이다.</li>
 * </ol>
 *
 * <p>모듈도 자체 JWT 필터를 그대로 유지한다(방어 심층). 게이트웨이를 우회해
 * 9001/9002에 직접 붙어도 인증이 필요하다 — 헤더만 믿으면 모듈이 무방비가 된다.
 */
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    public static final String USER_HEADER = "X-Auth-User";
    public static final String ROLE_HEADER = "X-Auth-Role";

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 인증 없이 통과시키는 경로.
     *
     * <p>{@code /ws}는 SockJS 핸드셰이크라 Authorization 헤더를 실을 수 없어 여기 있다.
     * 토큰 인증은 STOMP CONNECT 프레임에서 해야 하며 아직 미구현이다(백로그).
     */
    private static final List<String> PUBLIC_PREFIXES = List.of("/api/auth/", "/actuator/");

    private static final String UNAUTHORIZED_BODY =
            "{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}}";

    private final PlatformTokenVerifier tokenVerifier;

    public AuthenticationGlobalFilter(PlatformTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (isPublic(request)) {
            return chain.filter(strip(exchange, null));
        }

        String token = resolveToken(request);
        PlatformTokenVerifier.Identity identity = token == null ? null : tokenVerifier.verify(token);

        if (identity == null) {
            return unauthorized(exchange);
        }

        return chain.filter(strip(exchange, identity));
    }

    /** 게이트웨이는 라우팅(order 10000)보다 먼저 판단해야 한다. */
    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublic(ServerHttpRequest request) {
        // CORS preflight에는 Authorization이 실리지 않는다. 게이트웨이의 globalcors가
        // 대개 먼저 처리하지만, 순서에 기대지 않고 명시적으로 통과시킨다.
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return true;
        }

        String path = request.getURI().getPath();

        // "/ws"와 "/ws/..."만 — "/wsomething"이 딸려 들어오지 않도록 접두사 비교를 쓰지 않는다.
        if (path.equals("/ws") || path.startsWith("/ws/")) {
            return true;
        }

        // API가 아닌 것(대시보드 정적 자원 등)은 인증 대상이 아니다.
        if (!path.startsWith("/api/")) {
            return true;
        }

        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /** 클라이언트가 보낸 신원 헤더를 지우고, 검증된 신원이 있으면 그것만 다시 붙인다. */
    private ServerWebExchange strip(ServerWebExchange exchange, PlatformTokenVerifier.Identity identity) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_HEADER);
                    headers.remove(ROLE_HEADER);
                    if (identity != null) {
                        headers.set(USER_HEADER, identity.username());
                        headers.set(ROLE_HEADER, identity.role());
                    }
                })
                .build();

        return exchange.mutate().request(mutated).build();
    }

    private String resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    /** 모듈의 RestAuthenticationEntryPoint와 같은 응답 형태를 낸다(대시보드가 한 가지만 알면 되도록). */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer body = response.bufferFactory()
                .wrap(UNAUTHORIZED_BODY.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(body));
    }
}
