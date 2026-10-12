package com.racehorse.auth;

import com.racehorse.common.api.ApiError;
import com.racehorse.common.api.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Method-security failures (@PreAuthorize) are thrown inside controller calls, so they reach MVC advice before the
 * security filter chain. Handle them here (ahead of the generic 500 handler) with the same envelope as the filters.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionAdvice {

    @ExceptionHandler(AccessDeniedException.class) // includes AuthorizationDeniedException from @PreAuthorize
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(new ApiError("FORBIDDEN", SecurityErrorWriter.FORBIDDEN_MESSAGE, List.of())));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(new ApiError("UNAUTHORIZED", SecurityErrorWriter.UNAUTHORIZED_MESSAGE, List.of())));
    }
}
