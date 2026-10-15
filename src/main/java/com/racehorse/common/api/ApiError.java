package com.racehorse.common.api;

import java.util.List;

/** Error part of the envelope. {@code details} lists field-level problems (empty when none). */
public record ApiError(String code, String message, List<ErrorDetail> details) {

    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }
}
