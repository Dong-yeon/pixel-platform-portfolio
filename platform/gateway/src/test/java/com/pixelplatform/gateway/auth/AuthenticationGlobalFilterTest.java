package com.pixelplatform.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 게이트웨이 인증 관문의 세 가지 책무(검증·신원 전달·스푸핑 차단)를 직접 검증한다.
 *
 * <p>{@code PlatformTokenVerifier}를 목으로 대체하지 않고 실제 서명 키로 토큰을 만들어
 * 태운다 — 검증 로직 자체(서명·만료)가 흔들리면 이 테스트도 같이 흔들려야 의미가 있다.
 * 목으로 대체하면 "필터가 검증기를 호출했다"만 확인하고 "검증이 실제로 맞는지"는
 * 놓친다.
 */
class AuthenticationGlobalFilterTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long-for-hs256!!";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private AuthenticationGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        GatewayJwtProperties properties = new GatewayJwtProperties();
        properties.setSecret(SECRET);
        filter = new AuthenticationGlobalFilter(new PlatformTokenVerifier(properties));

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String token(String username, String role, Duration validFor) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validFor)))
                .signWith(KEY)
                .compact();
    }

    private String validToken(String username, String role) {
        return token(username, role, Duration.ofHours(2));
    }

    @Nested
    class 공개경로는_토큰없이_통과한다 {

        @Test
        void 로그인_경로() {
            assertPassesThrough(MockServerHttpRequest.post("/api/auth/login").build());
        }

        @Test
        void actuator_경로() {
            assertPassesThrough(MockServerHttpRequest.get("/actuator/health").build());
        }

        @Test
        void OPTIONS_요청은_보호_경로여도_통과한다() {
            assertPassesThrough(MockServerHttpRequest.options("/api/factory/equipments").build());
        }

        @Test
        void ws_경로() {
            assertPassesThrough(MockServerHttpRequest.get("/ws").build());
        }

        @Test
        void ws_하위_경로() {
            assertPassesThrough(MockServerHttpRequest.get("/ws/fleet/info").build());
        }

        @Test
        void api가_아닌_정적_자원_경로() {
            assertPassesThrough(MockServerHttpRequest.get("/assets/main.js").build());
        }

        private void assertPassesThrough(MockServerHttpRequest request) {
            ServerWebExchange exchange = MockServerWebExchange.from(request);
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            verify(chain, times(1)).filter(any());
        }
    }

    @Nested
    class ws_접두사_비교는_느슨하지_않다 {

        @Test
        void api_아래의_ws로_시작하는_경로는_공개가_아니다() {
            // "/wsevil"은 /api/ 밖이라 다른 규칙(비-API는 공개)으로 어차피 통과한다.
            // ws 접두사 비교가 진짜 느슨한지는 /api/ 아래에서 확인해야 의미가 있다.
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/wsevil").build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verifyNoInteractions(chain);
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class 보호된_경로는_토큰이_있어야_통과한다 {

        @Test
        void 토큰이_없으면_401이고_체인이_호출되지_않는다() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/factory/equipments").build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verifyNoInteractions(chain);
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(exchange.getResponse().getHeaders().getContentType())
                    .hasToString("application/json");
        }

        @Test
        void Bearer_접두사_없이_토큰만_보내면_401이다() {
            String raw = validToken("admin", "ADMIN");
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/factory/equipments")
                    .header("Authorization", raw)
                    .build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verifyNoInteractions(chain);
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 형식이_깨진_토큰은_401이다() {
            assert401("not-a-real-jwt-at-all");
        }

        @Test
        void 만료된_토큰은_401이다() {
            String expired = token("admin", "ADMIN", Duration.ofSeconds(-1));
            assert401(expired);
        }

        @Test
        void 다른_키로_서명된_토큰은_401이다() {
            SecretKey otherKey = Keys.hmacShaKeyFor(
                    "a-completely-different-secret-key-32-bytes!".getBytes(StandardCharsets.UTF_8));
            String forged = Jwts.builder()
                    .subject("admin")
                    .claim("role", "ADMIN")
                    .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                    .signWith(otherKey)
                    .compact();
            assert401(forged);
        }

        private void assert401(String token) {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/factory/equipments")
                    .header("Authorization", "Bearer " + token)
                    .build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verifyNoInteractions(chain);
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class 유효한_토큰은_신원_헤더로_바뀌어_전달된다 {

        @Test
        void 검증된_신원이_그대로_실린다() {
            String token = validToken("dispatcher", "DISPATCHER");
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/fleet/robots")
                    .header("Authorization", "Bearer " + token)
                    .build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            ServerWebExchange forwarded = capturedExchange();
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Auth-User")).isEqualTo("dispatcher");
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Auth-Role")).isEqualTo("DISPATCHER");
        }

        private ServerWebExchange capturedExchange() {
            org.mockito.ArgumentCaptor<ServerWebExchange> captor =
                    org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            return captor.getValue();
        }
    }

    @Nested
    class 클라이언트가_보낸_신원_헤더는_항상_지워진다 {

        /**
         * 이게 핵심이다 — 헤더 기반 신원 전달에서 가장 흔한 구멍이 "클라이언트가 보낸
         * X-Auth-* 를 안 지우는 것"이다. 여기서 깨지면 누구나 헤더만 붙여 관리자로
         * 행세할 수 있다.
         */
        @Test
        void 위조된_role_헤더는_검증된_토큰의_role로_덮어써진다() {
            String token = validToken("operator", "OPERATOR");
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/factory/equipments")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Auth-Role", "ADMIN") // 위조 시도
                    .header("X-Auth-User", "hacker") // 위조 시도
                    .build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            ServerWebExchange forwarded = capturedExchange();
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Auth-User")).isEqualTo("operator");
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Auth-Role")).isEqualTo("OPERATOR");
        }

        @Test
        void 토큰_없이_위조된_헤더만_보내도_401이고_헤더는_모듈에_닿지_않는다() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/factory/equipments")
                    .header("X-Auth-Role", "ADMIN")
                    .header("X-Auth-User", "hacker")
                    .build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            // 401로 끊기므로 체인(=모듈로 가는 경로)이 아예 호출되지 않는다 —
            // 위조 헤더가 모듈에 도달할 방법 자체가 없다.
            verifyNoInteractions(chain);
        }

        @Test
        void 공개_경로에서도_위조된_헤더는_지워진다() {
            // 공개 경로라 인증은 필요 없지만, 그렇다고 클라이언트가 보낸 신원 헤더를
            // 그대로 흘려보내면 안 된다 — strip(exchange, null)이 이 경우도 지운다.
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                    .header("X-Auth-Role", "ADMIN")
                    .header("X-Auth-User", "hacker")
                    .build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            ServerWebExchange forwarded = capturedExchange();
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Auth-User")).isNull();
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Auth-Role")).isNull();
        }

        private ServerWebExchange capturedExchange() {
            org.mockito.ArgumentCaptor<ServerWebExchange> captor =
                    org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            return captor.getValue();
        }
    }

    @Test
    void 라우팅보다_먼저_실행되도록_순서가_낮다() {
        // GlobalFilter 순서는 낮을수록 먼저 실행된다. 게이트웨이 라우팅 필터는 보통
        // order 10000(NettyRoutingFilter) 근처라, 그보다 한참 낮아야 라우팅 전에 막힌다.
        assertThat(filter.getOrder()).isLessThan(0);
    }
}
