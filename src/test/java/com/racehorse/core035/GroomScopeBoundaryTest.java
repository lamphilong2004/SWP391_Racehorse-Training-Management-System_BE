package com.racehorse.core035;

import com.racehorse.common.error.*;
import com.racehorse.core035.MedicalPorts.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(GroomScopeBoundaryTest.ActorOnly.class)
class GroomScopeBoundaryTest {
    @TestConfiguration static class ActorOnly {
        @Bean ActorResolver actorResolver() { return () -> new Actor(30, Set.of(Role.GROOM)); }
    }
    @Autowired MedicalService service;
    @Test void missingAssignmentAdapterDeniesGroomListAndCompletion() {
        BusinessException list = assertThrows(BusinessException.class, () -> service.groomTasks(0, 20));
        BusinessException complete = assertThrows(BusinessException.class, () -> service.complete(1));
        assertEquals(ErrorCode.FORBIDDEN, list.code());
        assertEquals(ErrorCode.FORBIDDEN, complete.code());
    }
}
