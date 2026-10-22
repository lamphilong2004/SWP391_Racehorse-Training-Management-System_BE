package com.racehorse.horse;

import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/horses")
@Validated
@SecurityRequirement(name="bearerAuth")
public class HorseController {
    private final HorseService service;
    public HorseController(HorseService service){this.service=service;}
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId="createHorse",summary="Create a DRAFT horse identity")
    public ApiResponse<Map<String,Object>> create(@Valid @RequestBody HorseIdentityRequest body){return ApiResponse.ok(service.create(body));}
    @GetMapping
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','TRAINER','VETERINARIAN','GROOM')")
    @Operation(operationId="listHorses",summary="Horse register, Trainer assignment scoped")
    public ApiResponse<HorseRepository.Page> list(
        @RequestParam(required=false) @Size(max=100) String q,
        @RequestParam(required=false) @Pattern(regexp="|DRAFT|ACTIVE|INACTIVE") String lifecycle,
        @RequestParam(required=false) @Pattern(regexp="|FIT|MONITORING|INJURED|QUARANTINED") String healthStatus,
        @RequestParam(required=false) @Pattern(regexp="|AVAILABLE|LIMITED|LOCKED") String availability,
        @RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return ApiResponse.ok(service.list(q,lifecycle,healthStatus,availability,page,size));}
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','TRAINER','VETERINARIAN','GROOM')")
    @Operation(operationId="getHorse",summary="Horse detail, Trainer assignment scoped")
    public ApiResponse<Map<String,Object>> detail(@PathVariable @Min(1) long id){return ApiResponse.ok(service.detail(id));}
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId="updateHorse",summary="Edit horse identification data")
    public ApiResponse<Map<String,Object>> update(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody HorseIdentityRequest body) {
        return ApiResponse.ok(service.update(id, body));
    }
    @PutMapping("/{id}/pedigree")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId="setPedigree",summary="Declare the horse pedigree",
            description="Accepts registered horse references or free-text parent names. A horse cannot be its own parent.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="Updated horse profile."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400", description="Invalid pedigree references."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401", description="Missing, invalid or expired JWT."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403", description="Only CLUB_MANAGER may declare pedigree."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404", description="Horse not found.")
    })
    public ApiResponse<Map<String,Object>> setPedigree(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody PedigreeRequest body) {
        return ApiResponse.ok(service.setPedigree(id, body));
    }
    @GetMapping("/{id}/ownerships")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    @Operation(operationId="listOwnerships",summary="Read current and past horse ownership",
            description="Current ownership rows are returned before historical rows.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="Ownership history."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401", description="Missing, invalid or expired JWT."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403", description="Role is not permitted to read ownership history."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404", description="Horse not found.")
    })
    public ApiResponse<java.util.List<Map<String,Object>>> ownershipHistory(@PathVariable @Min(1) long id) {
        return ApiResponse.ok(service.ownershipHistory(id));
    }
    @PostMapping("/{id}/ownerships")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId="addOwnership",summary="Link an active Horse Owner to the horse",
            description="Requires an ACTIVE app user with the HORSE_OWNER role. Ownership shares are not aggregated or capped.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201", description="Ownership created."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400", description="Invalid ownership or owner is not an active HORSE_OWNER."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401", description="Missing, invalid or expired JWT."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403", description="Only CLUB_MANAGER may add ownership."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404", description="Horse not found.")
    })
    public ApiResponse<Map<String,Object>> addOwnership(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody OwnershipRequest body) {
        return ApiResponse.ok(service.addOwnership(id, body));
    }
    @GetMapping("/{id}/stable-assignments")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    @Operation(operationId="listStableAssignments",summary="Read current and past stable assignments")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="Assignment history, current first."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401", description="Missing, invalid or expired JWT."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403", description="Role is not permitted to read assignment history."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404", description="Horse not found.")
    })
    public ApiResponse<java.util.List<Map<String,Object>>> stableAssignmentHistory(@PathVariable @Min(1) long id) {
        return ApiResponse.ok(service.stableAssignmentHistory(id));
    }
    @PostMapping("/{id}/stable-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId="assignStableBox",summary="Assign or move the horse to a stable box",
            description="Moves close the previous assignment the day before the new inclusive effective date. The close and new assignment are atomic.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201", description="Assignment created."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400", description="Invalid effective date or assignment request."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401", description="Missing, invalid or expired JWT."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403", description="Only CLUB_MANAGER may assign a stable box."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404", description="Horse or stable box not found."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409", description="The requested assignment period overlaps an existing assignment.")
    })
    public ApiResponse<Map<String,Object>> assignStableBox(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody StableAssignmentRequest body) {
        return ApiResponse.ok(service.assignStableBox(id, body));
    }
}
