package com.pixelqms.factory;

import com.pixelplatform.core.auth.jwt.JwtProperties;
import com.pixelplatform.core.auth.jwt.JwtTokenProvider;
import com.pixelplatform.core.user.domain.UserRole;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 모듈 간 호출용 서비스 토큰.
 *
 * <p>플랫폼이 서명 키를 공유하므로 서비스 주체({@code svc-qms})로 직접 발급한다 —
 * 서비스 계정 비밀번호를 설정에 심어 두는 것보다 안전하고 로그인 왕복도 없다.
 * 진짜 M2M 인증이 생기면 이 클래스만 갈아끼운다.
 */
@Component
public class ServiceTokenProvider {

    private static final String SERVICE_PRINCIPAL = "svc-qms";
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
