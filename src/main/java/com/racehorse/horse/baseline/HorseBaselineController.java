package com.racehorse.horse.baseline;

import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/horses/{horseId}")
@Validated
@SecurityRequirement(name = "bearerAuth")
public class HorseBaselineController {

    private final HorseBaselineService service;

    public HorseBaselineController(HorseBaselineService service) {
        this.service = service;
    }

    @PostMapping("/baseline-examinations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VETERINARIAN')")
    @Operation(operationId = "recordBaselineExamination", summary = "Record initial veterinarian baseline")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Baseline recorded."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid baseline fields."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid or expired JWT."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Only an active Veterinarian may record a baseline."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Horse not found."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "A baseline already exists.")
    })
    public ApiResponse<BaselineExamination> recordBaseline(
            @PathVariable @Min(1) long horseId,
            @Valid @RequestBody BaselineExaminationRequest request) {
        return ApiResponse.ok(service.recordBaseline(horseId, request));
    }

    @GetMapping("/weights")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','TRAINER','VETERINARIAN','GROOM')")
    @Operation(operationId = "listHorseWeightRecords", summary = "Read horse weight history")
    public ApiResponse<WeightRecordsPage> listWeights(
            @PathVariable @Min(1) long horseId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.listWeights(horseId, page, size));
    }

    @PostMapping("/weights")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VETERINARIAN')")
    @Operation(operationId = "recordHorseWeight", summary = "Record horse weight")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Weight recorded."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid weight fields."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid or expired JWT."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Only an active Veterinarian may record weight."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Horse not found.")
    })
    public ApiResponse<WeightRecord> recordWeight(
            @PathVariable @Min(1) long horseId,
            @Valid @RequestBody WeightRecordRequest request) {
        return ApiResponse.ok(service.recordWeight(horseId, request));
    }
}
