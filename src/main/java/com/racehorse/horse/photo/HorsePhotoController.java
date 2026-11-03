package com.racehorse.horse.photo;

import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@Validated
@Profile({"dev", "test"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Horses")
public class HorsePhotoController {

    private final HorsePhotoService service;

    public HorsePhotoController(HorsePhotoService service) {
        this.service = service;
    }

    @GetMapping("/api/horses/{id}/photos")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','TRAINER','VETERINARIAN','GROOM')")
    @Operation(
            operationId = "listHorsePhotos",
            summary = "List horse photos newest first",
            description = "TRAINER access is limited to horses with an open trainer_assignment.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Photo metadata."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "The role is not permitted."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Horse not found.")
    })
    public ApiResponse<List<HorsePhoto>> list(@PathVariable("id") @Positive long horseId) {
        return ApiResponse.ok(service.list(horseId));
    }

    @GetMapping("/api/horses/{id}/photos/primary")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','TRAINER','VETERINARIAN','GROOM')")
    @Operation(operationId = "getPrimaryHorsePhoto", summary = "Read the latest uploaded horse photo")
    public ResponseEntity<Resource> staffPrimary(@PathVariable("id") @Positive long horseId) {
        return image(service.primaryForStaff(horseId));
    }

    @GetMapping("/api/owner/horses/{id}/photo")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    @Operation(operationId = "getOwnedHorsePhoto", summary = "Read the latest uploaded photo of an owned horse")
    public ResponseEntity<Resource> ownerPrimary(@PathVariable("id") @Positive long horseId) {
        return image(service.primaryForOwner(horseId));
    }

    @GetMapping("/api/training-sessions/{id}/horse-photo")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','TRAINER','VETERINARIAN','WORK_RIDER','GROOM')")
    @Operation(operationId = "getSessionHorsePhoto", summary = "Read the latest photo of the horse in an assigned session")
    public ResponseEntity<Resource> sessionPrimary(@PathVariable("id") @Positive long sessionId) {
        return image(service.primaryForSession(sessionId));
    }

    private static ResponseEntity<Resource> image(Optional<HorsePhotoService.PhotoContent> photo) {
        if (photo.isEmpty()) {
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        }
        var content = photo.get();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(content.type()).body(content.resource());
    }

    @PostMapping(path = "/api/horses/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(
            operationId = "uploadHorsePhoto",
            summary = "Upload a horse photo",
            description = "Accepts JPEG, PNG, or WebP image content up to 5 MiB. Invalid files are not persisted.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Photo uploaded."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid image or file too large."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Only CLUB_MANAGER may upload."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Horse not found.")
    })
    public ApiResponse<HorsePhoto> upload(
            @PathVariable("id") @Positive long horseId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.upload(horseId, file));
    }
}
