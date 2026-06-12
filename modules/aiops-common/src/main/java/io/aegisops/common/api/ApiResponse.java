package io.aegisops.common.api;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
        boolean success,
        T data,
        String errorCode,
        String message,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> fail(String errorCode, String message) {
        return new ApiResponse<>(false, null, errorCode, message, OffsetDateTime.now());
    }
}
