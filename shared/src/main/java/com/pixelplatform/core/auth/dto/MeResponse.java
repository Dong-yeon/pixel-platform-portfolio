package com.pixelplatform.core.auth.dto;

import com.pixelplatform.core.user.domain.UserRole;

public record MeResponse(
        String username,
        UserRole role
) {
}
