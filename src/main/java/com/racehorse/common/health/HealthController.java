package com.racehorse.common.health;

import com.racehorse.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness check used by the frontend, scripts and deployment. Public (see DEC-008 §6 R10). */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    public record HealthStatus(String status) {
    }

    @GetMapping
    public ApiResponse<HealthStatus> health() {
        return ApiResponse.ok(new HealthStatus("UP"));
    }
}
