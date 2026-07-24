package com.pixelfleet.common.response;

public record ErrorResponse(
        String code,
        String message
) {
}
