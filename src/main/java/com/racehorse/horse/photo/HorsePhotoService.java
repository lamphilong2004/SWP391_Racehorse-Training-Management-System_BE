package com.racehorse.horse.photo;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;

@Service
@Profile({"dev", "test"})
public class HorsePhotoService {

    public static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

    private final HorsePhotoRepository repository;
    private final LocalHorsePhotoStorage storage;

    public HorsePhotoService(HorsePhotoRepository repository, LocalHorsePhotoStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<HorsePhoto> list(long horseId) {
        requireHorse(horseId);
        return repository.findByHorseId(horseId).stream()
                .map(row -> {
                    if (!storage.exists(row.fileReference())) {
                        throw new IllegalStateException("A stored horse photo is missing from local storage.");
                    }
                    return new HorsePhoto(row.id(), storage.url(row.fileReference()), row.uploadedAt());
                })
                .toList();
    }

    @Transactional
    public HorsePhoto upload(long horseId, MultipartFile file) {
        requireHorse(horseId);
        if (file == null || file.isEmpty() || file.getSize() > MAX_PHOTO_BYTES) {
            throw invalidPhoto();
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("The uploaded horse photo could not be read.", ex);
        }

        ImageType imageType = detectImageType(bytes);
        if (imageType == null || (file.getContentType() != null
                && !file.getContentType().isBlank()
                && !file.getContentType().equalsIgnoreCase(imageType.mediaType))) {
            throw invalidPhoto();
        }

        String reference = storage.store(bytes, imageType.extension);
        try {
            Instant uploadedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            long photoId = repository.insert(horseId, reference, uploadedAt);
            registerRollbackCleanup(reference);
            return new HorsePhoto(photoId, storage.url(reference), uploadedAt);
        } catch (RuntimeException ex) {
            storage.delete(reference);
            throw ex;
        }
    }

    private void requireHorse(long horseId) {
        if (!repository.horseExists(horseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Horse not found.");
        }
    }

    private void registerRollbackCleanup(String reference) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(reference);
                }
            }
        });
    }

    private static ImageType detectImageType(byte[] bytes) {
        if (startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
                && imageIoRecognizes(bytes, "png")) {
            return new ImageType("png", "image/png");
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF && imageIoRecognizes(bytes, "jpeg")) {
            return new ImageType("jpg", "image/jpeg");
        }
        if (validWebpContainer(bytes)) {
            return new ImageType("webp", "image/webp");
        }
        return null;
    }

    private static boolean imageIoRecognizes(byte[] bytes, String expectedFormat) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (imageInput == null) {
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                return expectedFormat.equalsIgnoreCase(reader.getFormatName())
                        && reader.getWidth(0) > 0 && reader.getHeight(0) > 0;
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean validWebpContainer(byte[] bytes) {
        if (bytes.length < 20 || !ascii(bytes, 0, 4).equals("RIFF")
                || !ascii(bytes, 8, 4).equals("WEBP")) {
            return false;
        }
        long riffLength = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).getInt());
        if (riffLength != bytes.length - 8L) {
            return false;
        }

        int offset = 12;
        boolean hasImageChunk = false;
        while (offset < bytes.length) {
            if (bytes.length - offset < 8) {
                return false;
            }
            String chunkType = ascii(bytes, offset, 4);
            long chunkLength = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset + 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt());
            long chunkEnd = offset + 8L + chunkLength;
            if (chunkEnd > bytes.length || chunkLength == 0) {
                return false;
            }
            if (chunkType.equals("VP8 ")) {
                if (chunkLength < 10 || (bytes[offset + 8] & 0x01) != 0
                        || bytes[offset + 11] != (byte) 0x9D
                        || bytes[offset + 12] != 0x01 || bytes[offset + 13] != 0x2A) {
                    return false;
                }
                hasImageChunk = true;
            } else if (chunkType.equals("VP8L")) {
                if (chunkLength < 5 || bytes[offset + 8] != 0x2F) {
                    return false;
                }
                hasImageChunk = true;
            } else if (chunkType.equals("VP8X")) {
                if (chunkLength != 10 || (bytes[offset + 8] & 0xFC) != 0
                        || bytes[offset + 9] != 0 || bytes[offset + 10] != 0 || bytes[offset + 11] != 0) {
                    return false;
                }
            } else if (chunkType.equals("ANMF")) {
                hasImageChunk |= containsValidWebpFrame(bytes, offset + 24, (int) chunkEnd);
            }
            offset = (int) (chunkEnd + (chunkLength & 1));
        }
        return offset == bytes.length && hasImageChunk;
    }

    private static boolean containsValidWebpFrame(byte[] bytes, int offset, int end) {
        while (offset < end) {
            if (end - offset < 8) {
                return false;
            }
            String chunkType = ascii(bytes, offset, 4);
            long chunkLength = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset + 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt());
            long chunkEnd = offset + 8L + chunkLength;
            if (chunkEnd > end || chunkLength == 0) {
                return false;
            }
            if (chunkType.equals("VP8 ") && chunkLength >= 10
                    && (bytes[offset + 8] & 0x01) == 0
                    && bytes[offset + 11] == (byte) 0x9D
                    && bytes[offset + 12] == 0x01 && bytes[offset + 13] == 0x2A) {
                return true;
            }
            if (chunkType.equals("VP8L") && chunkLength >= 5 && bytes[offset + 8] == 0x2F) {
                return true;
            }
            offset = (int) (chunkEnd + (chunkLength & 1));
        }
        return false;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static BusinessException invalidPhoto() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Upload a valid JPEG, PNG, or WebP image no larger than 5 MiB.");
    }

    private record ImageType(String extension, String mediaType) {
    }
}
