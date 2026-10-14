package com.racehorse.common.error;

import com.racehorse.common.api.ErrorDetail;

import java.util.List;

/** Thrown by services for expected rule violations; rendered as the error envelope with the code's HTTP status. */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final List<ErrorDetail> details;

    public BusinessException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public BusinessException(ErrorCode code, String message, List<ErrorDetail> details) {
        super(message);
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ErrorDetail> details() {
        return details;
    }
}
