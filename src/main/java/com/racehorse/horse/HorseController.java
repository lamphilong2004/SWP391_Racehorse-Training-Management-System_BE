package com.racehorse.horse;

import com.racehorse.common.api.ApiResponse;
import com.racehorse.horse.HorseService.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** DEMO IMPLEMENTATION — flow1-horse.openapi.yaml (photos, timeline and Owner reads are not part of the demo). */
@RestController
public class HorseController {
    private final HorseService service;
    public HorseController(HorseService service) { this.service = service; }

    @GetMapping("/api/horses")
    public ApiResponse<Page<HorseSummary>> list(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) String lifecycle,
                                                @RequestParam(required = false) String availability,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(q, lifecycle, availability, page, size));
    }

    @PostMapping("/api/horses")
    public ResponseEntity<ApiResponse<HorseDetail>> create(@RequestBody IdentityRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.create(body)));
    }

    @GetMapping("/api/horses/pending-activation")
    public ApiResponse<List<ActivationStatus>> pending() { return ApiResponse.ok(service.pending()); }

    @GetMapping("/api/horses/{id}")
    public ApiResponse<HorseDetail> detail(@PathVariable long id) { return ApiResponse.ok(service.detail(id)); }

    @PutMapping("/api/horses/{id}")
    public ApiResponse<HorseDetail> update(@PathVariable long id, @RequestBody IdentityRequest body) {
        return ApiResponse.ok(service.update(id, body));
    }

    @PutMapping("/api/horses/{id}/pedigree")
    public ApiResponse<HorseDetail> pedigree(@PathVariable long id, @RequestBody PedigreeRequest body) {
        return ApiResponse.ok(service.pedigree(id, body));
    }

    @GetMapping("/api/horses/{id}/ownerships")
    public ApiResponse<List<Ownership>> ownerships(@PathVariable long id) { return ApiResponse.ok(service.ownerships(id)); }

    @PostMapping("/api/horses/{id}/ownerships")
    public ResponseEntity<ApiResponse<Ownership>> addOwnership(@PathVariable long id, @RequestBody OwnershipRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.addOwnership(id, body)));
    }

    @GetMapping("/api/stable-boxes")
    public ApiResponse<List<StableBox>> boxes() { return ApiResponse.ok(service.boxes()); }

    @GetMapping("/api/horse-owners")
    public ApiResponse<List<UserRef>> owners() { return ApiResponse.ok(service.owners()); }

    @GetMapping("/api/horses/{id}/stable-assignments")
    public ApiResponse<List<StableAssignment>> assignments(@PathVariable long id) { return ApiResponse.ok(service.assignments(id)); }

    @PostMapping("/api/horses/{id}/stable-assignments")
    public ResponseEntity<ApiResponse<StableAssignment>> assign(@PathVariable long id, @RequestBody AssignmentRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.assign(id, body)));
    }

    @GetMapping("/api/horses/{id}/activation-status")
    public ApiResponse<ActivationStatus> activation(@PathVariable long id) { return ApiResponse.ok(service.activationStatus(id)); }

    @PostMapping("/api/horses/{id}/readiness-review")
    public ResponseEntity<Void> review(@PathVariable long id) {
        service.readinessReview(id);
        return ResponseEntity.noContent().build();
    }
}
