package com.pixelfleet.auth.dto;

import com.pixelfleet.user.domain.UserRole;

public record MeResponse(
        String username,
        UserRole role
) {
}
