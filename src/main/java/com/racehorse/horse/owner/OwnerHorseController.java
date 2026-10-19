package com.racehorse.horse.owner;

import com.racehorse.common.api.ApiResponse;
import com.racehorse.horse.owner.OwnerHorseDtos.HealthSummary;
import com.racehorse.horse.owner.OwnerHorseDtos.HorseProfile;
import com.racehorse.horse.owner.OwnerHorseDtos.TrainingSummary;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/owner/horses")
@PreAuthorize("hasRole('HORSE_OWNER')")
@Validated
@Tag(name = "Owner", description = "Read-only, row-scoped Owner horse summaries.")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OwnerHorseController {

    private final OwnerHorseService ownerHorseService;

    public OwnerHorseController(OwnerHorseService ownerHorseService) {
        this.ownerHorseService = ownerHorseService;
    }

    @GetMapping
    @Operation(
            operationId = "listMyHorses",
            summary = "List horses owned by the current Owner",
            description = "Returns an unpaginated array. Ownership is scoped in SQL to open rows for the authenticated Owner.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "Owned horses; an empty result is an empty array.",
                            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {"success":true,"data":[{"id":1201,"horseCode":"HR-01201","name":"Silver Comet","breed":"Arabian","sex":"MARE","foaledDate":"2020-04-12","lifecycle":"ACTIVE","healthStatus":"FIT","trainingAvailability":"AVAILABLE"}]}
                                    """))),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required."),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "The authenticated role is not HORSE_OWNER.")
            })
    public ApiResponse<List<OwnerHorseDtos.HorseCard>> listMyHorses() {
        return ApiResponse.ok(ownerHorseService.listOwnedHorses());
    }

    @GetMapping("/{id}/profile")
    @Operation(
            operationId = "getMyHorseProfile",
            summary = "Get an owned horse profile and pedigree",
            description = "Returns only the authenticated Owner's own ownership row. Non-owned horses return 403; missing horses return 404.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "Owned horse profile.",
                            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {"success":true,"data":{"horse":{"id":1201,"horseCode":"HR-01201","name":"Silver Comet","breed":"Arabian","sex":"MARE","foaledDate":"2020-04-12","lifecycle":"ACTIVE","healthStatus":"FIT","trainingAvailability":"AVAILABLE"},"countryOfOrigin":"United Kingdom","registrationNo":"REG-1201","activatedAt":"2025-01-02T03:04:05Z","pedigree":{"sire":{"horseId":1101,"horseCode":"HR-01101","name":null},"dam":{"horseId":null,"horseCode":null,"name":"Quiet Dawn"}},"myOwnership":{"sharePercentage":40.00,"effectiveFrom":"2024-01-01","isManagingOwner":false}}}
                                    """))),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "The horse id is invalid."),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required."),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "The horse is not owned by the authenticated Owner."),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "The horse does not exist.")
            })
    public ApiResponse<HorseProfile> getMyHorseProfile(@PathVariable("id") @Positive long id) {
        return ApiResponse.ok(ownerHorseService.getOwnedProfile(id));
    }

    @GetMapping("/{id}/health-summary")
    @Operation(
            operationId = "getMyHorseHealthSummary",
            summary = "Get a non-clinical health and readiness summary",
            description = "Excludes findings, diagnosis, prescriptions, treatment protocols, and restriction reasons.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "Non-clinical health summary.",
                            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {"success":true,"data":{"healthStatus":"MONITORING","trainingAvailability":"LIMITED","restrictionReviewDate":"2026-08-12","recoveryStatus":"IMPROVING","lastExaminedAt":"2026-06-05T10:15:30Z","weights":[{"weightKg":452.50,"recordedAt":"2026-06-01T00:00:00Z"}]}}
                                    """))),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "The horse id is invalid."),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required."),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "The horse is not owned by the authenticated Owner."),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "The horse does not exist.")
            })
    public ApiResponse<HealthSummary> getMyHorseHealthSummary(@PathVariable("id") @Positive long id) {
        return ApiResponse.ok(ownerHorseService.getOwnedHealthSummary(id));
    }

    @GetMapping("/{id}/training-summary")
    @Operation(
            operationId = "getMyHorseTrainingSummary",
            summary = "Get the current program and recent sessions",
            description = "The current program is ACTIVE and covers the current UTC date; if several qualify, the latest activation and then highest program ID are selected. The current phase is returned only when exactly one phase covers today; progress counts completed and all sessions in the selected program. Recent sessions include all statuses across the horse's programs, ordered by scheduled start and then session ID newest first, limited to 10. Evaluation notes, exercise details, race fields, and clinical data are excluded.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "Training summary.",
                            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {"success":true,"data":{"currentProgram":{"name":"Conditioning","status":"ACTIVE","periodStart":"2026-01-01","periodEnd":"2026-12-31","currentPhaseName":"Base","sessionsCompleted":8,"sessionsPlanned":12},"recentSessions":[{"sessionCode":"TS-01201","scheduledStart":"2026-06-05T10:00:00Z","status":"COMPLETED","evaluationLabel":"SATISFACTORY"}]}}
                                    """))),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "The horse id is invalid."),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required."),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "The horse is not owned by the authenticated Owner."),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "The horse does not exist.")
            })
    public ApiResponse<TrainingSummary> getMyHorseTrainingSummary(@PathVariable("id") @Positive long id) {
        return ApiResponse.ok(ownerHorseService.getOwnedTrainingSummary(id));
    }
}
