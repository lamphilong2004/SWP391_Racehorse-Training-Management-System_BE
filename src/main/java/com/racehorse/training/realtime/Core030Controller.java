package com.racehorse.training.realtime;

import com.racehorse.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.racehorse.training.realtime.Core030Api.*;

@RestController
public class Core030Controller {
    private final Core030Service service;
    private final Core030Simulator simulator;
    public Core030Controller(Core030Service service,Core030Simulator simulator){this.service=service;this.simulator=simulator;}
    @PostMapping("/api/training-sessions/{id}/metrics")
    @PreAuthorize("hasRole('WORK_RIDER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MetricResult> metric(@PathVariable long id,@Valid @RequestBody MetricRequest request){return ApiResponse.ok(service.submit(id,request));}
    @PostMapping("/api/simulator/sessions/{id}/start")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<Void> startSimulator(@PathVariable long id){simulator.start(id);return ResponseEntity.noContent().build();}
    @PostMapping("/api/simulator/sessions/{id}/stop")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<Void> stopSimulator(@PathVariable long id){simulator.stop(id);return ResponseEntity.noContent().build();}
    @PostMapping("/api/monitoring-alerts/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('TRAINER','VETERINARIAN')")
    public ApiResponse<Alert> acknowledge(@PathVariable long id,@Valid @RequestBody AcknowledgeRequest request){return ApiResponse.ok(service.acknowledge(id,request));}
}
