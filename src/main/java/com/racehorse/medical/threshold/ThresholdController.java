package com.racehorse.medical.threshold;

import com.racehorse.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.racehorse.medical.threshold.ThresholdApi.*;

@RestController
@RequestMapping("/api")
public class ThresholdController {
    private final ThresholdService service;
    public ThresholdController(ThresholdService service) { this.service = service; }

    @GetMapping("/medical/thresholds")
    public ApiResponse<List<Threshold>> listGlobal() { return ApiResponse.ok(service.global()); }

    @GetMapping("/horses/{horseId}/medical-thresholds")
    public ApiResponse<List<Threshold>> effective(@PathVariable long horseId) {
        return ApiResponse.ok(service.effectiveForHorse(horseId));
    }

    @PutMapping("/horses/{horseId}/medical-thresholds")
    public ApiResponse<Threshold> set(@PathVariable long horseId, @Valid @RequestBody OverrideRequest request) {
        return ApiResponse.ok(service.setOverride(horseId, request));
    }

    @PostMapping("/horses/{horseId}/medical-thresholds/end")
    public ApiResponse<Threshold> end(@PathVariable long horseId, @Valid @RequestBody EndRequest request) {
        return ApiResponse.ok(service.endOverride(horseId, request));
    }
}
