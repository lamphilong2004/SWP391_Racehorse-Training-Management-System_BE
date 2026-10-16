package com.racehorse.common.audit;

import com.racehorse.auth.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {

    private static final String LOGOUT_ACTION = "USER_LOGGED_OUT";
    private static final String WEB_SOURCE = "WEB";
    private static final String LOGOUT_SUMMARY = "User logged out.";

    private final JdbcTemplate jdbcTemplate;

    public AuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordLogout(CurrentUser actor) {
        String actorRole = actor.roles().size() == 1 ? actor.roles().getFirst().name() : null;
        jdbcTemplate.update("""
                INSERT INTO dbo.audit_log (actor_user_id, actor_role, action, safe_summary, source)
                VALUES (?, ?, ?, ?, ?)
                """, actor.id(), actorRole, LOGOUT_ACTION, LOGOUT_SUMMARY, WEB_SOURCE);
    }
}
