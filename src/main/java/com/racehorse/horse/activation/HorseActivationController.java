package com.racehorse.horse.activation;

import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/horses")
@Validated
@SecurityRequirement(name = "bearerAuth")
public class HorseActivationController {

    private final HorseActivationService service;

    public HorseActivationController(HorseActivationService service) {
        this.service = service;
    }

    @GetMapping("/pending-activation")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN')")
    @Operation(operationId = "listPendingActivation",
            summary = "List DRAFT horses and their activation checklist")
    public ApiResponse<ActivationPage> listPending(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.listPending(page, size));
    }

    @GetMapping("/{id}/activation-status")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN')")
    @Operation(operationId = "getActivationStatus", summary = "Get one horse's activation checklist")
    public ApiResponse<ActivationStatus> getStatus(@PathVariable @Min(1) long id) {
        return ApiResponse.ok(service.getStatus(id));
    }

    @PostMapping("/{id}/readiness-review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('HEAD_TRAINER')")
    @Operation(operationId = "acknowledgeReadinessReview",
            summary = "Record a non-blocking Head Trainer readiness review")
    public void recordReadinessReview(@PathVariable @Min(1) long id) {
        service.recordReadinessReview(id);
    }
}
