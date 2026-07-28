package com.pixelplatform.core.auth.dto;

import com.pixelplatform.core.user.domain.UserRole;

public record LoginResponse(
        String accessToken,
        String tokenType,
        String username,
        String name,
        UserRole role
) {
}
