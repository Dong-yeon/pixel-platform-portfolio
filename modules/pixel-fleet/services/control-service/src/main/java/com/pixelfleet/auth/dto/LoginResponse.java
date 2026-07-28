package com.pixelfleet.auth.dto;

import com.pixelfleet.user.domain.UserRole;

public record LoginResponse(
        String accessToken,
        String tokenType,
        String username,
        String name,
        UserRole role
) {
}
