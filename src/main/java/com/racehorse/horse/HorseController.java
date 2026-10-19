package com.racehorse.horse;

import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
}
