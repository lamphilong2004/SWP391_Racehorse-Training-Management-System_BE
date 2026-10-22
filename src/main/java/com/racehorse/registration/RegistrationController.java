package com.racehorse.registration;

import com.racehorse.auth.CurrentUser;
import com.racehorse.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.racehorse.registration.RegistrationDtos.*;

@RestController
public class RegistrationController {
    private final RegistrationService service;
    public RegistrationController(RegistrationService service){this.service=service;}
    @io.swagger.v3.oas.annotations.Operation(operationId="register",summary="Submit a neutral access request without a role")
    @PostMapping("/api/auth/register")
    public ResponseEntity<ApiResponse<NeutralMessage>> register(@Valid @RequestBody Register request,HttpServletRequest http){
        return ResponseEntity.accepted().body(ApiResponse.ok(service.register(request,http.getRemoteAddr())));
    }
    @io.swagger.v3.oas.annotations.Operation(operationId="listRegistrations",summary="List registration requests")
    @GetMapping("/api/admin/registrations") @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ApiResponse<Page<Request>> list(@AuthenticationPrincipal CurrentUser actor,@RequestParam(required=false) Status status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ApiResponse.ok(service.list(actor.id(),status,page,size));
    }
    @io.swagger.v3.oas.annotations.Operation(operationId="approveRegistration",summary="Approve and assign exactly one role")
    @PostMapping("/api/admin/registrations/{id}/approve") @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ApiResponse<Request> approve(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id,@Valid @RequestBody Approve request){
        return ApiResponse.ok(service.approve(actor.id(),id,request.roleCode()));
    }
    @io.swagger.v3.oas.annotations.Operation(operationId="rejectRegistration",summary="Reject an access request and clear its hash")
    @PostMapping("/api/admin/registrations/{id}/reject") @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ApiResponse<Request> reject(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id,@Valid @RequestBody(required=false) Reject request){
        return ApiResponse.ok(service.reject(actor.id(),id,request==null?null:request.reason()));
    }
}
