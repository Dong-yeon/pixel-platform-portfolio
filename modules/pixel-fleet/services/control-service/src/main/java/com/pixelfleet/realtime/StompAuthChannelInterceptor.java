package com.pixelfleet.realtime;

import com.pixelplatform.core.auth.jwt.JwtTokenProvider;
import com.pixelplatform.core.user.domain.UserRole;
import io.jsonwebtoken.JwtException;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * STOMP CONNECT 프레임 인증(P16) — {@code /ws/**}는 HTTP 필터 체인에서 여전히 permitAll이다
 * (SockJS 핸드셰이크 자체는 헤더를 못 실어서, 그 단계에서 막을 방법이 없다). 실제 인증은
 * 업그레이드 이후 첫 STOMP 프레임인 CONNECT에서 여기가 담당한다.
 *
 * <p>HTTP 쪽 {@link com.pixelplatform.core.auth.jwt.JwtAuthenticationFilter}와 같은 검증
 * 빌딩 블록({@link JwtTokenProvider})을 그대로 재사용한다 — 다만 실패 처리는 다르다. HTTP
 * 필터는 컨텍스트를 비우고 요청을 통과시켜(나중에 401) 두지만, 여기서는 CONNECT 자체를
 * 거부한다({@code preSend}에서 던지면 Spring이 ERROR 프레임을 보내고 세션을 닫는다) —
 * 백로그가 요구하는 "토큰 없이 연결 시 거부"에 맞춘 것이다.
 *
 * <p>{@link org.springframework.security.core.context.SecurityContextHolder}는 HTTP 요청
 * 스코프라 CONNECT 프레임 처리 이후까지 안 살아남는다 — 대신 {@link StompHeaderAccessor#setUser}로
 * STOMP 세션에 인증 정보를 붙인다(구독·이후 프레임에서 {@code Principal}로 남는다).
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public StompAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                Objects.requireNonNull(StompHeaderAccessor.wrap(message));

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = resolveToken(accessor);
            if (token == null) {
                throw new MessagingException("STOMP CONNECT rejected: missing Authorization header.");
            }
            try {
                jwtTokenProvider.validateToken(token);
                String username = jwtTokenProvider.getUsername(token);
                UserRole role = jwtTokenProvider.getRole(token);
                Principal principal = new UsernamePasswordAuthenticationToken(
                        username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
                accessor.setUser(principal);
            } catch (JwtException | IllegalArgumentException e) {
                throw new MessagingException("STOMP CONNECT rejected: invalid token.", e);
            }
        }

        return message;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
