package com.pixelplatform.core.common.response;

public record ErrorResponse(
        String code,
        String message
) {
}
