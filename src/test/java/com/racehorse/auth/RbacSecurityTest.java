package com.racehorse.auth;

import com.racehorse.identity.AccountStatus;
import com.racehorse.identity.AppUserRepository;
import com.racehorse.identity.RoleCode;
import com.racehorse.identity.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CORE-007: @PreAuthorize for all 7 roles, permitAll = exactly the R10 list, CORS through the security chain. */
@SpringBootTest
@ActiveProfiles("test")
@Import(RbacSecurityTest.RoleProbeConfig.class)
class RbacSecurityTest {

    /** One endpoint per role, protected the way feature code will protect its endpoints. */
    @TestConfiguration
    static class RoleProbeConfig {
        @RestController
        static class RoleProbeController {
            @GetMapping("/api/test-rbac/CLUB_MANAGER") @PreAuthorize("hasRole('CLUB_MANAGER')") String cm() { return "ok"; }
            @GetMapping("/api/test-rbac/HEAD_TRAINER") @PreAuthorize("hasRole('HEAD_TRAINER')") String ht() { return "ok"; }
            @GetMapping("/api/test-rbac/TRAINER") @PreAuthorize("hasRole('TRAINER')") String tr() { return "ok"; }
            @GetMapping("/api/test-rbac/WORK_RIDER") @PreAuthorize("hasRole('WORK_RIDER')") String wr() { return "ok"; }
            @GetMapping("/api/test-rbac/VETERINARIAN") @PreAuthorize("hasRole('VETERINARIAN')") String vet() { return "ok"; }
            @GetMapping("/api/test-rbac/GROOM") @PreAuthorize("hasRole('GROOM')") String groom() { return "ok"; }
            @GetMapping("/api/test-rbac/HORSE_OWNER") @PreAuthorize("hasRole('HORSE_OWNER')") String owner() { return "ok"; }
            @GetMapping("/api/test-rbac/any/{role}") String any(@PathVariable String role) { return "ok"; }
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired RequestMappingHandlerMapping handlerMapping;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String loginAs(RoleCode role) throws Exception {
        String email = role.name().toLowerCase() + "." + UUID.randomUUID().toString().substring(0, 8) + "@club.test";
        new AuthTestSupport(users, roles, encoder).user(email, role, AccountStatus.ACTIVE, true);
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + AuthTestSupport.PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(body);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void roleCodesAreExactlyTheSevenApprovedRoles() {
        assertThat(Arrays.stream(RoleCode.values()).map(Enum::name)).containsExactly(
                "CLUB_MANAGER", "HEAD_TRAINER", "TRAINER", "WORK_RIDER", "VETERINARIAN", "GROOM", "HORSE_OWNER");
    }

    @Test
    void eachRoleReachesOnlyItsOwnProtectedEndpoint() throws Exception {
        for (RoleCode caller : RoleCode.values()) {
            String jwt = loginAs(caller);
            for (RoleCode target : RoleCode.values()) {
                var result = mvc.perform(get("/api/test-rbac/" + target.name()).header("Authorization", "Bearer " + jwt));
                if (caller == target) {
                    result.andExpect(status().isOk());
                } else {
                    result.andExpect(status().isForbidden())
                            .andExpect(jsonPath("$.success").value(false))
                            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
                }
            }
            mvc.perform(get("/api/test-rbac/any/" + caller.name()).header("Authorization", "Bearer " + jwt))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void everyMappedRouteOutsideTheR10ListRequiresAuthentication() throws Exception {
        Set<String> publicRoutes = Set.of("POST /api/auth/login", "POST /api/auth/register", "POST /api/auth/forgot-password",
                "POST /api/auth/reset-password", "GET /api/health");
        int checked = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Set<String> patterns = info.getPatternValues();
            Set<org.springframework.web.bind.annotation.RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                for (var method : methods.isEmpty() ? Set.of(org.springframework.web.bind.annotation.RequestMethod.GET) : methods) {
                    String path = pattern.replaceAll("\\{[^}]+}", "1");
                    String route = method.name() + " " + pattern;
                    var result = mvc.perform(request(HttpMethod.valueOf(method.name()), path).contentType(MediaType.APPLICATION_JSON).content("{}"));
                    int code = result.andReturn().getResponse().getStatus();
                    if (publicRoutes.contains(route)) {
                        assertThat(code).as(route).isNotEqualTo(401);
                    } else {
                        assertThat(code).as(route + " must require authentication").isEqualTo(401);
                        checked++;
                    }
                }
            }
        }
        assertThat(checked).isGreaterThan(8);
        // Not-yet-implemented R10 routes are permitted (they 404 until CORE-047/050 add them), never 401.
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        // Unknown routes are hidden behind authentication.
        mvc.perform(get("/api/horses")).andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerIsNotPublicOutsideTheDevProfile() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void corsPreflightWorksThroughTheSecurityChain() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/auth/me").header("Origin", "http://evil.example").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
