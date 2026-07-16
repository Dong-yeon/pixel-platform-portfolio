package com.pixelfactory.auth.dto;

import com.pixelfactory.user.domain.UserRole;

public record LoginResponse(
        String accessToken,
        String tokenType,
        String username,
        String name,
        UserRole role
) {
}
