package com.pixelplatform.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 플랫폼 토큰 검증기.
 *
 * <p>모듈의 {@code JwtTokenProvider}가 발급한 토큰을 같은 서명 키로 검증한다.
 * 게이트웨이는 발급 책임이 없으므로(사용자 저장소를 갖지 않는다) 검증만 구현한다.
 */
@Component
public class PlatformTokenVerifier {

    /** 토큰에서 뽑아낸 신원. 다운스트림 모듈에 헤더로 전달된다. */
    public record Identity(String username, String role) {}

    private final SecretKey secretKey;

    public PlatformTokenVerifier(GatewayJwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return 서명·만료가 모두 유효하면 신원, 아니면 {@code null}
     */
    public Identity verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new Identity(claims.getSubject(), claims.get("role", String.class));
        } catch (Exception exception) {
            // 서명 불일치·만료·형식 오류 — 어느 쪽이든 클라이언트에겐 똑같이 401이다.
            return null;
        }
    }
}
