package com.racehorse.auth;

import com.racehorse.identity.AccountStatus;
import com.racehorse.identity.AppUser;
import com.racehorse.identity.AppUserRepository;
import com.racehorse.identity.RoleCode;
import com.racehorse.identity.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CORE-007: login outcomes (DEC-008 §6 R5), rate limit (R8), /me, logout, token validation, logging (R11). */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Import(AuthApiTest.PendingRegistrationStub.class)
class AuthApiTest {

    /** Stands in for CORE-047: one email has a PENDING request whose password is AuthTestSupport.PASSWORD. */
    @TestConfiguration
    static class PendingRegistrationStub {
        static final String PENDING_EMAIL = "pending.applicant@club.test";

        @Bean
        @Primary // takes precedence over the real CORE-047 implementation in this auth-only test
        RegistrationLoginCheck registrationLoginCheck() {
            return (email, password) -> !PENDING_EMAIL.equals(email) ? RegistrationLoginCheck.Outcome.NO_PENDING_REQUEST
                    : AuthTestSupport.PASSWORD.equals(password) ? RegistrationLoginCheck.Outcome.PENDING_PASSWORD_MATCHES
                    : RegistrationLoginCheck.Outcome.PENDING_PASSWORD_MISMATCH;
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired JwtProperties jwtProperties;

    MockMvc mvc;
    AuthTestSupport support;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        support = new AuthTestSupport(users, roles, encoder);
    }

    private static String unique(String local) {
        return local + "." + UUID.randomUUID().toString().substring(0, 8) + "@club.test";
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String token(MvcResult result) throws Exception {
        Matcher m = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void activeUserGetsJwtAndUserWithoutAnyPasswordData() throws Exception {
        String email = unique("vet");
        AppUser user = support.user(email, RoleCode.VETERINARIAN, AccountStatus.ACTIVE, true);

        MvcResult result = login(email.toUpperCase(), AuthTestSupport.PASSWORD, "10.0.0.1") // case-insensitive email
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(jwtProperties.expiresIn().toSeconds()))
                .andExpect(jsonPath("$.data.user.id").value(user.getId()))
                .andExpect(jsonPath("$.data.user.userCode").value(user.getUserCode()))
                .andExpect(jsonPath("$.data.user.fullName").value(user.getFullName()))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.roles[0]").value("VETERINARIAN"))
                .andExpect(jsonPath("$.data.user.accountStatus").value("ACTIVE"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("password").doesNotContain("$2a$").doesNotContain("$2b$");
        assertThat(token(result)).isNotBlank();
        assertThat(users.findById(user.getId()).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void wrongPasswordUnknownEmailAndNullHashGiveTheIdenticalGeneric401() throws Exception {
        String email = unique("groom");
        support.user(email, RoleCode.GROOM, AccountStatus.ACTIVE, true);
        String nullHash = unique("newstaff");
        support.user(nullHash, RoleCode.TRAINER, AccountStatus.ACTIVE, false);

        String wrongPassword = login(email, "Wrong-password-1", "10.0.0.2").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknown = login(unique("nobody"), AuthTestSupport.PASSWORD, "10.0.0.2").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String noHash = login(nullHash, AuthTestSupport.PASSWORD, "10.0.0.2").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknown).isEqualTo(noHash)
                .isEqualTo("{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Invalid email or password.\",\"details\":[]}}");
    }

    @Test
    void inactiveUserGets403OnlyAfterACorrectPassword() throws Exception {
        String email = unique("rider");
        support.user(email, RoleCode.WORK_RIDER, AccountStatus.INACTIVE, true);

        login(email, AuthTestSupport.PASSWORD, "10.0.0.3")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Your account is deactivated."))
                .andExpect(jsonPath("$.data").doesNotExist());
        login(email, "Wrong-password-1", "10.0.0.3")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("Invalid email or password."));
    }

    @Test
    void pendingRegistrationHookGives403WithoutTokenOnlyAfterCorrectPassword() throws Exception {
        login(PendingRegistrationStub.PENDING_EMAIL, AuthTestSupport.PASSWORD, "10.0.0.4")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("Your account is waiting for Club Manager approval."))
                .andExpect(jsonPath("$.data").doesNotExist());
        login(PendingRegistrationStub.PENDING_EMAIL, "Wrong-password-1", "10.0.0.4")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("Invalid email or password."));
    }

    @Test
    void tenFailuresPerIpAndEmailThenGeneric429EvenWithTheCorrectPassword() throws Exception {
        String email = unique("owner");
        support.user(email, RoleCode.HORSE_OWNER, AccountStatus.ACTIVE, true);
        for (int i = 0; i < 10; i++) {
            login(email, "Wrong-password-1", "10.0.0.5").andExpect(status().isUnauthorized());
        }
        login(email, AuthTestSupport.PASSWORD, "10.0.0.5")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.error.message").value("Too many attempts. Try again later."));
        // Same answer for an email that does not exist (no account enumeration through the limiter).
        String ghost = unique("ghost");
        for (int i = 0; i < 10; i++) {
            login(ghost, "Wrong-password-1", "10.0.0.5").andExpect(status().isUnauthorized());
        }
        login(ghost, "Wrong-password-1", "10.0.0.5").andExpect(status().isTooManyRequests());
        // Another client IP is not affected.
        login(email, AuthTestSupport.PASSWORD, "10.0.0.6").andExpect(status().isOk());
    }

    @Test
    void invalidBodyReturns400Envelope() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void meRequiresAValidTokenAndReturnsTheCurrentUser() throws Exception {
        String email = unique("ht");
        AppUser user = support.user(email, RoleCode.HEAD_TRAINER, AccountStatus.ACTIVE, true);
        String jwt = token(login(email, AuthTestSupport.PASSWORD, "10.0.0.7").andReturn());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.roles[0]").value("HEAD_TRAINER"));
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void expiredTokenAndForeignIssuerAreRejected() throws Exception {
        AppUser user = support.user(unique("cm"), RoleCode.CLUB_MANAGER, AccountStatus.ACTIVE, true);
        String expired = sign(user.getId(), jwtProperties.issuer(), Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800));
        String foreign = sign(user.getId(), "someone-else", Instant.now(), Instant.now().plusSeconds(600));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + foreign)).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivationTakesEffectImmediatelyForAnIssuedToken() throws Exception {
        String email = unique("trainer");
        AppUser user = support.user(email, RoleCode.TRAINER, AccountStatus.ACTIVE, true);
        String jwt = token(login(email, AuthTestSupport.PASSWORD, "10.0.0.8").andReturn());

        AppUser stored = users.findById(user.getId()).orElseThrow();
        stored.setAccountStatus(AccountStatus.INACTIVE);
        users.save(stored);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutNeedsAuthenticationAndReturns204() throws Exception {
        String email = unique("groom2");
        support.user(email, RoleCode.GROOM, AccountStatus.ACTIVE, true);
        String jwt = token(login(email, AuthTestSupport.PASSWORD, "10.0.0.9").andReturn());

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + jwt)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void logsNeverContainPasswordsTokensOrFullEmails(CapturedOutput output) throws Exception {
        String email = unique("secretive");
        support.user(email, RoleCode.VETERINARIAN, AccountStatus.ACTIVE, true);
        String jwt = token(login(email, AuthTestSupport.PASSWORD, "10.0.0.10").andReturn());
        login(email, "Wrong-password-1", "10.0.0.10");

        assertThat(output.getAll()).doesNotContain(AuthTestSupport.PASSWORD).doesNotContain("Wrong-password-1")
                .doesNotContain(email).doesNotContain(jwt).contains(EmailAddresses.mask(email));
    }

    private String sign(Long userId, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(String.valueOf(userId))
                .issuedAt(issuedAt).expiresAt(expiresAt).claim("roles", List.of("CLUB_MANAGER")).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
