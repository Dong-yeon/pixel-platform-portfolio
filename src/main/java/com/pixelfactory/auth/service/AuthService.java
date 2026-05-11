package com.pixelfactory.auth.service;

import com.pixelfactory.auth.dto.LoginRequest;
import com.pixelfactory.auth.dto.LoginResponse;
import com.pixelfactory.auth.jwt.JwtTokenProvider;
import com.pixelfactory.user.domain.UserRole;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        // TODO: Replace this mock login with UserRepository + PasswordEncoder authentication.
        UserRole role = resolveMockRole(request.username());
        String accessToken = jwtTokenProvider.createAccessToken(request.username(), role);

        return new LoginResponse(
                accessToken,
                "Bearer",
                request.username(),
                resolveMockName(request.username()),
                role
        );
    }

    private UserRole resolveMockRole(String username) {
        return switch (username.toLowerCase()) {
            case "admin" -> UserRole.ADMIN;
            case "qms" -> UserRole.QMS_MANAGER;
            case "inspector" -> UserRole.INSPECTOR;
            case "warehouse" -> UserRole.WAREHOUSE_OPERATOR;
            default -> UserRole.OPERATOR;
        };
    }

    private String resolveMockName(String username) {
        return switch (username.toLowerCase()) {
            case "admin" -> "관리자";
            case "qms" -> "QMS 담당자";
            case "inspector" -> "검사 담당자";
            case "warehouse" -> "창고 담당자";
            default -> "작업자";
        };
    }
}
