package com.racehorse.medical.incident;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores incident photos on the local disk under a random UUID name. A file is only read back through
 * {@code GET /api/incidents/{id}/photos/{photoId}}, which checks the incident's read scope first.
 */
@Component
@Profile({"dev", "test"})
public class IncidentPhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(IncidentPhotoStorage.class);
    private static final String FILE_REFERENCE_PATTERN = "[0-9a-fA-F-]{36}\\.(jpg|png|webp)";

    private final Path root;

    public IncidentPhotoStorage(IncidentPhotoProperties properties) {
        this.root = properties.getDirectory().toAbsolutePath().normalize();
    }

    public String store(byte[] bytes, String extension) {
        String fileReference = UUID.randomUUID() + "." + extension;
        Path temporary = null;
        try {
            Files.createDirectories(root);
            temporary = Files.createTempFile(root, ".incident-photo-", ".tmp");
            Files.write(temporary, bytes);
            Path destination = resolve(fileReference);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination);
            }
            return fileReference;
        } catch (IOException ex) {
            throw new IllegalStateException("The incident photo could not be stored.", ex);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ex) {
                    log.error("Could not remove temporary incident photo file {}", temporary.getFileName(), ex);
                }
            }
        }
    }

    public FileSystemResource resource(String fileReference) {
        Path file = resolve(fileReference);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("A stored incident photo is missing from local storage.");
        }
        return new FileSystemResource(file);
    }

    public void delete(String fileReference) {
        try {
            Files.deleteIfExists(resolve(fileReference));
        } catch (IOException ex) {
            log.error("Could not remove incident photo storage reference {}", fileReference, ex);
        }
    }

    private Path resolve(String fileReference) {
        if (fileReference == null || !fileReference.matches(FILE_REFERENCE_PATTERN)) {
            throw new IllegalArgumentException("Invalid local incident photo reference.");
        }
        Path file = root.resolve(fileReference).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Invalid local incident photo reference.");
        }
        return file;
    }
}
