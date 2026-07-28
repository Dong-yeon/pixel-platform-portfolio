package com.pixelfactory.auth.jwt;

import com.pixelfactory.user.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(String username, UserRole role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.getAccessTokenExpirationMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        parseClaims(token);
        return true;
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public UserRole getRole(String token) {
        String role = parseClaims(token).get("role", String.class);
        return UserRole.valueOf(role);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
