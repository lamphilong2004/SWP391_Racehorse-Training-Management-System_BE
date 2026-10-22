package com.racehorse.horse.stable;

import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stable-boxes")
@Tag(name = "Stable")
@PreAuthorize("hasAnyRole('CLUB_MANAGER', 'HEAD_TRAINER')")
public class StableBoxController {

    private final StableBoxService service;

    public StableBoxController(StableBoxService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            operationId = "listStableBoxes",
            summary = "List stable boxes and their current occupancy",
            description = """
                    Occupancy is derived from open horse_stable_assignment rows. Box administration is outside this API.
                    Access is limited to CLUB_MANAGER and HEAD_TRAINER.

                    Example request: GET /api/stable-boxes?status=FREE
                    Example response: {"success":true,"data":[{"id":1,"boxCode":"A-01","yard":"North","status":"OCCUPIED","currentHorse":{"id":12,"horseCode":"RH-012","name":"Silver Comet"}}]}
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stable boxes."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid occupancy filter."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid or expired JWT."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "The role is not permitted to read stable boxes.")
    })
    public ApiResponse<List<StableBoxResponse>> listStableBoxes(
            @Parameter(
                    name = "status",
                    in = ParameterIn.QUERY,
                    description = "Optional derived occupancy filter: FREE or OCCUPIED.")
            @RequestParam(required = false) BoxStatus status) {
        return ApiResponse.ok(service.list(status));
    }
}
