package com.racehorse.auth;

import com.racehorse.common.api.ApiResponse;
import com.racehorse.common.audit.AuditRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final AuditRepository auditRepository;

    public AuthController(AuthenticationService authenticationService, AuditRepository auditRepository) {
        this.authenticationService = authenticationService;
        this.auditRepository = auditRepository;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Signed in with a JWT."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request validation failed."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid email or password."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "The account is deactivated or valid pending-registration credentials await approval; no JWT is issued."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many attempts.")
    })
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        String remoteAddress = servletRequest.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            throw new IllegalStateException("The request does not contain a remote client address.");
        }
        return ApiResponse.ok(authenticationService.login(request, remoteAddress));
    }

    @GetMapping("/me")
    @Operation(summary = "Return the signed-in user's profile", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current user."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid or expired JWT."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "The authenticated account is deactivated.")
    })
    public ApiResponse<CurrentUser> currentUser(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(currentUser);
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Sign out",
            description = "Logout is stateless: this records an audit event, and the client must discard its token.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Signed out."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid or expired JWT.")
    })
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CurrentUser currentUser) {
        auditRepository.recordLogout(currentUser);
        return ResponseEntity.noContent().build();
    }
}
