package com.racehorse.auth;

import com.racehorse.auth.AuthDtos.CurrentUser;
import com.racehorse.auth.AuthDtos.LoginRequest;
import com.racehorse.auth.AuthDtos.LoginResponse;
import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/auth/login, GET /api/auth/me, POST /api/auth/logout — docs/api/shared.openapi.yaml (CORE-003). */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Login, current user, logout (DEC-008 §6 R5, R8, R10, R11).")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements // public (R10)
    @Operation(operationId = "login", summary = "Sign in with email and password",
            description = "ACTIVE → JWT; INACTIVE or pending registration → 403 after a correct password; "
                    + "anything else → one identical 401. 10 failed attempts per 15 min per IP + email → 429.")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        // The direct peer address is used; X-Forwarded-For is not trusted (no proxy is configured).
        return ApiResponse.ok(authService.login(body.email(), body.password(), request.getRemoteAddr()));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "getCurrentUser", summary = "The signed-in user (used to resolve /dashboard and the sidebar)")
    public ApiResponse<CurrentUser> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(authService.currentUser(principal));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "logout", summary = "Sign out",
            description = "Stateless JWT (no token store): the client discards its token.")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.noContent().build();
    }
}
