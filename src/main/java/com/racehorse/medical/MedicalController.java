package com.racehorse.medical;

import com.racehorse.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/horses/{horseId}")
public class MedicalController {
    private final MedicalService service;
    public MedicalController(MedicalService service) { this.service = service; }

    @PostMapping("/baseline-examinations")
    public ResponseEntity<ApiResponse<BaselineExaminationResponse>> baseline(
            @PathVariable long horseId, @RequestBody BaselineExaminationRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.recordBaseline(horseId, request)));
    }

    @PostMapping("/weights")
    public ResponseEntity<ApiResponse<WeightRecordResponse>> weight(
            @PathVariable long horseId, @RequestBody WeightRecordRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.recordWeight(horseId, request)));
    }

    @GetMapping("/weights")
    public ApiResponse<WeightRecordsPage> weights(@PathVariable long horseId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.listWeights(horseId, page, size));
    }
}
