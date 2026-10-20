package com.racehorse.medical.restriction;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.common.error.GlobalExceptionHandler;
import com.racehorse.medical.restriction.api.MedicalRestrictionCreateRequest;
import com.racehorse.medical.restriction.api.MedicalRestrictionController;
import com.racehorse.medical.restriction.api.MedicalRestrictionResponse;
import com.racehorse.medical.restriction.api.TrainingAvailabilityResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MedicalRestrictionControllerTest {

    @Mock
    private MedicalRestrictionService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MedicalRestrictionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createReturnsCommonEnvelopeAndCreatedStatus() throws Exception {
        when(service.create(eq(1L), any(MedicalRestrictionCreateRequest.class))).thenReturn(
                new MedicalRestrictionResponse(7, 1, RestrictionLevel.LOCKED, "Operational rest",
                        Instant.parse("2026-10-16T10:00:00Z"), null, null, null, List.of()));

        mvc.perform(post("/api/horses/1/medical-restrictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level":"LOCKED","reason":"Operational rest","effectiveAt":"2026-10-16T10:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.level").value("LOCKED"))
                .andExpect(jsonPath("$.data.setByUserId").doesNotExist());
    }

    @Test
    void onlyTheContractedTrainingAvailabilityRouteIsExposed() throws Exception {
        when(service.availability(1)).thenReturn(
                new TrainingAvailabilityResponse(1, TrainingAvailability.AVAILABLE, null, null, List.of()));

        mvc.perform(get("/api/horses/1/training-availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability").value("AVAILABLE"));

        mvc.perform(get("/api/horses/1/availability"))
                .andExpect(status().isNotFound());
        verify(service).availability(1);
    }

    @Test
    void batchAvailabilityRouteIsNotExposed() throws Exception {
        mvc.perform(post("/api/horses/availability/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horseIds\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void liftAcceptsOnlyTheContractedEmptyRequestBody() throws Exception {
        when(service.lift(7)).thenReturn(
                new TrainingAvailabilityResponse(1, TrainingAvailability.AVAILABLE, null, null, List.of()));

        mvc.perform(post("/api/medical/restrictions/7/lift")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability").value("AVAILABLE"));
    }

    @Test
    void serviceAuthorizationErrorsUseCommonForbiddenEnvelope() throws Exception {
        when(service.availability(1)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "Outside read scope."));

        mvc.perform(get("/api/horses/1/training-availability"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
