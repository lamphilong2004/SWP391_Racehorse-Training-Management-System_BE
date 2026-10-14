package com.racehorse.training;

import com.racehorse.common.api.ApiResponse;
import com.racehorse.realtime.RealtimeService;
import com.racehorse.training.ProgramService.*;
import com.racehorse.training.SessionReadModel.SessionDetail;
import com.racehorse.training.SessionReadModel.SessionSummary;
import com.racehorse.training.SessionService.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** DEMO IMPLEMENTATION — flow2-training.openapi.yaml operations used by the main demo path. */
@RestController
public class TrainingController {
    private final ProgramService programs;
    private final SessionService sessions;
    private final RealtimeService realtime;

    public TrainingController(ProgramService programs, SessionService sessions, RealtimeService realtime) {
        this.programs = programs;
        this.sessions = sessions;
        this.realtime = realtime;
    }

    // ---- templates / recommendations / exercises ----
    @GetMapping("/api/program-templates")
    public ApiResponse<List<Template>> templates(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return ApiResponse.ok(programs.templates(includeArchived));
    }

    @GetMapping("/api/program-templates/{id}")
    public ApiResponse<Template> template(@PathVariable long id) { return ApiResponse.ok(programs.templateDetail(id)); }

    @GetMapping("/api/horses/{id}/template-recommendations")
    public ApiResponse<List<Recommendation>> templateRecs(@PathVariable long id) {
        return ApiResponse.ok(programs.templateRecommendations(id));
    }

    @GetMapping("/api/horses/{id}/trainer-recommendations")
    public ApiResponse<List<Recommendation>> trainerRecs(@PathVariable long id, @RequestParam(required = false) Long templateId) {
        return ApiResponse.ok(programs.trainerRecommendations(id, templateId));
    }

    @GetMapping("/api/exercises")
    public ApiResponse<List<Exercise>> exercises() { return ApiResponse.ok(programs.exercises()); }

    // ---- programs ----
    @PostMapping("/api/training-programs")
    public ResponseEntity<ApiResponse<Program>> createProgram(@RequestBody CreateProgramRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.ok(programs.create(body)));
    }

    @GetMapping("/api/training-programs")
    public ApiResponse<List<Program>> listPrograms(@RequestParam(required = false) Long horseId,
                                                   @RequestParam(defaultValue = "false") boolean assignedToMe) {
        return ApiResponse.ok(programs.list(horseId, assignedToMe));
    }

    @GetMapping("/api/training-programs/{id}")
    public ApiResponse<Program> program(@PathVariable long id) { return ApiResponse.ok(programs.detail(id)); }

    @GetMapping("/api/training-programs/{id}/progress")
    public ApiResponse<Object> progress(@PathVariable long id) { return ApiResponse.ok(programs.detail(id).progress()); }

    // ---- sessions ----
    @PostMapping("/api/training-sessions/suitability-check")
    public ApiResponse<Suitability> suitability(@RequestBody SuitabilityRequest body) { return ApiResponse.ok(sessions.suitability(body)); }

    @PostMapping("/api/training-sessions/work-rider-recommendations")
    public ApiResponse<List<Recommendation>> riders(@RequestBody(required = false) RiderRequest body) {
        return ApiResponse.ok(sessions.riderRecommendations(body));
    }

    @PostMapping("/api/training-sessions")
    public ResponseEntity<ApiResponse<SessionDetail>> createSession(@RequestBody CreateSessionRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.ok(sessions.create(body)));
    }

    @GetMapping("/api/training-sessions")
    public ApiResponse<List<SessionSummary>> listSessions(@RequestParam(required = false) Instant from,
                                                          @RequestParam(required = false) Instant to,
                                                          @RequestParam(required = false) Long trainerId,
                                                          @RequestParam(required = false) Long programId,
                                                          @RequestParam(required = false) Long horseId) {
        return ApiResponse.ok(sessions.list(from, to, trainerId, programId, horseId));
    }

    @GetMapping("/api/training-sessions/{id}")
    public ApiResponse<SessionDetail> session(@PathVariable long id) { return ApiResponse.ok(sessions.detail(id)); }

    @PostMapping("/api/training-sessions/{id}/start")
    public ApiResponse<SessionDetail> start(@PathVariable long id) { return ApiResponse.ok(sessions.start(id)); }

    @PostMapping("/api/training-sessions/{id}/pause")
    public ApiResponse<SessionDetail> pause(@PathVariable long id) { return ApiResponse.ok(sessions.pause(id)); }

    @PostMapping("/api/training-sessions/{id}/resume")
    public ApiResponse<SessionDetail> resume(@PathVariable long id) { return ApiResponse.ok(sessions.resume(id)); }

    @PostMapping("/api/training-sessions/{id}/stop")
    public ApiResponse<SessionDetail> stop(@PathVariable long id, @RequestBody StopRequest body) {
        return ApiResponse.ok(sessions.stop(id, body));
    }

    @PostMapping("/api/training-sessions/{id}/complete")
    public ApiResponse<SessionDetail> complete(@PathVariable long id) { return ApiResponse.ok(sessions.complete(id)); }

    @PostMapping("/api/training-sessions/{id}/reduce-intensity")
    public ApiResponse<SessionDetail> reduce(@PathVariable long id, @RequestBody IntensityRequest body) {
        return ApiResponse.ok(sessions.reduceIntensity(id, body));
    }

    @PostMapping("/api/training-sessions/{id}/report-issue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reportIssue(@PathVariable long id, @RequestBody ReportIssueRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.ok(sessions.reportIssue(id, body)));
    }

    @PostMapping("/api/training-sessions/{id}/rider-feedback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> feedback(@PathVariable long id, @RequestBody FeedbackRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.ok(sessions.feedback(id, body)));
    }

    @PostMapping("/api/training-sessions/{id}/evaluation")
    public ResponseEntity<ApiResponse<Map<String, Object>>> evaluation(@PathVariable long id, @RequestBody EvaluationRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.ok(sessions.evaluation(id, body)));
    }

    @GetMapping("/api/training-sessions/{id}/result")
    public ApiResponse<Map<String, Object>> result(@PathVariable long id) { return ApiResponse.ok(sessions.result(id)); }

    @GetMapping("/api/work-rider/assignments")
    public ApiResponse<List<SessionSummary>> assignments() { return ApiResponse.ok(sessions.riderAssignments(false)); }

    @GetMapping("/api/work-rider/history")
    public ApiResponse<List<SessionSummary>> history() { return ApiResponse.ok(sessions.riderAssignments(true)); }

    // ---- realtime ----
    @PostMapping("/api/training-sessions/{id}/metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> metrics(@PathVariable long id, @RequestBody RealtimeService.MetricInput body) {
        return ResponseEntity.status(201).body(ApiResponse.ok(realtime.submit(id, body)));
    }

    @PostMapping("/api/simulator/sessions/{id}/start")
    public ResponseEntity<Void> simStart(@PathVariable long id, @RequestParam(defaultValue = "escalating") String profile) {
        realtime.startSimulator(id, profile);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/simulator/sessions/{id}/stop")
    public ResponseEntity<Void> simStop(@PathVariable long id) {
        realtime.stopSimulator(id, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/monitoring-alerts/{id}/acknowledge")
    public ApiResponse<Map<String, Object>> acknowledge(@PathVariable long id, @RequestBody RealtimeService.AckRequest body) {
        return ApiResponse.ok(realtime.acknowledge(id, body));
    }
}
