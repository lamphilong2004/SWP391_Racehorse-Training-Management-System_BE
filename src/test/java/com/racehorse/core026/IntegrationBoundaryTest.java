package com.racehorse.core026;

import com.racehorse.common.error.*;
import com.racehorse.core026.Contracts.*;
import com.racehorse.core026.IntegrationPorts.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntegrationBoundaryTest {
    ValidatorFactory validation;
    Core026Store store;
    ApplicationEventPublisher events;
    DefaultListableBeanFactory beans;
    AccessPolicy access;
    Actor actor;

    @BeforeEach
    void setup() {
        validation = Validation.buildDefaultValidatorFactory();
        store = mock(Core026Store.class);
        events = mock(ApplicationEventPublisher.class);
        beans = new DefaultListableBeanFactory();
        actor = new Actor(1, Set.of(Role.GROOM));
        beans.registerSingleton("actor", (ActorResolver) () -> actor);
        access = new AccessPolicy(beans.getBeanProvider(ActorResolver.class),
                beans.getBeanProvider(GroomWorkScope.class));
    }

    @AfterEach
    void close() {
        validation.close();
    }

    PreSessionCheckService checks() {
        return new PreSessionCheckService(store, access, validation.getValidator(), new ObjectMapper(), events,
                beans.getBeanProvider(SessionProjection.class));
    }

    IncidentService incidents() {
        return new IncidentService(store, access, validation.getValidator(), events);
    }

    void scope(List<Long> ids, boolean allowed) {
        beans.registerSingleton("grooms", new GroomWorkScope() {
            public boolean mayCheck(Actor a, long id) {
                return allowed;
            }

            public boolean mayReportCareTask(Actor a, long id) {
                return allowed;
            }

            public List<Long> sessionIds(Actor a, LocalDate date) {
                return ids;
            }
        });
    }

    @Test
    void listReturnsCanonicalProjectionForAuthorizedScheduledSessionsOnly() {
        scope(List.of(1L, 1L, 2L, 3L), true);
        var summary = new SessionSummary(1, "S1", new HorseRef(10, "H1", "Horse"), 100, "Phase",
                Instant.parse("2026-10-13T09:00:00Z"), null, "SCHEDULED", new UserRef(3, "U3", "Rider"), null,
                CheckResult.ALL_NORMAL, true, List.of("AVAILABILITY_LOCKED"), "Medical block");
        SessionProjection projection = mock(SessionProjection.class);
        beans.registerSingleton("projection", projection);
        when(projection.summary(1)).thenReturn(summary);
        when(store.session(1, false))
                .thenReturn(Optional.of(new Core026Store.SessionContext(1, 10, 100, 3, "SCHEDULED")));
        when(store.session(2, false))
                .thenReturn(Optional.of(new Core026Store.SessionContext(2, 10, 100, 3, "COMPLETED")));
        when(store.session(3, false)).thenReturn(Optional.empty());
        assertEquals(List.of(summary), checks().sessions(LocalDate.of(2026, 10, 13)));
        // ALL_NORMAL must preserve the full lifecycle projection's medical block.
        assertTrue(summary.blocked());
        verify(projection, times(1)).summary(1);
        verifyNoMoreInteractions(projection);
    }

    @Test
    void unauthorizedWorkListIsRejectedBeforeLoadingSession() {
        scope(List.of(1L), false);
        beans.registerSingleton("projection", mock(SessionProjection.class));
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(BusinessException.class, () -> checks().sessions(LocalDate.of(2026, 10, 13))).code());
        verifyNoInteractions(store);
    }

    @Test
    void databaseIncidentUniquenessRaceMapsToConflictWithoutPublishingEvent() {
        when(store.horseExists(10)).thenReturn(true);
        when(store.insertIncident(any(), eq(1L), anyString(), any())).thenThrow(new DuplicateKeyException("race"));
        var request = new IncidentCreateRequest(10L, null, null, null, IncidentType.OTHER, Severity.LOW, null,
                Instant.now());
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(BusinessException.class, () -> incidents().create(request)).code());
        verifyNoInteractions(events);
    }

    @Test
    void databaseCheckUniquenessRaceMapsToConflictWithoutPublishingEvent() {
        scope(List.of(1L), true);
        when(store.session(1, true))
                .thenReturn(Optional.of(new Core026Store.SessionContext(1, 10, 100, 3, "SCHEDULED")));
        when(store.checkResult(1)).thenReturn(Optional.empty());
        when(store.user(1)).thenReturn(new UserRef(1, "U1", "Groom"));
        when(store.insertCheck(eq(1L), eq(1L), any(), anyString(), any())).thenThrow(new DuplicateKeyException("race"));
        var request = new PreSessionCheckRequest(CheckResult.ALL_NORMAL, true, true, null, null, null);
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(BusinessException.class, () -> checks().submit(1, request)).code());
        verifyNoInteractions(events);
    }

    @Test
    void multiRoleHeadTrainerDoesNotRequireGroomAdapterToReportSession() {
        actor = new Actor(1, Set.of(Role.HEAD_TRAINER, Role.GROOM));
        when(store.horseExists(10)).thenReturn(true);
        when(store.session(1, false))
                .thenReturn(Optional.of(new Core026Store.SessionContext(1, 10, 100, 3, "SCHEDULED")));
        when(store.insertIncident(any(), eq(1L), anyString(), any())).thenReturn(5L);
        var result = new Incident(5, "INC-TEST", 10, 1, null, null, 1L, null, IncidentType.OTHER, Severity.LOW, null,
                Instant.now(), Instant.now(), IncidentStatus.REPORTED);
        when(store.incident(5)).thenReturn(Optional.of(result));
        assertEquals(result, incidents().create(
                new IncidentCreateRequest(10L, null, 1L, null, IncidentType.OTHER, Severity.LOW, null, Instant.now())));
    }
}
