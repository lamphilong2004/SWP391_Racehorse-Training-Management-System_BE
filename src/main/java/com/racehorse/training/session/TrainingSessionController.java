package com.racehorse.training.session;

import com.racehorse.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
public class TrainingSessionController {
    private final TrainingSessionService service;
    public TrainingSessionController(TrainingSessionService service) { this.service = service; }

    @GetMapping("/api/training-sessions")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','TRAINER','WORK_RIDER','CLUB_MANAGER','VETERINARIAN')")
    public ApiResponse<List<SessionApi.Session>> list(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required=false) Long trainerId, @RequestParam(required=false) Long horseId,
            @RequestParam(required=false) Long programId, @RequestParam(required=false) String status) {
        return ApiResponse.ok(service.list(from,to,trainerId,horseId,programId,status));
    }

    @PostMapping("/api/training-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<SessionApi.Session> create(@Valid @RequestBody SessionApi.CreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping("/api/training-sessions/{id}")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','TRAINER','WORK_RIDER','CLUB_MANAGER','VETERINARIAN','GROOM')")
    public ApiResponse<SessionApi.Session> get(@PathVariable long id) { return ApiResponse.ok(service.get(id)); }

    @PatchMapping("/api/training-sessions/{id}")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<SessionApi.Session> update(@PathVariable long id,@Valid @RequestBody SessionApi.UpdateRequest request) {
        return ApiResponse.ok(service.update(id,request));
    }

    @PutMapping("/api/training-sessions/{id}/exercises")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<SessionApi.Session> exercises(@PathVariable long id,@Valid @RequestBody SessionApi.ReplaceExercisesRequest request) {
        return ApiResponse.ok(service.replaceExercises(id,request.exercises()));
    }

    @PostMapping("/api/training-sessions/suitability-check")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<SessionApi.SuitabilityResult> suitability(@Valid @RequestBody SessionApi.DraftRequest request) {
        return ApiResponse.ok(service.suitability(request));
    }

    @PostMapping("/api/training-sessions/work-rider-recommendations")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<SessionApi.CandidateList> riders(@Valid @RequestBody SessionApi.DraftRequest request) {
        return ApiResponse.ok(service.riderCandidates(request));
    }

    @GetMapping("/api/training-sessions/{id}/work-rider-recommendations")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<SessionApi.CandidateList> riders(@PathVariable long id) {
        return ApiResponse.ok(service.riderCandidatesForSession(id));
    }

    @GetMapping("/api/groom/sessions")
    @PreAuthorize("hasRole('GROOM')")
    public ApiResponse<List<SessionApi.Session>> groomSessions(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) java.time.LocalDate date) {
        return ApiResponse.ok(service.groomSessions(date));
    }

    @GetMapping("/api/training-sessions/{id}/groom-candidates")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<List<SessionApi.UserRef>> groomCandidates(@PathVariable long id) {
        return ApiResponse.ok(service.groomCandidates(id));
    }

    @PostMapping("/api/training-sessions/{id}/groom-assignment")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<SessionApi.Session> assignGroom(@PathVariable long id,
            @Valid @RequestBody SessionApi.GroomAssignmentRequest request) {
        return ApiResponse.ok(service.assignGroom(id,request.groomUserId()));
    }

    @PostMapping("/api/training-sessions/{id}/reduce-intensity")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<SessionApi.Session> reduceIntensity(@PathVariable long id,
            @Valid @RequestBody SessionApi.IntensityRequest request) {
        return ApiResponse.ok(service.reduceIntensity(id,request.intensity()));
    }

    @PostMapping("/api/training-sessions/{id}/start")
    @PreAuthorize("hasAnyRole('WORK_RIDER','TRAINER')")
    public ApiResponse<SessionApi.Session> start(@PathVariable long id) { return ApiResponse.ok(service.start(id)); }

    @PostMapping("/api/training-sessions/{id}/pause")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<SessionApi.Session> pause(@PathVariable long id) { return ApiResponse.ok(service.transition(id,"IN_PROGRESS","PAUSED")); }

    @PostMapping("/api/training-sessions/{id}/resume")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<SessionApi.Session> resume(@PathVariable long id) { return ApiResponse.ok(service.resume(id)); }
}
