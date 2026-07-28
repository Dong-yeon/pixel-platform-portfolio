package com.pixelfactory.auth.controller;

import com.pixelfactory.auth.dto.LoginRequest;
import com.pixelfactory.auth.dto.LoginResponse;
import com.pixelfactory.auth.dto.MeResponse;
import com.pixelfactory.auth.service.AuthService;
import com.pixelfactory.common.response.ApiResponse;
import com.pixelfactory.user.domain.UserRole;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(Authentication authentication) {
        // TODO: Load the user profile from UserRepository after real authentication is introduced.
        String username = authentication.getName();
        String authority = authentication.getAuthorities().iterator().next().getAuthority();
        UserRole role = UserRole.valueOf(authority.replace("ROLE_", ""));

        return ApiResponse.ok(new MeResponse(username, role));
    }
}
