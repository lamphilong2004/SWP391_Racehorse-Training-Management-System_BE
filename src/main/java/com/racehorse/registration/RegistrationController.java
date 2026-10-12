package com.racehorse.registration;

import com.racehorse.common.api.ApiResponse;
import com.racehorse.registration.RegistrationDtos.NeutralMessage;
import com.racehorse.registration.RegistrationDtos.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/auth/register — public (DEC-008 §6 R10). */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    @SecurityRequirements // public
    @Operation(operationId = "register", summary = "Submit an access request (no role, no account yet)",
            description = "Always 202 with the same neutral body (DEC-008 §6 R8); 400 only for invalid fields; "
                    + "429 after 5 submissions per 15 min per IP or 3 per 24 h per email.")
    public ResponseEntity<ApiResponse<NeutralMessage>> register(@Valid @RequestBody RegisterRequest body,
                                                                HttpServletRequest request) {
        registrationService.register(body.fullName(), body.email(), body.password(), body.confirmPassword(),
                request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(new NeutralMessage(RegistrationService.NEUTRAL_MESSAGE)));
    }
}
