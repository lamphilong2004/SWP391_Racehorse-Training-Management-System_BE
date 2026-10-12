package com.racehorse.registration;

import com.racehorse.audit.AuditLog;
import com.racehorse.audit.AuditLogRepository;
import com.racehorse.auth.AuthenticatedUser;
import com.racehorse.identity.AccountStatus;
import com.racehorse.identity.AppUser;
import com.racehorse.identity.AppUserRepository;
import com.racehorse.identity.Role;
import com.racehorse.identity.RoleCode;
import com.racehorse.identity.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CORE-047: DEC-008 §6 R1–R12 through the real HTTP API (H2, test profile). */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RegistrationApiTest {

    static final String PASSWORD = "Gallop2026x";
    static final String NEUTRAL = "{\"success\":true,\"data\":{\"message\":\"" + RegistrationService.NEUTRAL_MESSAGE + "\"}}";
    static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired WebApplicationContext context;
    @Autowired RegistrationRequestRepository requests;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired AuditLogRepository auditLogs;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationService service;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (RoleCode code : RoleCode.values()) {
            roles.findByRoleCode(code).orElseGet(() -> roles.save(new Role(code, code.name())));
        }
    }

    // ---------- helpers ----------

    static String email(String local) {
        return local + "." + UUID.randomUUID().toString().substring(0, 8) + "@club.test";
    }

    static String freshIp() {
        return "192.168.7." + IP.getAndIncrement();
    }

    ResultActions register(String body, String ip) throws Exception {
        return mvc.perform(post("/api/auth/register").with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    ResultActions register(String fullName, String email, String password, String confirm, String ip) throws Exception {
        return register("{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"confirmPassword\":\"" + confirm + "\"}", ip);
    }

    String registerOk(String email) throws Exception {
        return register("New Applicant", email, PASSWORD, PASSWORD, freshIp())
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
    }

    ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr(freshIp()); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    AppUser staff(RoleCode code) {
        AppUser user = new AppUser("S-" + UUID.randomUUID().toString().substring(0, 6), "Staff " + code,
                email(code.name().toLowerCase()), encoder.encode(PASSWORD), AccountStatus.ACTIVE);
        user.addRole(roles.findByRoleCode(code).orElseThrow(), null);
        return users.save(user);
    }

    String tokenFor(AppUser user) throws Exception {
        String body = login(user.getEmail(), PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(body);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    RegistrationRequest pending(String email) {
        return requests.findFirstByEmailNormalizedAndStatus(email.toLowerCase(), RegistrationStatus.PENDING_APPROVAL).orElseThrow();
    }

    long rowsFor(String email) {
        return requests.findAll().stream().filter(r -> r.getEmailNormalized().equals(email.toLowerCase())).count();
    }

    ResultActions approve(String token, Long id, String roleCode) throws Exception {
        return mvc.perform(post("/api/admin/registrations/" + id + "/approve").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCode\":\"" + roleCode + "\"}"));
    }

    ResultActions reject(String token, Long id, String reason) throws Exception {
        return mvc.perform(post("/api/admin/registrations/" + id + "/reject").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}"));
    }

    // ---------- R2 / R7 / R8: submission ----------

    @Test
    void newRequestIsPendingWithABcryptHashAndCreatesNoAccount() throws Exception {
        String email = email("applicant");
        assertThat(registerOk(email)).isEqualTo(NEUTRAL);

        RegistrationRequest row = pending(email);
        assertThat(row.getPasswordHash()).startsWith("$2a$10$").doesNotContain(PASSWORD);
        assertThat(encoder.matches(PASSWORD, row.getPasswordHash())).isTrue();
        assertThat(row.getFullName()).isEqualTo("New Applicant");
        assertThat(users.existsByEmailIgnoreCase(email)).isFalse();
    }

    @Test
    void roleFieldsInThePayloadAreIgnoredAndNeverPersisted() throws Exception {
        String email = email("sneaky");
        register("{\"fullName\":\"Sneaky\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"confirmPassword\":\""
                + PASSWORD + "\",\"role\":\"CLUB_MANAGER\",\"roles\":[\"CLUB_MANAGER\"],\"roleCode\":\"CLUB_MANAGER\",\"authority\":\"ROLE_CLUB_MANAGER\"}",
                freshIp())
                .andExpect(status().isAccepted());
        RegistrationRequest row = pending(email);
        assertThat(row.getApprovedRole()).isNull();
        assertThat(jdbc.queryForObject("SELECT approved_role_id FROM registration_request WHERE id = ?", Long.class, row.getId())).isNull();
        login(email, PASSWORD).andExpect(status().isForbidden()); // still only an access request, no token
    }

    @Test
    void passwordRulesAreEnforcedServerSideWithoutEchoingTheValue() throws Exception {
        String email = email("weak");
        String ip = freshIp();
        for (String[] bad : new String[][]{
                {"short1", "short1"},                            // < 8
                {"onlyletters", "onlyletters"},                  // no digit
                {"1234567890", "1234567890"},                    // no letter
                {"Đ".repeat(37) + "1", "Đ".repeat(37) + "1"},    // 38 chars but 75 bytes > 72
                {email, email},                                  // equal to the email (also has letters/digits?)
                {PASSWORD, PASSWORD + "x"}}) {                   // confirmation mismatch
            String body = register("Weak", email, bad[0], bad[1], ip).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(body).doesNotContain(bad[0]);
        }
        assertThat(rowsFor(email)).isZero();
    }

    @Test
    void registrationIsLimitedPerIpAndPerEmailWithAGeneric429() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 5; i++) {
            register("Applicant", email("burst"), PASSWORD, PASSWORD, ip).andExpect(status().isAccepted());
        }
        register("Applicant", email("burst"), PASSWORD, PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.message").value("Too many attempts. Try again later."));

        String sameEmail = email("repeat");
        for (int i = 0; i < 3; i++) {
            register("Applicant", sameEmail, PASSWORD, PASSWORD, freshIp()).andExpect(status().isAccepted());
        }
        register("Applicant", sameEmail, PASSWORD, PASSWORD, freshIp()).andExpect(status().isTooManyRequests());
    }

    // ---------- R1: duplicate-email policy ----------

    @Test
    void everyDuplicateCaseCreatesNoRowAndReturnsTheSameNeutral202() throws Exception {
        AppUser active = staff(RoleCode.GROOM);
        AppUser inactive = staff(RoleCode.TRAINER);
        inactive.setAccountStatus(AccountStatus.INACTIVE);
        users.save(inactive);
        assertThat(registerOk(active.getEmail().toUpperCase())).isEqualTo(NEUTRAL);
        assertThat(registerOk(inactive.getEmail())).isEqualTo(NEUTRAL);
        assertThat(rowsFor(active.getEmail()) + rowsFor(inactive.getEmail())).isZero();

        String waiting = email("waiting");
        registerOk(waiting);
        assertThat(registerOk(waiting.toUpperCase())).isEqualTo(NEUTRAL); // normalised (case) duplicate
        assertThat(rowsFor(waiting)).isEqualTo(1);
    }

    @Test
    void afterARejectionTheCooldownAndTheThreeRejectionLimitApply() throws Exception {
        String cm = tokenFor(staff(RoleCode.CLUB_MANAGER));
        String email = email("retry");

        registerOk(email);
        reject(cm, pending(email).getId(), null).andExpect(status().isOk());
        assertThat(registerOk(email)).isEqualTo(NEUTRAL);                 // < 7 days: no row
        assertThat(rowsFor(email)).isEqualTo(1);

        jdbc.update("UPDATE registration_request SET reviewed_at = ? WHERE email_normalized = ?",
                java.sql.Timestamp.from(Instant.now().minus(8, ChronoUnit.DAYS)), email);
        registerOk(email);                                                  // ≥ 7 days and 1 rejection: allowed
        assertThat(rowsFor(email)).isEqualTo(2);

        // Three old rejections (set up directly: the R8 per-email limit allows only 3 submissions per 24 h).
        AppUser reviewer = staff(RoleCode.CLUB_MANAGER);
        String blocked = email("thrice");
        for (int i = 0; i < 3; i++) {
            jdbc.update("INSERT INTO registration_request (full_name, email, email_normalized, password_hash, status, requested_at, "
                            + "reviewed_by_user_id, reviewed_at, created_at) VALUES (?, ?, ?, NULL, 'REJECTED', ?, ?, ?, ?)",
                    "Applicant", blocked, blocked, java.sql.Timestamp.from(Instant.now().minus(60 - i, ChronoUnit.DAYS)),
                    reviewer.getId(), java.sql.Timestamp.from(Instant.now().minus(59 - i, ChronoUnit.DAYS)),
                    java.sql.Timestamp.from(Instant.now()));
        }
        assertThat(registerOk(blocked)).isEqualTo(NEUTRAL);                // ≥ 3 rejections: never again
        assertThat(rowsFor(blocked)).isEqualTo(3);
    }

    // ---------- R3: approval ----------

    @Test
    void approvalCreatesAnActiveUserWithTheCopiedHashAndClearsTheRequestHash() throws Exception {
        AppUser cmUser = staff(RoleCode.CLUB_MANAGER);
        String cm = tokenFor(cmUser);
        String email = email("newtrainer");
        registerOk(email);
        RegistrationRequest req = pending(email);
        String originalHash = req.getPasswordHash();

        login(email, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("Your account is waiting for Club Manager approval."));
        login(email, "Wrong-pass-9").andExpect(status().isUnauthorized());

        approve(cm, req.getId(), "TRAINER").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedRoleCode").value("TRAINER"))
                .andExpect(jsonPath("$.data.reviewedBy.id").value(cmUser.getId()))
                .andExpect(jsonPath("$.data.createdUserId").isNumber())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        RegistrationRequest decided = requests.findById(req.getId()).orElseThrow();
        assertThat(decided.getPasswordHash()).isNull();
        AppUser created = users.findWithRolesById(decided.getCreatedUserId()).orElseThrow();
        assertThat(created.getPasswordHash()).isEqualTo(originalHash);
        assertThat(created.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(created.getUserCode()).matches("U-\\d{3,}");
        assertThat(created.roleCodes()).containsExactly(RoleCode.TRAINER);
        Long assignedBy = jdbc.queryForObject("SELECT assigned_by_user_id FROM user_role WHERE user_id = ?", Long.class, created.getId());
        assertThat(assignedBy).isEqualTo(cmUser.getId());

        login(email, PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.data.user.roles[0]").value("TRAINER"));

        List<AuditLog> audit = auditLogs.findAll().stream()
                .filter(a -> (a.getTargetId() != null && a.getTargetId().equals(req.getId()) && "REGISTRATION_REQUEST".equals(a.getTargetType()))
                        || (a.getTargetId() != null && a.getTargetId().equals(created.getId()) && "APP_USER".equals(a.getTargetType())))
                .toList();
        assertThat(audit).extracting(AuditLog::getAction).containsExactlyInAnyOrder("REGISTRATION_APPROVED", "ROLE_ASSIGNED");
        assertThat(audit).allSatisfy(a -> {
            assertThat(a.getActorUserId()).isEqualTo(cmUser.getId());
            assertThat(a.getActorRole()).isEqualTo("CLUB_MANAGER");
            assertThat(a.getSafeSummary()).doesNotContain(PASSWORD).doesNotContain("$2a$").doesNotContain(email);
        });
    }

    @Test
    void approveNeedsAClubManagerAndExactlyOneValidRole() throws Exception {
        String email = email("needsrole");
        registerOk(email);
        Long id = pending(email).getId();

        for (RoleCode other : List.of(RoleCode.HEAD_TRAINER, RoleCode.TRAINER, RoleCode.WORK_RIDER, RoleCode.VETERINARIAN,
                RoleCode.GROOM, RoleCode.HORSE_OWNER)) {
            approve(tokenFor(staff(other)), id, "GROOM").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
        mvc.perform(post("/api/admin/registrations/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCode\":\"GROOM\"}")).andExpect(status().isUnauthorized());

        String cm = tokenFor(staff(RoleCode.CLUB_MANAGER));
        approve(cm, id, "SYSTEM").andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/registrations/" + id + "/approve").header("Authorization", "Bearer " + cm)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("roleCode"));
        assertThat(pending(email).getStatus()).isEqualTo(RegistrationStatus.PENDING_APPROVAL);
    }

    @Test
    void decidedRequestsAndEmailCollisionsGive409() throws Exception {
        String cm = tokenFor(staff(RoleCode.CLUB_MANAGER));
        String email = email("twice");
        registerOk(email);
        Long id = pending(email).getId();
        approve(cm, id, "GROOM").andExpect(status().isOk());
        approve(cm, id, "GROOM").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CONFLICT"));
        reject(cm, id, null).andExpect(status().isConflict());

        // An account with the same email appears while the request is pending: 409 and the request stays PENDING.
        String collide = email("collide");
        registerOk(collide);
        RegistrationRequest req = pending(collide);
        AppUser meanwhile = new AppUser("S-" + UUID.randomUUID().toString().substring(0, 6), "Created by MF-17", collide, null, AccountStatus.ACTIVE);
        meanwhile.addRole(roles.findByRoleCode(RoleCode.GROOM).orElseThrow(), null);
        users.save(meanwhile);
        approve(cm, req.getId(), "GROOM").andExpect(status().isConflict());
        RegistrationRequest still = requests.findById(req.getId()).orElseThrow();
        assertThat(still.getStatus()).isEqualTo(RegistrationStatus.PENDING_APPROVAL);
        assertThat(still.getPasswordHash()).isNotNull();
        reject(cm, req.getId(), "Duplicate of an existing account").andExpect(status().isOk());

        mvc.perform(post("/api/admin/registrations/999999/approve").header("Authorization", "Bearer " + cm)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCode\":\"GROOM\"}")).andExpect(status().isNotFound());
    }

    @Test
    void concurrentDoubleApproveGivesOneSuccessAndOneConflict() throws Exception {
        AppUser cmUser = staff(RoleCode.CLUB_MANAGER);
        AuthenticatedUser cm = new AuthenticatedUser(cmUser.getId(), cmUser.getUserCode(), List.of(RoleCode.CLUB_MANAGER));
        String email = email("race");
        registerOk(email);
        Long id = pending(email).getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = List.of(
                pool.submit(() -> { start.await(); return attempt(id, RoleCode.GROOM, cm); }),
                pool.submit(() -> { start.await(); return attempt(id, RoleCode.TRAINER, cm); }));
        start.countDown();
        List<String> outcomes = List.of(results.get(0).get(), results.get(1).get());
        pool.shutdown();

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "CONFLICT");
        assertThat(users.findAll().stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).count()).isEqualTo(1);
        assertThat(requests.findById(id).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.APPROVED);
    }

    private String attempt(Long id, RoleCode role, AuthenticatedUser cm) {
        try {
            service.approve(id, role, cm);
            return "OK";
        } catch (com.racehorse.common.error.BusinessException e) {
            return e.code().name();
        }
    }

    // ---------- R4: rejection, R5 login ----------

    @Test
    void rejectionClearsTheHashKeepsTheRowAndLoginBecomesTheGeneric401() throws Exception {
        String cm = tokenFor(staff(RoleCode.CLUB_MANAGER));
        String email = email("rejected");
        registerOk(email);
        Long id = pending(email).getId();

        reject(cm, id, "Not a club member").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.decisionReason").value("Not a club member"));
        RegistrationRequest row = requests.findById(id).orElseThrow();
        assertThat(row.getPasswordHash()).isNull();
        assertThat(row.getStatus()).isEqualTo(RegistrationStatus.REJECTED);

        String rejected = login(email, PASSWORD).andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = login(email("never-registered"), PASSWORD).andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(rejected).isEqualTo(unknown).doesNotContain("Not a club member");
        // The applicant-facing register answer never reveals the reason or the rejection.
        assertThat(registerOk(email)).isEqualTo(NEUTRAL);

        reject(cm, id, "x".repeat(501)).andExpect(status().isBadRequest());
    }

    // ---------- list ----------

    @Test
    void listIsClubManagerOnlyPagedAndNeverExposesHashes() throws Exception {
        String email = email("listed");
        registerOk(email);
        String cm = tokenFor(staff(RoleCode.CLUB_MANAGER));

        String body = mvc.perform(get("/api/admin/registrations").param("status", "PENDING_APPROVAL").param("size", "100")
                        .header("Authorization", "Bearer " + cm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.totalItems").isNumber())
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains(email).doesNotContainIgnoringCase("password").doesNotContain("$2a$");

        mvc.perform(get("/api/admin/registrations").header("Authorization", "Bearer " + tokenFor(staff(RoleCode.HEAD_TRAINER))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/registrations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/registrations").param("size", "500").header("Authorization", "Bearer " + cm))
                .andExpect(status().isBadRequest());
    }

    // ---------- R11 and schema ----------

    @Test
    void logsNeverContainPasswordsHashesOrFullEmails(CapturedOutput output) throws Exception {
        String email = email("quiet");
        registerOk(email);
        login(email, PASSWORD);
        String hash = pending(email).getPasswordHash();
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(hash).doesNotContain(email);
    }

    @Test
    void canonicalSchemaIsUntouchedAndTheTechnicalTableLivesOutsideIt() throws Exception {
        byte[] schema = Files.readAllBytes(Path.of("database/schema.sql"));
        String expected = Files.readString(Path.of("database/schema.sql.sha256")).split("\\s+")[0];
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(schema))).isEqualTo(expected);
        assertThat(new String(schema, StandardCharsets.UTF_8)).doesNotContain("registration_request");

        String ddl = Files.readString(Path.of("database/technical/registration_request.sql"));
        assertThat(ddl).contains("CREATE UNIQUE INDEX ux_registration_request__pending_email")
                .contains("WHERE status = 'PENDING_APPROVAL'")
                .contains("ck_registration_request__hash_only_while_pending");
    }
}
