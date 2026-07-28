package com.pixelfactory.auth.dto;

import com.pixelfactory.user.domain.UserRole;

public record MeResponse(
        String username,
        UserRole role
) {
}
