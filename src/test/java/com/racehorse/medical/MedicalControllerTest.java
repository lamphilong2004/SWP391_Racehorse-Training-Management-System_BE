package com.racehorse.medical;

import com.racehorse.common.error.GlobalExceptionHandler;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MedicalControllerTest {
    @Test void missingAuthenticationReturns401EnvelopeForEveryEndpoint() throws Exception {
        var factory = new DefaultListableBeanFactory();
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var service = new MedicalService(new MedicalAccess(factory.getBeanProvider(MedicalIdentityProvider.class)),
                    mock(MedicalHorseRepository.class), mock(VeterinaryExaminationRepository.class),
                    mock(WeightRecordRepository.class), validation.getValidator());
            var mvc = MockMvcBuilders.standaloneSetup(new MedicalController(service))
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
            for (String path : new String[]{"baseline-examinations", "weights"}) {
                mvc.perform(post("/api/horses/7/" + path).contentType("application/json")
                        .content("{\"weightKg\":450,\"healthStatus\":\"FIT\"}"))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
            }
            mvc.perform(get("/api/horses/7/weights")).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));

        }
    }

    @Test void successUses201AndEnvelope() throws Exception {
        var service = mock(MedicalService.class);
        when(service.recordWeight(eq(7L), any())).thenReturn(
                new WeightRecordResponse(12, 7, new java.math.BigDecimal("450"), java.time.Instant.EPOCH));
        var mvc = MockMvcBuilders.standaloneSetup(new MedicalController(service)).build();
        mvc.perform(post("/api/horses/7/weights").contentType("application/json").content("{\"weightKg\":450}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.weightKg").value(450));
    }


}
