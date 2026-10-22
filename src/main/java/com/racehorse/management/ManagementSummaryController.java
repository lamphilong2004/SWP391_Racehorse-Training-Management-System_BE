package com.racehorse.management;

import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('CLUB_MANAGER')")
public class ManagementSummaryController {
    private final ManagementSummaryService service;

    public ManagementSummaryController(ManagementSummaryService service) {
        this.service = service;
    }

    @GetMapping("/api/dashboard/club-manager")
    @Operation(operationId = "getClubManagerDashboard",
            summary = "Club Manager dashboard, core widgets",
            description = "Read-only core counts from SQL, with no clinical detail. Training today uses Asia/Ho_Chi_Minh calendar dates.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<ManagementSummaryService.Dashboard> dashboard() {
        return ApiResponse.ok(service.dashboard());
    }
}
