package com.racehorse.training.adjustment;

import com.racehorse.common.api.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.racehorse.training.adjustment.AdjustmentApi.*;

@RestController
public class AdjustmentRequestController {
    private final AdjustmentRequestService service;
    public AdjustmentRequestController(AdjustmentRequestService service) { this.service = service; }

    @PostMapping("/api/training-programs/{id}/adjustment-requests")
    @Operation(operationId = "createAdjustmentRequest")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<AdjustmentRequest> create(@PathVariable long id, @Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(id, request));
    }

    @GetMapping("/api/adjustment-requests")
    @Operation(operationId = "listAdjustmentRequests")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','TRAINER')")
    public ApiResponse<Page> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) Long programId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(status, programId, page, size));
    }

    @PostMapping("/api/adjustment-requests/{id}/approve")
    @Operation(operationId = "approveAdjustmentRequest")
    @PreAuthorize("hasRole('HEAD_TRAINER')")
    public ApiResponse<AdjustmentRequest> approve(@PathVariable long id,
            @Valid @RequestBody(required = false) DecisionRequest request) {
        return ApiResponse.ok(service.decide(id, true, request == null ? null : request.decisionReason()));
    }

    @PostMapping("/api/adjustment-requests/{id}/reject")
    @Operation(operationId = "rejectAdjustmentRequest")
    @PreAuthorize("hasRole('HEAD_TRAINER')")
    public ApiResponse<AdjustmentRequest> reject(@PathVariable long id, @Valid @RequestBody RejectRequest request) {
        return ApiResponse.ok(service.decide(id, false, request.decisionReason()));
    }
}
