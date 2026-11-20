package com.racehorse.horse.stable;

import com.racehorse.common.api.ApiResponse;
import com.racehorse.horse.stable.StableLayoutDtos.CreateYardRequest;
import com.racehorse.horse.stable.StableLayoutDtos.StableMap;
import com.racehorse.horse.stable.StableLayoutDtos.UpdateBoxStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** DEC-016 stable layout: map for staff, yard creation and box maintenance for the Club Manager. */
@RestController
@Tag(name = "Stable")
public class StableLayoutController {

    private final StableLayoutService service;

    public StableLayoutController(StableLayoutService service) {
        this.service = service;
    }

    @GetMapping("/api/stable-map")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','TRAINER','VETERINARIAN','GROOM','WORK_RIDER')")
    @Operation(operationId = "getStableMap", summary = "Stable layout: yards as row x column grids with the horse in each box",
            description = "Capacity is the sum of yard grids. A legacy box outside every yard that still holds a horse is listed"
                    + " in outsideLayout so it can be moved. Horse Owner has no access.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<StableMap> map() {
        return ApiResponse.ok(service.map());
    }

    @PostMapping("/api/stable-yards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId = "createStableYard", summary = "Create a yard; its rows x columns boxes are created with it",
            description = "Limits: at most 6 yards, 1-4 rows, 1-10 columns, yard code one letter A-Z. Returns the updated map.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<StableMap> createYard(@RequestBody CreateYardRequest request) {
        return ApiResponse.ok(service.createYard(request));
    }

    @PatchMapping("/api/stable-boxes/{boxId}/status")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId = "updateStableBoxStatus", summary = "Put a free box under maintenance or reopen it",
            description = "status ACTIVE or MAINTENANCE; a reason is required for MAINTENANCE. An occupied box cannot be put"
                    + " under maintenance. A box under maintenance cannot receive a horse. Returns the updated map.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<StableMap> updateBoxStatus(@PathVariable long boxId, @RequestBody UpdateBoxStatusRequest request) {
        return ApiResponse.ok(service.updateBoxStatus(boxId, request));
    }
}
