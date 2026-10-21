package com.racehorse.horse.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@Profile({"dev", "test"})
public class LocalHorsePhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalHorsePhotoStorage.class);
    private static final String FILE_REFERENCE_PATTERN = "[0-9a-fA-F-]{36}\\.(jpg|png|webp)";

    private final Path root;
    private final LocalHorsePhotoProperties properties;

    public LocalHorsePhotoStorage(LocalHorsePhotoProperties properties) {
        this.root = properties.getDirectory().toAbsolutePath().normalize();
        this.properties = properties;
        properties.publicPath();
    }

    public String store(byte[] bytes, String extension) {
        String fileReference = UUID.randomUUID() + "." + extension;
        Path temporary = null;
        try {
            Files.createDirectories(root);
            temporary = Files.createTempFile(root, ".horse-photo-", ".tmp");
            Files.write(temporary, bytes);
            Path destination = resolve(fileReference);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination);
            }
            return fileReference;
        } catch (IOException ex) {
            throw new IllegalStateException("The horse photo could not be stored.", ex);
        } finally {
            if (temporary != null) {
                deleteTemporary(temporary);
            }
        }
    }

    public boolean exists(String fileReference) {
        return Files.isRegularFile(resolve(fileReference));
    }

    public void delete(String fileReference) {
        try {
            Files.deleteIfExists(resolve(fileReference));
        } catch (IOException ex) {
            log.error("Could not remove horse photo storage reference {}", fileReference, ex);
        }
    }

    public java.net.URI url(String fileReference) {
        return properties.photoUrl(fileReference);
    }

    public String resourceHandlerPattern() {
        return properties.resourceHandlerPattern();
    }

    public Path rootDirectory() {
        return root;
    }

    private Path resolve(String fileReference) {
        if (fileReference == null || !fileReference.matches(FILE_REFERENCE_PATTERN)) {
            throw new IllegalArgumentException("Invalid local horse photo reference.");
        }
        Path file = root.resolve(fileReference).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Invalid local horse photo reference.");
        }
        return file;
    }

    private void deleteTemporary(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ex) {
            log.error("Could not remove temporary horse photo file {}", temporary.getFileName(), ex);
        }
    }
}
