package com.pixelfleet.location;

import com.pixelplatform.core.auth.jwt.JwtProperties;
import com.pixelplatform.core.auth.jwt.JwtTokenProvider;
import com.pixelplatform.core.user.domain.UserRole;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * fleet이 factory({@code GET /api/layout})를 부를 때 쓰는 M2M 토큰 발급자(P16 WP2).
 *
 * <p>QMS의 {@code com.pixelqms.factory.ServiceTokenProvider}, WMS의
 * {@code com.pixelwms.fleet.ServiceTokenProvider}와 완전히 같은 패턴이다 — 별도 서비스
 * 계정·서명 키를 새로 만들지 않고, {@code PLATFORM_JWT_SECRET}을 공유하는 {@link
 * JwtTokenProvider}로 모듈 자신을 위해 일반 로그인 토큰과 같은 모양의 토큰을 발급한다
 * ("서비스" 전용 role/claim은 없다 — 검증 쪽도 구분하지 않는다). 패키지가
 * {@code com.pixelfleet.factory}가 아니라 {@code com.pixelfleet.location}인 이유는
 * 유일한 소비자인 {@link LocationRegistry}와 같은 자리에 두기 위해서다(QMS/WMS는
 * 호출 대상 모듈 이름으로 패키지를 짓는 관례지만, fleet은 이미 이 개념이 사는 패키지가
 * 따로 있었다).
 */
@Component
public class ServiceTokenProvider {

    private static final String SERVICE_PRINCIPAL = "svc-fleet";
    private static final Duration RENEW_BEFORE = Duration.ofMinutes(10);

    private final JwtTokenProvider jwtTokenProvider;
    private final Duration tokenLifetime;

    private volatile String cachedToken;
    private volatile Instant renewAt = Instant.EPOCH;

    public ServiceTokenProvider(JwtTokenProvider jwtTokenProvider, JwtProperties jwtProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenLifetime = Duration.ofMinutes(jwtProperties.getAccessTokenExpirationMinutes());
    }

    public String token() {
        if (cachedToken == null || Instant.now().isAfter(renewAt)) {
            synchronized (this) {
                if (cachedToken == null || Instant.now().isAfter(renewAt)) {
                    cachedToken = jwtTokenProvider.createAccessToken(SERVICE_PRINCIPAL, UserRole.ADMIN);
                    renewAt = Instant.now().plus(tokenLifetime).minus(RENEW_BEFORE);
                }
            }
        }
        return cachedToken;
    }
}
