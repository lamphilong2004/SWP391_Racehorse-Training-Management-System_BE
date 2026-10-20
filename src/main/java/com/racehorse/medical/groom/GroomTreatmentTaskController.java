package com.racehorse.medical.groom;

import com.racehorse.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class GroomTreatmentTaskController {
    private final GroomTreatmentTaskService service;

    public GroomTreatmentTaskController(GroomTreatmentTaskService service) {
        this.service = service;
    }

    @GetMapping("/api/groom/treatment-tasks")
    @PreAuthorize("hasRole('GROOM')")
    public ApiResponse<GroomTreatmentTasksPage> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(page, size));
    }

    @PostMapping("/api/groom/treatment-tasks/{taskId}/complete")
    @PreAuthorize("hasRole('GROOM')")
    public ApiResponse<GroomTreatmentTask> complete(
            @PathVariable long taskId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.complete(taskId, request));
    }
}
