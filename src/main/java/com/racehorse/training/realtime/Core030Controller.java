package com.racehorse.training.realtime;

import com.racehorse.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.racehorse.training.realtime.Core030Api.*;

@RestController
public class Core030Controller {
    private final Core030Service service;
    public Core030Controller(Core030Service service){this.service=service;}
    @PostMapping("/api/training-sessions/{id}/metrics")
    @PreAuthorize("hasRole('WORK_RIDER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MetricResult> metric(@PathVariable long id,@Valid @RequestBody MetricRequest request){return ApiResponse.ok(service.submit(id,request));}
    @PostMapping("/api/monitoring-alerts/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('TRAINER','VETERINARIAN')")
    public ApiResponse<Alert> acknowledge(@PathVariable long id,@Valid @RequestBody AcknowledgeRequest request){return ApiResponse.ok(service.acknowledge(id,request));}
}
