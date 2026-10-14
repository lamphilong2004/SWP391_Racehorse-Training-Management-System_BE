package com.racehorse.registration;

import com.racehorse.auth.AuthenticatedUser;
import com.racehorse.common.api.ApiResponse;
import com.racehorse.common.api.PageResponse;
import com.racehorse.registration.RegistrationDtos.ApproveRequest;
import com.racehorse.registration.RegistrationDtos.RegistrationRequestView;
import com.racehorse.registration.RegistrationDtos.RejectRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** DEC-008 registration review — CLUB_MANAGER only (shared.openapi.yaml, permission-matrix.md). */
@RestController
@RequestMapping("/api/admin/registrations")
@PreAuthorize("hasRole('CLUB_MANAGER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Registrations", description = "DEC-008 registration review (Club Manager only).")
public class AdminRegistrationController {

    private final RegistrationService registrationService;

    public AdminRegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping
    @Operation(operationId = "listRegistrations", summary = "Registration requests (SCR-ADMIN-REG)")
    public ApiResponse<PageResponse<RegistrationRequestView>> list(
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(registrationService.list(status, page, size));
    }

    @PostMapping("/{id}/approve")
    @Operation(operationId = "approveRegistration", summary = "Approve and assign exactly one role")
    public ApiResponse<RegistrationRequestView> approve(@PathVariable Long id, @Valid @RequestBody ApproveRequest body,
                                                        @AuthenticationPrincipal AuthenticatedUser clubManager) {
        return ApiResponse.ok(registrationService.approve(id, body.roleCode(), clubManager));
    }

    @PostMapping("/{id}/reject")
    @Operation(operationId = "rejectRegistration", summary = "Reject a registration request")
    public ApiResponse<RegistrationRequestView> reject(@PathVariable Long id,
                                                       @Valid @RequestBody(required = false) RejectRequest body,
                                                       @AuthenticationPrincipal AuthenticatedUser clubManager) {
        return ApiResponse.ok(registrationService.reject(id, body == null ? null : body.reason(), clubManager));
    }
}
