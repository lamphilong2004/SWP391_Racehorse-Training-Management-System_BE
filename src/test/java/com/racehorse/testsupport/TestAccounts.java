package com.racehorse.testsupport;

import com.racehorse.auth.RoleCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Creates ACTIVE accounts in the shared H2 fixture and logs them in through the real /api/auth/login. */
public final class TestAccounts {

    private static final String PASSWORD = "Test-Password-123";

    private TestAccounts() {
    }

    public record Account(long id, String token) {
    }

    public static Account create(MockMvc mvc, JdbcTemplate jdbc, PasswordEncoder encoder, JsonMapper json,
                                 long userId, RoleCode role) throws Exception {
        long roleId = roleId(jdbc, role);
        String email = "dec016-" + userId + "@example.test";
        jdbc.update("DELETE FROM dbo.user_role WHERE user_id = ?", userId);
        jdbc.update("""
                INSERT INTO dbo.app_user (id, user_code, full_name, email, password_hash, account_status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """, userId, "T-" + userId, "Test " + role.name() + " " + userId, email, encoder.encode(PASSWORD));
        jdbc.update("INSERT INTO dbo.user_role (id, user_id, role_id) VALUES (?, ?, ?)", userId + 900_000, userId, roleId);
        String body = json.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Account(userId, json.readTree(response).path("data").path("accessToken").stringValue());
    }

    private static long roleId(JdbcTemplate jdbc, RoleCode role) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM dbo.role WHERE role_code = ?", Long.class, role.name());
        if (!ids.isEmpty()) {
            return ids.getFirst();
        }
        long id = 800 + role.ordinal();
        jdbc.update("INSERT INTO dbo.role (id, role_code) VALUES (?, ?)", id, role.name());
        return id;
    }
}
