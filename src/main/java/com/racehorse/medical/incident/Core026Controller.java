package com.racehorse.medical.incident;

import com.racehorse.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.racehorse.medical.incident.IncidentApi.*;

@RestController
public class Core026Controller {
    private final Core026Service service;
    public Core026Controller(Core026Service service){this.service=service;}

    @PostMapping("/api/training-sessions/{id}/pre-session-check")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PreSessionCheck> precheck(@PathVariable long id,@Valid @RequestBody PreSessionCheckRequest request){
        return ApiResponse.ok(service.submitPrecheck(id,request));
    }

    @PostMapping("/api/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Incident> create(@Valid @RequestBody CreateRequest request){return ApiResponse.ok(service.create(request));}

    @GetMapping("/api/incidents")
    public ApiResponse<Page> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ApiResponse.ok(service.list(page,size));
    }

    @GetMapping("/api/incidents/{incidentId}")
    public ApiResponse<Incident> get(@PathVariable long incidentId){return ApiResponse.ok(service.get(incidentId));}

    @PostMapping("/api/incidents/{incidentId}/review")
    public ApiResponse<Incident> review(@PathVariable long incidentId,@Valid @RequestBody ReviewRequest request){
        return ApiResponse.ok(service.review(incidentId,request));
    }
}
