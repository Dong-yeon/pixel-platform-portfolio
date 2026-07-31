package com.pixelfactory.auth;

import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import com.pixelplatform.core.user.domain.User;
import com.pixelplatform.core.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 인증된 요청의 현재 사용자를 해석한다.
 *
 * <p>토큰/principal에는 username만 있고 숫자 userId는 없다({@code JwtAuthenticationFilter}).
 * "내 작업지시"·POP 조작처럼 userId가 필요한 곳에서 username → {@link User}로 한 번 변환한다.
 * 이걸로 기존 {@code /work-orders/my}의 request-param(assignedUserId) TODO를 없앤다.
 */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,
                        "인증된 사용자를 찾을 수 없습니다: " + username));
    }

    public Long requireUserId() {
        return require().getId();
    }
}
