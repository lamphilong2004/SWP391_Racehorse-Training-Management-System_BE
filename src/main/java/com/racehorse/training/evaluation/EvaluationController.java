package com.racehorse.training.evaluation;

import com.racehorse.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

import static com.racehorse.training.evaluation.EvaluationApi.*;

@RestController
public class EvaluationController {
    private final EvaluationService service;
    public EvaluationController(EvaluationService service) { this.service = service; }

    @PostMapping("/api/training-sessions/{id}/evaluation")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<Map<String, Object>> evaluate(@PathVariable long id, @Valid @RequestBody EvaluationRequest request) {
        return ApiResponse.ok(service.evaluate(id, request));
    }

    @GetMapping("/api/training-sessions/{id}/result")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','TRAINER','WORK_RIDER','CLUB_MANAGER','VETERINARIAN')")
    public ApiResponse<Map<String, Object>> result(@PathVariable long id) { return ApiResponse.ok(service.result(id)); }

    @GetMapping("/api/horses/{id}/performance")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','TRAINER','CLUB_MANAGER','VETERINARIAN')")
    public ApiResponse<java.util.List<PerformancePoint>> performance(@PathVariable long id,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return ApiResponse.ok(service.performance(id, from, to));
    }
}
