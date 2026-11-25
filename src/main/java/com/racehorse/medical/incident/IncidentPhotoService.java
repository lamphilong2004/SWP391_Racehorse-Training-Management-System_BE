package com.racehorse.medical.incident;

import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.horse.photo.HorsePhotoService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Flow 3 step 3.1: the reporter attaches scene photos to an incident report.
 * Read scope is exactly the incident's scope ({@link Core026Service#get}): the reporter, or Vet / Club Manager /
 * Head Trainer. Only the reporter may add photos, while the incident is not RESOLVED, up to {@value #MAX_PHOTOS}.
 * Photos are evidence, so there is no delete.
 */
@Service
@Profile({"dev", "test"})
public class IncidentPhotoService {

    public static final int MAX_PHOTOS = 5;

    private final IncidentPhotoRepository repository;
    private final IncidentPhotoStorage storage;
    private final Core026Service incidents;
    private final CurrentActorProvider actors;

    public IncidentPhotoService(IncidentPhotoRepository repository, IncidentPhotoStorage storage,
                                Core026Service incidents, CurrentActorProvider actors) {
        this.repository = repository;
        this.storage = storage;
        this.incidents = incidents;
        this.actors = actors;
    }

    public record IncidentPhoto(long id, OffsetDateTime uploadedAt) {
    }

    public record PhotoContent(Resource resource, MediaType type) {
    }

    @Transactional(readOnly = true)
    public List<IncidentPhoto> list(long incidentId) {
        incidents.get(incidentId);
        return repository.findByIncident(incidentId).stream()
                .map(row -> new IncidentPhoto(row.id(), OffsetDateTime.ofInstant(row.uploadedAt(), ZoneOffset.UTC)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PhotoContent content(long incidentId, long photoId) {
        incidents.get(incidentId);
        String reference = repository.findReference(incidentId, photoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Incident photo was not found."));
        MediaType type = reference.endsWith(".jpg") ? MediaType.IMAGE_JPEG
                : reference.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.parseMediaType("image/webp");
        return new PhotoContent(storage.resource(reference), type);
    }

    // SERIALIZABLE: two parallel uploads cannot both pass the 5-photo count.
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public IncidentPhoto upload(long incidentId, MultipartFile file) {
        IncidentApi.Incident incident = incidents.get(incidentId);
        long actorId = actors.currentActor().orElseThrow(
                () -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required.")).userId();
        if (incident.reportedByUserId() != actorId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the reporter can attach photos to this incident.");
        }
        if ("RESOLVED".equals(incident.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Photos cannot be added to a resolved incident.");
        }
        if (repository.count(incidentId) >= MAX_PHOTOS) {
            throw new BusinessException(ErrorCode.CONFLICT, "An incident can have at most " + MAX_PHOTOS + " photos.");
        }
        if (file == null || file.isEmpty() || file.getSize() > HorsePhotoService.MAX_PHOTO_BYTES) {
            throw invalidPhoto();
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("The uploaded incident photo could not be read.", ex);
        }
        HorsePhotoService.ImageType type = HorsePhotoService.detectImageType(bytes);
        if (type == null || (file.getContentType() != null && !file.getContentType().isBlank()
                && !file.getContentType().equalsIgnoreCase(type.mediaType()))) {
            throw invalidPhoto();
        }

        String reference = storage.store(bytes, type.extension());
        try {
            Instant uploadedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            long id = repository.insert(incidentId, reference, uploadedAt);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.delete(reference);
                    }
                }
            });
            return new IncidentPhoto(id, OffsetDateTime.ofInstant(uploadedAt, ZoneOffset.UTC));
        } catch (RuntimeException ex) {
            storage.delete(reference);
            throw ex;
        }
    }

    private static BusinessException invalidPhoto() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Upload a valid JPEG, PNG, or WebP image no larger than 5 MiB.");
    }
}
