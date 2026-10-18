package com.racehorse.medical.restriction.api;

import com.racehorse.common.api.ApiResponse;
import com.racehorse.medical.restriction.MedicalRestrictionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MedicalRestrictionController {

    private final MedicalRestrictionService service;

    public MedicalRestrictionController(MedicalRestrictionService service) {
        this.service = service;
    }

    @PostMapping("/horses/{horseId}/medical-restrictions")
    public ResponseEntity<ApiResponse<MedicalRestrictionResponse>> create(
            @PathVariable long horseId,
            @Valid @RequestBody MedicalRestrictionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(horseId, request)));
    }

    @PutMapping("/medical/restrictions/{restrictionId}/exercise-rules")
    public ApiResponse<MedicalRestrictionResponse> replaceExerciseRules(
            @PathVariable long restrictionId,
            @Valid @RequestBody ReplaceExerciseRulesRequest request) {
        return ApiResponse.ok(service.replaceExerciseRules(restrictionId, request));
    }

    @PostMapping("/medical/restrictions/{restrictionId}/lift")
    public ApiResponse<TrainingAvailabilityResponse> lift(
            @PathVariable long restrictionId,
            @Valid @RequestBody MedicalRestrictionLiftRequest request) {
        return ApiResponse.ok(service.lift(restrictionId));
    }

    @GetMapping("/horses/{horseId}/training-availability")
    public ApiResponse<TrainingAvailabilityResponse> availability(@PathVariable long horseId) {
        return ApiResponse.ok(service.availability(horseId));
    }


    @GetMapping("/medical/restrictions")
    public ApiResponse<RestrictionsPageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(page, size));
    }
}