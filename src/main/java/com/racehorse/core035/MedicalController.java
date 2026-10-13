package com.racehorse.core035;

import com.racehorse.common.api.ApiResponse;
import com.racehorse.core035.MedicalDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class MedicalController {
    private final MedicalService service;
    public MedicalController(MedicalService service) { this.service = service; }

    @PostMapping("/api/medical/cases")
    public ResponseEntity<ApiResponse<CaseDetail>> create(@Valid @RequestBody CaseCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(body)));
    }
    @GetMapping("/api/medical/cases")
    public ApiResponse<Page<Object>> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(page, size));
    }
    @GetMapping("/api/medical/cases/{caseId}")
    public ApiResponse<Object> get(@PathVariable long caseId) { return ApiResponse.ok(service.get(caseId)); }
    @PostMapping("/api/medical/cases/{caseId}/close")
    public ApiResponse<CaseDetail> close(@PathVariable long caseId) { return ApiResponse.ok(service.close(caseId)); }
    @PostMapping("/api/medical/cases/{caseId}/examinations")
    public ResponseEntity<ApiResponse<ExamView>> examine(@PathVariable long caseId, @Valid @RequestBody ExamCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.examine(caseId, body)));
    }
    @PostMapping("/api/medical/cases/{caseId}/re-examinations")
    public ResponseEntity<ApiResponse<ReexamView>> reexamine(@PathVariable long caseId, @Valid @RequestBody ReexamCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.reexamine(caseId, body)));
    }
    @PostMapping("/api/medical/cases/{caseId}/injuries")
    public ResponseEntity<ApiResponse<InjuryView>> injure(@PathVariable long caseId, @Valid @RequestBody InjuryCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.injure(caseId, body)));
    }
    @PutMapping("/api/medical/cases/{caseId}/treatment-plan")
    public ApiResponse<PlanView> putPlan(@PathVariable long caseId, @Valid @RequestBody PlanPut body) {
        return ApiResponse.ok(service.putPlan(caseId, body));
    }
    @GetMapping("/api/medical/cases/{caseId}/treatment-plan")
    public ApiResponse<PlanView> plan(@PathVariable long caseId) { return ApiResponse.ok(service.plan(caseId)); }
    @GetMapping("/api/medical/cases/{caseId}/treatment-tasks")
    public ApiResponse<Page<VetTaskView>> caseTasks(@PathVariable long caseId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.caseTasks(caseId, page, size));
    }
    @GetMapping("/api/groom/treatment-tasks")
    public ApiResponse<Page<TaskView>> groomTasks(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return ApiResponse.ok(service.groomTasks(page, size)); }
    @PostMapping("/api/groom/treatment-tasks/{taskId}/complete")
    public ApiResponse<TaskView> complete(@PathVariable long taskId, @RequestBody Map<String, Object> body) {
        if (!body.isEmpty()) throw MedicalService.error(com.racehorse.common.error.ErrorCode.VALIDATION_ERROR, "Completion request must be empty.");
        return ApiResponse.ok(service.complete(taskId));
    }
}
