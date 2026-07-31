package com.pixelqms.auth;

import com.pixelplatform.core.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 현재 사용자 id 해석.
 *
 * <p>토큰에는 username만 있다. QMS DB의 users 테이블은 <b>비어 있다</b>(계정 마스터는 factory가
 * 갖는다) — 그래서 없으면 null을 돌려준다. 검사원 id는 기록용이라 없어도 판정은 진행된다.
 */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** @return 사용자 id. 이 모듈 DB에 해당 계정이 없으면 null. */
    public Long currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }
}
