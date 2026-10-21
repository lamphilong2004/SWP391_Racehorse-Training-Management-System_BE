package com.racehorse.horse.timeline;

import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/horses")
@Validated
@SecurityRequirement(name = "bearerAuth")
public class HorseTimelineController {

    private final HorseTimelineService service;

    public HorseTimelineController(HorseTimelineService service) {
        this.service = service;
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','TRAINER','VETERINARIAN','GROOM')")
    @Operation(operationId = "getHorseTimeline", summary = "Read a horse's non-clinical timeline",
            description = "Events begin at horse.activated_at. DATE-valued effective_from values are represented at 00:00:00Z, not as exact operation times. Scheduled-only sessions are excluded.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Newest-first page of timeline events."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid page or size."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid or expired JWT."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Role is not permitted or the horse is outside the caller's assignment scope."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Horse not found.")
    })
    public ApiResponse<TimelinePage> getTimeline(
            @PathVariable @Min(1) long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.getTimeline(id, page, size));
    }
}
