package com.racehorse.horse.activation;

import com.racehorse.auth.RoleCode;
import com.racehorse.horse.photo.HorsePhotoRepository;
import com.racehorse.horse.photo.LocalHorsePhotoStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HorsePhotoApiTest extends HorseActivationTestSupport {

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private LocalHorsePhotoStorage storage;

    @MockitoSpyBean
    private HorsePhotoRepository photoRepository;

    private final List<String> storedReferences = new ArrayList<>();
    private final List<Long> trainerProgramIds = new ArrayList<>();

    @AfterEach
    void cleanStoredPhotos() {
        trainerProgramIds.forEach(programId -> {
            jdbc.update("DELETE FROM dbo.trainer_assignment WHERE horse_training_program_id = ?", programId);
            jdbc.update("DELETE FROM dbo.horse_training_program WHERE id = ?", programId);
        });
        trainerProgramIds.clear();
        storedReferences.forEach(storage::delete);
        storedReferences.clear();
    }

    @Test
    void uploadsListsAndServesValidJpegAndPngNewestFirst() throws Exception {
        long horseId = createHorse("Photo API Horse");
        long trainerId = createAccount(RoleCode.TRAINER);
        long managerId = createAccount(RoleCode.CLUB_MANAGER);

        byte[] jpeg = image("jpeg");
        byte[] png = image("png");
        JsonNode first = upload(horseId, managerId, "image/jpeg", jpeg);
        JsonNode second = upload(horseId, managerId, "image/png", png);

        mvc.perform(get("/api/horses/{id}/photos", horseId)
                        .with(authenticatedAs(RoleCode.TRAINER, trainerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(get(second.path("data").path("url").asText())
                        .with(authenticatedAs(RoleCode.TRAINER, trainerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        long programId = createActiveProgram(horseId);
        jdbc.update("""
                INSERT INTO dbo.trainer_assignment (horse_training_program_id, trainer_user_id)
                VALUES (?, ?)
                """, programId, trainerId);

        mvc.perform(get("/api/horses/{id}/photos", horseId)
                        .with(authenticatedAs(RoleCode.TRAINER, trainerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(second.path("data").path("id").asLong()))
                .andExpect(jsonPath("$.data[0].url").value(second.path("data").path("url").asText()))
                .andExpect(jsonPath("$.data[1].id").value(first.path("data").path("id").asLong()));

        mvc.perform(get(second.path("data").path("url").asText())
                        .with(authenticatedAs(RoleCode.TRAINER, trainerId)))
                .andExpect(status().isOk())
                .andExpect(result -> assertArrayEquals(png, result.getResponse().getContentAsByteArray()));

        byte[] served = mvc.perform(get(second.path("data").path("url").asText())
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(png, served);

        long ownerId = createAccount(RoleCode.HORSE_OWNER);
        mvc.perform(get("/api/horses/{id}/photos", horseId)
                        .with(authenticatedAs(RoleCode.HORSE_OWNER, ownerId)))
                .andExpect(status().isForbidden());
    }

    private long createActiveProgram(long horseId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.horse_training_program
                        (horse_id, name, status, period_start, period_end, activated_at)
                    VALUES (?, 'Photo scope test', 'ACTIVE', ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, horseId);
            statement.setObject(2, LocalDate.of(2026, 1, 1));
            statement.setObject(3, LocalDate.of(2026, 12, 31));
            statement.setObject(4, LocalDateTime.now());
            return statement;
        }, keyHolder);
        Number programId = keyHolder.getKey();
        if (programId == null) {
            throw new IllegalStateException("The database did not return a training program identifier.");
        }
        trainerProgramIds.add(programId.longValue());
        return programId.longValue();
    }

    @Test
    void rejectsInvalidMimeAndOversizedFilesWithoutPersistingMetadata() throws Exception {
        long horseId = createHorse("Invalid Photo Horse");
        long managerId = createAccount(RoleCode.CLUB_MANAGER);

        mvc.perform(multipart("/api/horses/{id}/photos", horseId)
                        .file(new MockMultipartFile("file", "not-a-photo.jpg", "image/jpeg",
                                "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mvc.perform(multipart("/api/horses/{id}/photos", horseId)
                        .file(new MockMultipartFile("file", "large.png", "image/png",
                                new byte[(int) (5L * 1024 * 1024 + 1)]))
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.horse_photo WHERE horse_id = ?", Long.class, horseId));
        if (Files.exists(storage.rootDirectory())) {
            try (var files = Files.list(storage.rootDirectory())) {
                assertFalse(files.anyMatch(Files::isRegularFile));
            }
        }
    }

    @Test
    void acceptsAWebpFileBasedOnItsContainerAndFrameBytes() throws Exception {
        long horseId = createHorse("WebP Photo Horse");
        long managerId = createAccount(RoleCode.CLUB_MANAGER);

        JsonNode uploaded = upload(horseId, managerId, "image/webp", webpImage());

        assertTrue(uploaded.path("data").path("url").asText().endsWith(".webp"));
        mvc.perform(get(uploaded.path("data").path("url").asText())
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isOk())
                .andExpect(result -> assertArrayEquals(webpImage(),
                        result.getResponse().getContentAsByteArray()));
    }

    @Test
    void requiresAuthenticationAndClubManagerForUploads() throws Exception {
        long horseId = createHorse("Photo Authorization Horse");
        long trainerId = createAccount(RoleCode.TRAINER);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", image("jpeg"));

        mvc.perform(multipart("/api/horses/{id}/photos", horseId).file(file))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/horses/{id}/photos", horseId).file(file)
                        .with(authenticatedAs(RoleCode.TRAINER, trainerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForMissingHorseAndCleansStorageWhenDatabaseInsertFails() throws Exception {
        long managerId = createAccount(RoleCode.CLUB_MANAGER);
        mvc.perform(get("/api/horses/{id}/photos", Long.MAX_VALUE)
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isNotFound());

        long horseId = createHorse("Photo Rollback Horse");
        doThrow(new IllegalStateException("simulated persistence failure"))
                .when(photoRepository).insert(anyLong(), anyString(), org.mockito.ArgumentMatchers.any(Instant.class));

        mvc.perform(multipart("/api/horses/{id}/photos", horseId)
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", image("jpeg")))
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isInternalServerError());

        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.horse_photo WHERE horse_id = ?", Long.class, horseId));
        try (var files = Files.list(storage.rootDirectory())) {
            assertFalse(files.anyMatch(Files::isRegularFile));
        }
    }

    @Test
    void publishesPhotoOperationsAndSecurityInGeneratedOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/horses/{id}/photos'].get.operationId")
                        .value("listHorsePhotos"))
                .andExpect(jsonPath("$.paths['/api/horses/{id}/photos'].post.operationId")
                        .value("uploadHorsePhoto"))
                .andExpect(jsonPath("$.paths['/api/horses/{id}/photos'].post.requestBody.content['multipart/form-data']")
                        .exists());
    }

    private JsonNode upload(long horseId, long managerId, String mediaType, byte[] content) throws Exception {
        MvcResult result = mvc.perform(multipart("/api/horses/{id}/photos", horseId)
                        .file(new MockMultipartFile("file", "client-file", mediaType, content))
                        .with(authenticatedAs(RoleCode.CLUB_MANAGER, managerId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());
        String url = response.path("data").path("url").asText();
        storedReferences.add(url.substring(url.lastIndexOf('/') + 1));
        assertTrue(url.startsWith("/local-media/horse-photos/"));
        return response;
    }

    private byte[] image(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(image, format, bytes));
        return bytes.toByteArray();
    }

    private byte[] webpImage() {
        byte[] fixture = Base64.getDecoder().decode(
                "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA==");
        return Arrays.copyOf(fixture, 42);
    }
}
