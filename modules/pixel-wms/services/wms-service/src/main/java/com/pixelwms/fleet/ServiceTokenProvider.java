package com.pixelwms.fleet;

import com.pixelplatform.core.auth.jwt.JwtTokenProvider;
import com.pixelplatform.core.user.domain.UserRole;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 모듈 간 호출용 서비스 토큰.
 *
 * <p><b>왜 로그인하지 않는가.</b> 플랫폼은 서명 키를 모든 모듈이 공유한다. WMS도 그 키를 갖고
 * 있으므로 서비스 주체({@code svc-wms})로 토큰을 직접 발급하면 된다 — 서비스 계정의 비밀번호를
 * 설정에 심어 두는 것보다 안전하고, 로그인 왕복도 없앤다.
 *
 * <p>진짜 M2M 인증(별도 발급자·스코프·감사)이 생기면 이 클래스만 갈아끼운다. 그때까지는
 * 이것이 플랫폼의 M2M 경로이며, <b>공유 키를 가진 모듈만</b> 이렇게 할 수 있다.
 *
 * <p>토큰은 만료 전까지 재사용하고 여유를 두고 미리 갱신한다.
 */
@Component
public class ServiceTokenProvider {

    private static final String SERVICE_PRINCIPAL = "svc-wms";
    /** 만료 직전 호출이 401로 튀지 않게 미리 갱신하는 여유. */
    private static final Duration RENEW_BEFORE = Duration.ofMinutes(10);

    private final JwtTokenProvider jwtTokenProvider;
    private final Duration tokenLifetime;

    private volatile String cachedToken;
    private volatile Instant renewAt = Instant.EPOCH;

    public ServiceTokenProvider(JwtTokenProvider jwtTokenProvider,
                                com.pixelplatform.core.auth.jwt.JwtProperties jwtProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenLifetime = Duration.ofMinutes(jwtProperties.getAccessTokenExpirationMinutes());
    }

    public String token() {
        Instant now = Instant.now();
        if (cachedToken == null || now.isAfter(renewAt)) {
            synchronized (this) {
                if (cachedToken == null || Instant.now().isAfter(renewAt)) {
                    // 운송 작업 생성은 관리 행위라 ADMIN 으로 낸다(fleet은 인증만 보지만,
                    // 나중에 역할 제한이 붙어도 계속 통하도록).
                    cachedToken = jwtTokenProvider.createAccessToken(SERVICE_PRINCIPAL, UserRole.ADMIN);
                    renewAt = Instant.now().plus(tokenLifetime).minus(RENEW_BEFORE);
                }
            }
        }
        return cachedToken;
    }
}
