package com.racehorse.staging;

import com.racehorse.common.api.ApiError;
import com.racehorse.common.api.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A DB connectivity probe, not evidence of business write/readback or schema acceptance. */
@RestController
@Profile("staging")
public class StagingReadinessController {
    private final JdbcTemplate jdbc;

    public StagingReadinessController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/health/ready")
    public ResponseEntity<?> ready() {
        try {
            if (Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class))) {
                return ResponseEntity.ok(ApiResponse.ok(new Status("UP")));
            }
        } catch (DataAccessException ignored) {
            // Public readiness deliberately excludes SQL host, credentials and exception details.
        }
        return ResponseEntity.status(503).body(ApiResponse.fail(ApiError.of("SERVICE_UNAVAILABLE", "Database is unavailable.")));
    }

    public record Status(String status) { }
}
