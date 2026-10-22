package com.racehorse.registration;

import com.racehorse.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationControllerTest {
    RegistrationService service;MockMvc mvc;
    @BeforeEach void setup(){
        service=mock(RegistrationService.class);
        when(service.register(any(),anyString())).thenReturn(new RegistrationDtos.NeutralMessage("If the details can be accepted, your request is waiting for Club Manager approval."));
        mvc=MockMvcBuilders.standaloneSetup(new RegistrationController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void registerIgnoresSelfAssignedRoleAndReturnsOnlyNeutral202()throws Exception{
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"fullName":"Applicant","email":"  Test@Example.test  ","password":"Password123","confirmPassword":"Password123","role":"CLUB_MANAGER","roles":["CLUB_MANAGER"]}
            """)).andExpect(status().isAccepted()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.message").exists()).andExpect(jsonPath("$.data.password").doesNotExist())
            .andExpect(jsonPath("$.data.roles").doesNotExist());
        verify(service).register(argThat(r->r.email.equals("test@example.test")),anyString());
    }
    @Test void invalidEmailAndMissingFieldsNeverReachPersistence()throws Exception{
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
}
