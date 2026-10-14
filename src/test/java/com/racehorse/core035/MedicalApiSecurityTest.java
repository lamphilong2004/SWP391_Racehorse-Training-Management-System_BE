package com.racehorse.core035;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class MedicalApiSecurityTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).build(); }

    @Test void missingAuthenticationAdapterFailsClosedWithEnvelope() throws Exception {
        mvc.perform(get("/api/medical/cases").header("X-User-Id", "10").header("X-Role", "VETERINARIAN"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
    @Test void requestCannotSupplyActorFields() throws Exception {
        mvc.perform(post("/api/medical/cases").contentType(MediaType.APPLICATION_JSON)
                .content("{\"horseId\":1,\"reason\":\"Concern\",\"openedByUserId\":10}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    @Test void invalidClinicalEnumUsesValidationEnvelope() throws Exception {
        mvc.perform(post("/api/medical/cases/1/injuries").contentType(MediaType.APPLICATION_JSON)
                .content("{\"bodyRegion\":\"Leg\",\"severity\":\"EXTREME\",\"recoveryStatus\":\"IMPROVING\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    @Test void completeRequiresEmptyBodyAndAuthentication() throws Exception {
        mvc.perform(post("/api/groom/treatment-tasks/1/complete").contentType(MediaType.APPLICATION_JSON).content("{\"completedByUserId\":9}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/groom/treatment-tasks/1/complete").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
