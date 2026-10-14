package com.racehorse.restriction;

import com.racehorse.common.api.ApiResponse;
import com.racehorse.identity.RoleCode;
import com.racehorse.integration.Db;
import com.racehorse.restriction.RestrictionService.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** DEMO IMPLEMENTATION — flow3-medical.openapi.yaml restriction / availability / threshold read paths. */
@RestController
public class RestrictionController {
    private final RestrictionService service;
    public RestrictionController(RestrictionService service) { this.service = service; }

    @GetMapping("/api/horses/{horseId}/training-availability")
    public ApiResponse<Availability> availability(@PathVariable long horseId) {
        Db.require(RoleCode.CLUB_MANAGER, RoleCode.HEAD_TRAINER, RoleCode.TRAINER, RoleCode.WORK_RIDER,
                RoleCode.VETERINARIAN, RoleCode.GROOM);
        return ApiResponse.ok(service.availability(horseId));
    }

    @GetMapping("/api/medical/restrictions")
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) Long horseId,
                                                 @RequestParam(defaultValue = "false") boolean active) {
        List<Restriction> items = service.list(horseId, active);
        return ApiResponse.ok(Map.of("items", items, "page", 0, "size", items.size(), "totalElements", items.size()));
    }

    @PostMapping("/api/horses/{horseId}/medical-restrictions")
    public ResponseEntity<ApiResponse<Restriction>> create(@PathVariable long horseId, @RequestBody CreateRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.create(horseId, body)));
    }

    @PostMapping("/api/medical/restrictions/{restrictionId}/lift")
    public ApiResponse<Restriction> lift(@PathVariable long restrictionId) {
        return ApiResponse.ok(service.lift(restrictionId));
    }

    @PutMapping("/api/medical/restrictions/{restrictionId}/exercise-rules")
    public ApiResponse<Restriction> rules(@PathVariable long restrictionId, @RequestBody Map<String, List<Rule>> body) {
        return ApiResponse.ok(service.putRules(restrictionId, body.get("rules")));
    }

    @GetMapping("/api/medical/thresholds")
    public ApiResponse<List<Threshold>> thresholds() { return ApiResponse.ok(service.thresholds(null)); }

    @GetMapping("/api/horses/{horseId}/medical-thresholds")
    public ApiResponse<List<Threshold>> horseThresholds(@PathVariable long horseId) {
        return ApiResponse.ok(service.thresholds(horseId));
    }
}
