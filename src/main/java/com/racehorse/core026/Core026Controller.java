package com.racehorse.core026;

import com.racehorse.common.api.ApiResponse;
import com.racehorse.core026.Contracts.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@RestController
public class Core026Controller {
    private final IncidentService incidents;
    private final PreSessionCheckService checks;

    public Core026Controller(IncidentService incidents, PreSessionCheckService checks) {
        this.incidents = incidents;
        this.checks = checks;
    }

    @PostMapping("/api/incidents")
    public ResponseEntity<ApiResponse<Incident>> create(@Valid @RequestBody IncidentCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(incidents.create(request)));
    }

    @GetMapping("/api/incidents")
    public ApiResponse<IncidentsPage> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(incidents.list(page, size));
    }

    @GetMapping("/api/incidents/{incidentId}")
    public ApiResponse<Incident> detail(@PathVariable long incidentId) {
        return ApiResponse.ok(incidents.detail(incidentId));
    }

    @GetMapping("/api/groom/sessions")
    public ApiResponse<List<SessionSummary>> sessions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(checks.sessions(date));
    }

    @PostMapping("/api/training-sessions/{id}/pre-session-check")
    public ResponseEntity<ApiResponse<PreSessionCheck>> submit(@PathVariable long id,
            @Valid @RequestBody PreSessionCheckRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(checks.submit(id, request)));
    }
}
