package com.racehorse.training.session;

import com.racehorse.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.racehorse.training.session.Core028Api.*;

@RestController
public class Core028Controller {
    private final Core028Service service;
    public Core028Controller(Core028Service service){this.service=service;}

    @GetMapping("/api/work-rider/assignments")
    @PreAuthorize("hasRole('WORK_RIDER')")
    public ApiResponse<List<Map<String,Object>>> assignments(@RequestParam(required=false) String status){return ApiResponse.ok(service.assignments(status));}

    @GetMapping("/api/work-rider/history")
    @PreAuthorize("hasRole('WORK_RIDER')")
    public ApiResponse<Map<String,Object>> history(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.ok(service.history(page,size));}

    @PostMapping("/api/training-sessions/{id}/complete")
    @PreAuthorize("hasRole('WORK_RIDER')")
    public ApiResponse<Map<String,Object>> complete(@PathVariable long id){return ApiResponse.ok(service.complete(id));}

    @PostMapping("/api/training-sessions/{id}/stop")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<Map<String,Object>> stop(@PathVariable long id,@Valid @RequestBody StopRequest request){return ApiResponse.ok(service.stop(id,request));}

    @PostMapping("/api/training-sessions/{id}/report-issue")
    @PreAuthorize("hasAnyRole('WORK_RIDER','TRAINER')")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<IssueResult> issue(@PathVariable long id,@Valid @RequestBody ReportIssueRequest request){return ApiResponse.ok(service.reportIssue(id,request));}

    @PostMapping("/api/training-sessions/{id}/rider-feedback")
    @PreAuthorize("hasRole('WORK_RIDER')")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<Feedback> feedback(@PathVariable long id,@Valid @RequestBody RiderFeedbackRequest request){return ApiResponse.ok(service.feedback(id,request));}
}
