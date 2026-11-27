package com.racehorse.medical.incident;

import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.racehorse.medical.incident.IncidentPhotoService.IncidentPhoto;

@RestController
@Validated
@Profile({"dev", "test"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Incidents")
public class IncidentPhotoController {

    private final IncidentPhotoService service;

    public IncidentPhotoController(IncidentPhotoService service) {
        this.service = service;
    }

    @GetMapping("/api/incidents/{incidentId}/photos")
    @Operation(operationId = "listIncidentPhotos", summary = "List the photos of an incident, oldest first",
            description = "Same read scope as GET /api/incidents/{incidentId}.")
    public ApiResponse<List<IncidentPhoto>> list(@PathVariable("incidentId") @Positive long incidentId) {
        return ApiResponse.ok(service.list(incidentId));
    }

    @GetMapping("/api/incidents/{incidentId}/photos/{photoId}")
    @Operation(operationId = "getIncidentPhoto", summary = "Read one incident photo")
    public ResponseEntity<Resource> content(@PathVariable("incidentId") @Positive long incidentId,
                                            @PathVariable("photoId") @Positive long photoId) {
        var photo = service.content(incidentId, photoId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(photo.type()).body(photo.resource());
    }

    @PostMapping(path = "/api/incidents/{incidentId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "uploadIncidentPhoto", summary = "Attach a photo to an incident",
            description = "Reporter only, while the incident is not RESOLVED, at most 5 photos. "
                    + "JPEG, PNG or WebP content up to 5 MiB; invalid files are not stored.")
    public ApiResponse<IncidentPhoto> upload(@PathVariable("incidentId") @Positive long incidentId,
                                             @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.upload(incidentId, file));
    }
}
