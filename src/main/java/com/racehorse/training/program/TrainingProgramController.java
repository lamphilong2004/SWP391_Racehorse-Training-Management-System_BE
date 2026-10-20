package com.racehorse.training.program;

import com.racehorse.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrainingProgramController {

    private final TrainingProgramService service;

    public TrainingProgramController(TrainingProgramService service) {
        this.service = service;
    }

    @GetMapping("/api/program-templates")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','TRAINER','CLUB_MANAGER')")
    public ApiResponse<ProgramApi.Page<ProgramApi.TemplateSummary>> templates(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.listTemplates(status, q, page, size));
    }

    @PostMapping("/api/program-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HEAD_TRAINER')")
    public ApiResponse<ProgramApi.TemplateDetail> createTemplate(
            @Valid @RequestBody ProgramApi.TemplateVersionRequest request) {
        return ApiResponse.ok(service.createTemplate(request));
    }

    @GetMapping("/api/program-templates/{id}")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','TRAINER','CLUB_MANAGER')")
    public ApiResponse<ProgramApi.TemplateDetail> template(
            @PathVariable long id,
            @RequestParam(required = false) Integer versionNo) {
        return ApiResponse.ok(service.templateDetail(id, versionNo));
    }

    @PostMapping("/api/program-templates/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HEAD_TRAINER')")
    public ApiResponse<ProgramApi.TemplateDetail> saveTemplateVersion(
            @PathVariable long id,
            @Valid @RequestBody ProgramApi.TemplateVersionRequest request) {
        return ApiResponse.ok(service.saveTemplateVersion(id, request));
    }

    @PostMapping("/api/program-templates/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HEAD_TRAINER')")
    public ApiResponse<ProgramApi.TemplateDetail> duplicateTemplate(
            @PathVariable long id,
            @Valid @RequestBody ProgramApi.DuplicateTemplateRequest request) {
        return ApiResponse.ok(service.duplicateTemplate(id, request));
    }

    @PostMapping("/api/program-templates/{id}/archive")
    @PreAuthorize("hasRole('HEAD_TRAINER')")
    public ApiResponse<ProgramApi.TemplateSummary> archiveTemplate(@PathVariable long id) {
        return ApiResponse.ok(service.archiveTemplate(id));
    }

    @GetMapping("/api/training-programs")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','TRAINER','CLUB_MANAGER','VETERINARIAN','GROOM')")
    public ApiResponse<ProgramApi.Page<ProgramApi.ProgramSummary>> programs(
            @RequestParam(required = false) Long horseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long trainerId,
            @RequestParam(required = false) Boolean assignedToMe,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.listPrograms(horseId, status, trainerId, assignedToMe, page, size));
    }

    @PostMapping("/api/training-programs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HEAD_TRAINER')")
    public ApiResponse<ProgramApi.ProgramDetail> createProgram(
            @Valid @RequestBody ProgramApi.CreateProgramRequest request) {
        return ApiResponse.ok(service.createProgram(request));
    }

    @GetMapping("/api/training-programs/{id}")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','TRAINER','CLUB_MANAGER','VETERINARIAN','GROOM')")
    public ApiResponse<ProgramApi.ProgramDetail> program(@PathVariable long id) {
        return ApiResponse.ok(service.programDetail(id));
    }

    @GetMapping("/api/training-programs/{id}/progress")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','TRAINER','CLUB_MANAGER')")
    public ApiResponse<ProgramApi.ProgramProgress> progress(@PathVariable long id) {
        return ApiResponse.ok(service.programProgress(id));
    }

    @GetMapping("/api/training-programs/{id}/history")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','TRAINER','CLUB_MANAGER')")
    public ApiResponse<java.util.List<ProgramApi.HistoryEntry>> history(@PathVariable long id) {
        return ApiResponse.ok(service.programHistory(id));
    }

    @org.springframework.web.bind.annotation.PatchMapping("/api/training-programs/{id}/status")
    @PreAuthorize("hasRole('HEAD_TRAINER')")
    public ApiResponse<ProgramApi.ProgramDetail> status(
            @PathVariable long id,
            @Valid @RequestBody ProgramApi.SetProgramStatusRequest request) {
        return ApiResponse.ok(service.setProgramStatus(id, request));
    }

    @PutMapping("/api/training-programs/{id}/trainer-assignment")
    @PreAuthorize("hasRole('HEAD_TRAINER')")
    public ApiResponse<ProgramApi.ProgramDetail> assignTrainer(
            @PathVariable long id,
            @Valid @RequestBody ProgramApi.TrainerAssignmentRequest request) {
        return ApiResponse.ok(service.reassignTrainer(id, request));
    }

    @PutMapping("/api/training-programs/{id}/phases")
    @PreAuthorize("hasRole('HEAD_TRAINER')")
    public ApiResponse<ProgramApi.ProgramDetail> adjustPhases(
            @PathVariable long id,
            @Valid @RequestBody ProgramApi.AdjustProgramPhasesRequest request) {
        return ApiResponse.ok(service.adjustPhases(id, request));
    }
}
