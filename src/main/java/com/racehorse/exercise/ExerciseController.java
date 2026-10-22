package com.racehorse.exercise;

import com.racehorse.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private static final String READ_ROLES = "hasAnyRole('HEAD_TRAINER','TRAINER','WORK_RIDER','VETERINARIAN','CLUB_MANAGER')";
    private static final String WRITE_ROLES = "hasAnyRole('HEAD_TRAINER','TRAINER')";

    private final ExerciseService service;

    public ExerciseController(ExerciseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ApiResponse<List<ExerciseResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String exerciseType) {
        return ApiResponse.ok(service.list(q, exerciseType));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public ApiResponse<ExerciseResponse> create(@Valid @RequestBody ExerciseInput input) {
        return ApiResponse.ok(service.create(input));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<ExerciseResponse> get(@PathVariable long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ApiResponse<ExerciseResponse> update(@PathVariable long id, @Valid @RequestBody ExerciseInput input) {
        return ApiResponse.ok(service.update(id, input));
    }
}
