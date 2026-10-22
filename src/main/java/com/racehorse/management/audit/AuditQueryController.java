package com.racehorse.management.audit;

import com.racehorse.auth.CurrentUser;
import com.racehorse.common.api.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController
@RequestMapping("/api/admin/audit-log")
@PreAuthorize("hasRole('CLUB_MANAGER')")
public class AuditQueryController {
    private final AuditQueryService service;
    public AuditQueryController(AuditQueryService service){this.service=service;}
    @io.swagger.v3.oas.annotations.Operation(operationId="listAuditLog",summary="Read-only Club Manager audit log")
    @GetMapping
    public ApiResponse<AuditQueryService.Page> list(@AuthenticationPrincipal CurrentUser reader,
        @RequestParam(required=false) Long actor,@RequestParam(required=false) String action,
        @RequestParam(required=false) String targetType,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ApiResponse.ok(service.list(reader.id(),actor,action,targetType,from,to,page,size));
    }
}
