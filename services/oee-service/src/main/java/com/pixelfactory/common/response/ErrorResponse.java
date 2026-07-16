package com.pixelfactory.common.response;

public record ErrorResponse(
        String code,
        String message
) {
}
