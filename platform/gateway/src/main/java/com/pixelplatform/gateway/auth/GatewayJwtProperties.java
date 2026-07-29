package com.pixelplatform.gateway.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 플랫폼 JWT 설정. 게이트웨이는 토큰을 <b>발급하지 않고 검증만</b> 하므로 서명 키만 필요하다.
 *
 * <p>이 키는 모듈(pixel-factory / pixel-fleet)의 {@code jwt.secret}과 <b>반드시 같아야</b> 한다.
 * 다르면 게이트웨이가 통과시킨 토큰을 모듈이 거부해 401이 난다.
 */
@ConfigurationProperties(prefix = "jwt")
public class GatewayJwtProperties {

    private String secret;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
