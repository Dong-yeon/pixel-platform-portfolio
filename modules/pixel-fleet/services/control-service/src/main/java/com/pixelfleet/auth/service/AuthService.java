package com.pixelfleet.auth.service;

import com.pixelfleet.auth.dto.LoginRequest;
import com.pixelfleet.auth.dto.LoginResponse;
import com.pixelfleet.auth.jwt.JwtTokenProvider;
import com.pixelfleet.common.exception.BusinessException;
import com.pixelfleet.common.exception.ErrorCode;
import com.pixelfleet.user.domain.User;
import com.pixelfleet.user.domain.UserStatus;
import com.pixelfleet.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw invalidCredentials();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "비활성화된 계정입니다.");
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getUsername(), user.getRole());

        return new LoginResponse(
                accessToken,
                "Bearer",
                user.getUsername(),
                user.getName(),
                user.getRole()
        );
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
