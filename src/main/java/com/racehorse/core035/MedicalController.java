package com.racehorse.core035;

import com.racehorse.common.api.ApiResponse;
import com.racehorse.core035.MedicalDtos.*;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class MedicalController {
    private final MedicalService service;
    public MedicalController(MedicalService service) { this.service = service; }

    @PostMapping("/api/medical/cases")
    @Operation(operationId = "createMedicalCase")
    public ResponseEntity<ApiResponse<CaseDetail>> create(@Valid @RequestBody CaseCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(body)));
    }
    @GetMapping("/api/medical/cases")
    @Operation(operationId = "listMedicalCases")
    public ApiResponse<Page<Object>> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(page, size));
    }
    @GetMapping("/api/medical/cases/{caseId}")
    @Operation(operationId = "getMedicalCase")
    public ApiResponse<Object> get(@PathVariable long caseId) { return ApiResponse.ok(service.get(caseId)); }
    @PostMapping("/api/medical/cases/{caseId}/close")
    @Operation(operationId = "closeMedicalCase")
    public ApiResponse<CaseDetail> close(@PathVariable long caseId) { return ApiResponse.ok(service.close(caseId)); }
    @PostMapping("/api/medical/cases/{caseId}/examinations")
    @Operation(operationId = "createMedicalExamination")
    public ResponseEntity<ApiResponse<ExamView>> examine(@PathVariable long caseId, @Valid @RequestBody ExamCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.examine(caseId, body)));
    }
    @PostMapping("/api/medical/cases/{caseId}/re-examinations")
    @Operation(operationId = "createReexamination")
    public ResponseEntity<ApiResponse<ReexamView>> reexamine(@PathVariable long caseId, @Valid @RequestBody ReexamCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.reexamine(caseId, body)));
    }
    @PostMapping("/api/medical/cases/{caseId}/injuries")
    @Operation(operationId = "createInjury")
    public ResponseEntity<ApiResponse<InjuryView>> injure(@PathVariable long caseId, @Valid @RequestBody InjuryCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.injure(caseId, body)));
    }
    @PutMapping("/api/medical/cases/{caseId}/treatment-plan")
    @Operation(operationId = "putTreatmentPlan")
    public ApiResponse<PlanView> putPlan(@PathVariable long caseId, @Valid @RequestBody PlanPut body) {
        return ApiResponse.ok(service.putPlan(caseId, body));
    }
    @GetMapping("/api/medical/cases/{caseId}/treatment-plan")
    @Operation(operationId = "getTreatmentPlan")
    public ApiResponse<PlanView> plan(@PathVariable long caseId) { return ApiResponse.ok(service.plan(caseId)); }
    @GetMapping("/api/medical/cases/{caseId}/treatment-tasks")
    @Operation(operationId = "listTreatmentCaseTasks")
    public ApiResponse<Page<VetTaskView>> caseTasks(@PathVariable long caseId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.caseTasks(caseId, page, size));
    }
}
