package com.racehorse.management;

import com.racehorse.auth.AccountStatus;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Approved shared user lookup needed by the Flow1 owner picker. */
@RestController
@PreAuthorize("hasRole('CLUB_MANAGER')")
public class UserDirectoryController {
    private final UserDirectoryService service;
    public UserDirectoryController(UserDirectoryService service) { this.service=service; }

    @GetMapping("/api/admin/users")
    @Operation(operationId="listUsers", summary="Read the Club Manager user directory")
    public ApiResponse<UserDirectoryService.Page> list(@RequestParam(required=false) String q,
            @RequestParam(required=false) RoleCode role, @RequestParam(required=false) AccountStatus status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.list(q,role,status,page,size));
    }
}
