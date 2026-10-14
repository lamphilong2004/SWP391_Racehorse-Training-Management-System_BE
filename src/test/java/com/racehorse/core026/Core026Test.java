package com.racehorse.core026;

import com.racehorse.common.error.*;
import com.racehorse.core026.Contracts.*;
import com.racehorse.core026.IntegrationPorts.*;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:core026;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Import(Core026Test.TestPorts.class)
class Core026Test {
    @Autowired
    IncidentService incidents;
    @Autowired
    PreSessionCheckService checks;
    @Autowired
    PreSessionStartGate gate;
    @Autowired
    Core026Store store;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Identities identities;
    @Autowired
    Receipts receipts;
    @Autowired
    ObjectMapper json;
    @Autowired
    WebApplicationContext context;
    @Autowired
    PlatformTransactionManager transactions;
    MockMvc mvc;
    static final Instant OBSERVED = Instant.parse("2026-10-13T09:05:06.123Z");

    @BeforeEach
    void setup() {
        new ResourceDatabasePopulator(new ClassPathResource("core026-fixture.sql"))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
        identities.actor = new Actor(1, Set.of(Role.GROOM));
        identities.workScope = true;
        receipts.events.clear();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    IncidentCreateRequest request(Long check, Long session, Long task) {
        return new IncidentCreateRequest(10L, check, session, task, IncidentType.OFF_FEED, Severity.MEDIUM, "Bỏ ăn",
                OBSERVED);
    }

    PreSessionCheckRequest check(CheckResult result) {
        return new PreSessionCheckRequest(items(result == CheckResult.ALL_NORMAL ? ItemResult.NORMAL : ItemResult.ABNORMAL, null),
                "Quan sát", null);
    }

    static CheckItems items(ItemResult general, String observation) {
        CheckItem ok = new CheckItem(ItemResult.NORMAL, observation);
        return new CheckItems(new CheckItem(general, observation), ok, ok, ok, ok, null);
    }

    void actor(long id, Role role) {
        identities.actor = new Actor(id, Set.of(role));
    }

    void error(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BusinessException.class, action).code());
    }

    @Test
    void independentIncidentPersistsUtcActorCodeAndReportedState() {
        Incident created = incidents.create(request(null, null, null));
        assertEquals(created, incidents.detail(created.id()));
        assertEquals(1, created.reportedByUserId());
        assertEquals(OBSERVED, created.observedAt());
        assertEquals(IncidentStatus.REPORTED, created.status());
        assertTrue(created.incidentCode().matches("INC-[A-F0-9]{16}"));
        assertNotNull(created.reportedAt());
        assertEquals("Bỏ ăn", created.description());
        assertEquals(1, receipts.events.size()); // after-commit integration event, not delivery
    }

    @ParameterizedTest
    @EnumSource(IncidentType.class)
    void allCanonicalTypesPersist(IncidentType type) {
        var base = request(null, null, null);
        assertEquals(type,
                incidents.create(
                        new IncidentCreateRequest(10L, null, null, null, type, base.observedSeverity(), null, OBSERVED))
                        .incidentType());
    }

    @ParameterizedTest
    @EnumSource(Severity.class)
    void allCanonicalSeveritiesPersist(Severity severity) {
        assertEquals(severity,
                incidents.create(
                        new IncidentCreateRequest(10L, null, null, null, IncidentType.OTHER, severity, null, OBSERVED))
                        .observedSeverity());
    }

    @Test
    void eachOptionalContextPersistsAndPrecheckIsUnique() {
        long checkId = checks.submit(10000, check(CheckResult.ALL_NORMAL)).id();
        assertEquals(checkId, incidents.create(request(checkId, null, null)).preSessionCheckId());
        assertEquals(10000L, incidents.create(request(null, 10000L, null)).trainingSessionId());
        assertEquals(30L, incidents.create(request(null, null, 30L)).careTaskId());
        error(ErrorCode.CONFLICT, () -> incidents.create(request(checkId, null, null)));
        assertEquals(3, incidents.list(0, 20).totalElements());
    }

    @Test
    void allMultipleSourceCombinationsAreRejected() {
        for (var request : List.of(request(1L, 10000L, null), request(1L, null, 30L), request(null, 10000L, 30L),
                request(1L, 10000L, 30L)))
            error(ErrorCode.VALIDATION_ERROR, () -> incidents.create(request));
        assertEquals(0, incidents.list(0, 20).totalElements());
    }

    @Test
    void missingReferencesAndWrongHorseAreRejected() {
        error(ErrorCode.NOT_FOUND, () -> incidents.create(
                new IncidentCreateRequest(999L, null, null, null, IncidentType.OTHER, Severity.LOW, null, OBSERVED)));
        error(ErrorCode.NOT_FOUND, () -> incidents.create(request(999L, null, null)));
        error(ErrorCode.NOT_FOUND, () -> incidents.create(request(null, 999L, null)));
        error(ErrorCode.NOT_FOUND, () -> incidents.create(request(null, null, 999L)));
        error(ErrorCode.VALIDATION_ERROR, () -> incidents.create(request(null, 20000L, null)));
        error(ErrorCode.VALIDATION_ERROR, () -> incidents.create(request(null, null, 40L)));
        long foreign = checks.submit(20000, check(CheckResult.ALL_NORMAL)).id();
        error(ErrorCode.VALIDATION_ERROR, () -> incidents.create(request(foreign, null, null)));
    }

    @Test
    void invalidBodyFieldsAreValidatedAtServiceBoundary() {
        assertThrows(ConstraintViolationException.class, () -> incidents
                .create(new IncidentCreateRequest(-1L, null, null, null, null, null, "x".repeat(2001), null)));
        assertThrows(ConstraintViolationException.class, () -> incidents.create(request(-1L, null, null)));
        assertThrows(ConstraintViolationException.class, () -> checks.submit(10000,
                new PreSessionCheckRequest(null, null, null)));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = { "CLUB_MANAGER", "VETERINARIAN", "HORSE_OWNER" })
    void disallowedRolesCannotCreate(Role role) {
        actor(4, role);
        error(ErrorCode.FORBIDDEN, () -> incidents.create(request(null, null, null)));
    }

    @Test
    void trainerAssignmentAndRiderInSessionScopeAreEnforced() {
        actor(2, Role.TRAINER);
        assertNotNull(incidents.create(request(null, 10000L, null)));
        error(ErrorCode.FORBIDDEN, () -> incidents.create(
                new IncidentCreateRequest(20L, null, null, null, IncidentType.OTHER, Severity.HIGH, null, OBSERVED)));
        actor(3, Role.WORK_RIDER);
        error(ErrorCode.FORBIDDEN, () -> incidents.create(request(null, null, null)));
        error(ErrorCode.FORBIDDEN, () -> incidents.create(request(null, 10000L, null)));
        assertNotNull(incidents.create(request(null, 10001L, null)));
        jdbc.update("UPDATE dbo.training_session SET work_rider_user_id=2 WHERE id=10001");
        error(ErrorCode.FORBIDDEN, () -> incidents.create(request(null, 10001L, null)));
    }

    @Test
    void groomContextScopeCannotBeBypassed() {
        identities.workScope = false;
        error(ErrorCode.FORBIDDEN, () -> checks.submit(10000, check(CheckResult.ALL_NORMAL)));
        error(ErrorCode.FORBIDDEN, () -> incidents.create(request(null, 10000L, null)));
        error(ErrorCode.FORBIDDEN, () -> incidents.create(request(null, null, 30L)));
        assertNotNull(incidents.create(request(null, null, null)));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = { "TRAINER", "WORK_RIDER", "GROOM" })
    void reportReadsAndCountsAreScopedBeforePagination(Role role) {
        Incident other = incidents.create(request(null, null, null));
        actor(4, Role.HEAD_TRAINER);
        Incident own = incidents.create(request(null, null, null));
        actor(4, role);
        var page = incidents.list(0, 1);
        assertEquals(1, page.totalElements());
        assertEquals(own.id(), page.items().getFirst().id());
        assertTrue(incidents.list(1, 1).items().isEmpty());
        error(ErrorCode.FORBIDDEN, () -> incidents.detail(other.id()));
        assertEquals(own, incidents.detail(own.id()));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = { "CLUB_MANAGER", "HEAD_TRAINER", "VETERINARIAN" })
    void globalReadRolesCanReadReporterProjection(Role role) {
        var created = incidents.create(request(null, null, null));
        actor(4, role);
        assertEquals(1, incidents.list(0, 20).totalElements());
        assertEquals(created, incidents.detail(created.id()));
        String body = json.writeValueAsString(created);
        assertFalse(body.contains("diagnosis"));
        assertFalse(body.contains("prescription"));
    }

    @Test
    void ownerCannotReadAndUnauthenticatedActorsAreRejected() {
        actor(4, Role.HORSE_OWNER);
        error(ErrorCode.FORBIDDEN, () -> incidents.list(0, 20));
        error(ErrorCode.FORBIDDEN, () -> incidents.detail(1));
        identities.actor = null;
        error(ErrorCode.UNAUTHORIZED, () -> incidents.list(0, 20));
        error(ErrorCode.UNAUTHORIZED, () -> checks.submit(10000, check(CheckResult.ALL_NORMAL)));
    }

    @Test
    void paginationAndIdsAreValidated() {
        error(ErrorCode.VALIDATION_ERROR, () -> incidents.list(-1, 20));
        error(ErrorCode.VALIDATION_ERROR, () -> incidents.list(0, 101));
        error(ErrorCode.VALIDATION_ERROR, () -> incidents.list(0, 0));
        error(ErrorCode.VALIDATION_ERROR, () -> incidents.detail(0));
        error(ErrorCode.NOT_FOUND, () -> incidents.detail(999));
    }

    @Test
    void checkPersistsStructuredObservationsAndPerformer() {
        var result = checks.submit(10000, check(CheckResult.ALL_NORMAL));
        assertEquals(10000, result.sessionId());
        assertEquals(1, result.performedBy().id());
        assertEquals("Groom One", result.performedBy().fullName());
        var observed = json.readTree(result.observations());
        assertEquals("NORMAL", observed.get("items").get("generalCondition").get("result").asString());
        assertEquals("Quan sát", observed.get("notes").asString());
        assertEquals(CheckResult.ALL_NORMAL, store.checkResult(10000).orElseThrow());
        assertEquals(1, receipts.events.size());
    }

    @Test
    void missingAndAbnormalBlockNormalPassesOnlyThisGate() {
        assertEquals(PreSessionStartGate.Outcome.PRE_CHECK_MISSING, gate.evaluate(10000));
        error(ErrorCode.CONFLICT, () -> gate.requirePass(10000));
        checks.submit(10000, check(CheckResult.ABNORMAL));
        assertEquals(PreSessionStartGate.Outcome.PRE_CHECK_ABNORMAL, gate.evaluate(10000));
        error(ErrorCode.CONFLICT, () -> gate.requirePass(10000));
        checks.submit(20000, check(CheckResult.ALL_NORMAL));
        assertEquals(PreSessionStartGate.Outcome.PASS, gate.evaluate(20000));
        gate.requirePass(20000);
        assertEquals("SCHEDULED",
                jdbc.queryForObject("SELECT status FROM dbo.training_session WHERE id=20000", String.class));
        error(ErrorCode.NOT_FOUND, () -> gate.evaluate(999));
    }

    @Test
    void abnormalDoesNotCreateIncidentOrPretendDelivery() {
        var result = checks.submit(10000, check(CheckResult.ABNORMAL));
        assertNull(result.incidentId());
        assertEquals(0, incidents.list(0, 20).totalElements());
        assertInstanceOf(CheckRecorded.class, receipts.events.getFirst());
    }

    @Test
    void nestedIncidentIsRejectedPendingDecisionAndChecksAreConsistent() {
        error(ErrorCode.VALIDATION_ERROR, () -> checks.submit(10000, new PreSessionCheckRequest(
                items(ItemResult.ABNORMAL, null), null, new IncidentInput(IncidentType.OTHER, Severity.HIGH, null))));
        assertTrue(store.checkResult(10000).isEmpty());
    }

    @Test
    void duplicateAndInvalidSessionStatesConflict() {
        checks.submit(10000, check(CheckResult.ALL_NORMAL));
        error(ErrorCode.CONFLICT, () -> checks.submit(10000, check(CheckResult.ABNORMAL)));
        for (String state : List.of("IN_PROGRESS", "PAUSED", "COMPLETED", "INTERRUPTED")) {
            jdbc.update("UPDATE dbo.training_session SET status=? WHERE id=20000", state);
            error(ErrorCode.CONFLICT, () -> checks.submit(20000, check(CheckResult.ALL_NORMAL)));
        }
        error(ErrorCode.NOT_FOUND, () -> checks.submit(999, check(CheckResult.ALL_NORMAL)));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = { "GROOM" }, mode = EnumSource.Mode.EXCLUDE)
    void onlyGroomMaySubmit(Role role) {
        actor(4, role);
        error(ErrorCode.FORBIDDEN, () -> checks.submit(10000, check(CheckResult.ALL_NORMAL)));
    }

    @Test
    void serializedSizeIsCheckedAfterEscaping() {
        error(ErrorCode.VALIDATION_ERROR, () -> checks.submit(10000,
                new PreSessionCheckRequest(items(ItemResult.NORMAL, "\n".repeat(500)), "\n".repeat(1000), null)));
        assertTrue(store.checkResult(10000).isEmpty());
    }

    @Test
    void rollbackEmitsNoAfterCommitNotificationEvent() {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            incidents.create(request(null, null, null));
            checks.submit(10000, check(CheckResult.ABNORMAL));
            status.setRollbackOnly();
        });
        assertEquals(0, incidents.list(0, 20).totalElements());
        assertTrue(store.checkResult(10000).isEmpty());
        assertTrue(receipts.events.isEmpty());
    }

    @Test
    void httpEndpointsUseSharedEnvelopeAndValidateEnums() throws Exception {
        mvc.perform(post("/api/incidents").contentType("application/json")
                .content(json.writeValueAsString(request(null, null, null)))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reportedByUserId").value(1))
                .andExpect(jsonPath("$.data.status").value("REPORTED"));
        mvc.perform(get("/api/incidents")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(post("/api/incidents").contentType("application/json").content(
                "{\"horseId\":10,\"incidentType\":\"WRONG\",\"observedSeverity\":\"HIGH\",\"observedAt\":\"2026-10-13T09:05:06Z\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/training-sessions/10000/pre-session-check").contentType("application/json")
                .content(json.writeValueAsString(check(CheckResult.ALL_NORMAL)))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.performedBy.id").value(1));
        mvc.perform(post("/api/training-sessions/10000/pre-session-check").contentType("application/json")
                .content(json.writeValueAsString(check(CheckResult.ALL_NORMAL)))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        identities.actor = null;
        mvc.perform(get("/api/incidents").header("X-Role", "HEAD_TRAINER").header("X-User-Id", "4"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @org.junit.jupiter.api.Disabled("DEMO INTEGRATION (demo/main-flow-hour1): pre-integration boundary test — the real CORE-007 adapters now exist and the partial H2 fixture lacks the integrated tables. Owner (Đức) to rework after the demo.")
    @Test
    void listRequiresFullLifecycleProjectionAndTrustedWorkList() {
        error(ErrorCode.FORBIDDEN, () -> checks.sessions(LocalDate.now(ZoneOffset.UTC)));
    }

    @Test
    void clientCannotSetActorOrIncidentStatus() throws Exception {
        String base = json.writeValueAsString(request(null, null, null));
        for (String extra : List.of("\"reportedByUserId\":4", "\"status\":\"RESOLVED\"", "\"status\":\"INVALID\"")) {
            mvc.perform(post("/api/incidents").contentType("application/json")
                    .content(base.substring(0, base.length() - 1) + "," + extra + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        assertEquals(0, incidents.list(0, 20).totalElements());
    }

    static class Identities implements ActorResolver, GroomWorkScope {
        Actor actor;
        boolean workScope;

        public Actor currentActor() {
            return actor;
        }

        public boolean mayCheck(Actor a, long id) {
            return workScope;
        }

        public boolean mayReportCareTask(Actor a, long id) {
            return workScope;
        }

        public List<Long> sessionIds(Actor a, LocalDate date) {
            return List.of(10000L);
        }
    }

    static class Receipts {
        List<Object> events = new ArrayList<>();

        @TransactionalEventListener
        public void incident(IncidentReported e) {
            events.add(e);
        }

        @TransactionalEventListener
        public void check(CheckRecorded e) {
            events.add(e);
        }
    }

    @TestConfiguration
    static class TestPorts {
        @Bean @org.springframework.context.annotation.Primary // DEMO INTEGRATION: wins over the production adapters
        Identities identities() {
            return new Identities();
        }

        @Bean
        Receipts receipts() {
            return new Receipts();
        }

        // Test-only override: H2 cannot prove SQL Server's UPDLOCK/HOLDLOCK semantics.
        @Bean
        @Primary
        Core026Store h2Store(JdbcTemplate jdbc) {
            return new Core026Store(jdbc) {
                @Override
                public Optional<SessionContext> session(long id, boolean lock) {
                    return super.session(id, false);
                }
            };
        }
    }
}
