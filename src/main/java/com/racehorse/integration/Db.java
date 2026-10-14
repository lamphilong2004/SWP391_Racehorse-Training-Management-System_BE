package com.racehorse.integration;

import com.racehorse.auth.AuthenticatedUser;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.identity.RoleCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/** DEMO INTEGRATION helpers: UTC DATETIME2 binding, inserts with generated keys, role guards, errors. */
public final class Db {
    private Db() {}

    public static LocalDateTime utc(Instant i) {
        return i == null ? null : LocalDateTime.ofInstant(i.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
    }

    public static LocalDateTime nowUtc() { return utc(Instant.now()); }

    public static Instant instant(ResultSet r, String col) throws SQLException {
        LocalDateTime v = r.getObject(col, LocalDateTime.class);
        return v == null ? null : v.toInstant(ZoneOffset.UTC);
    }

    public static Instant instant(Object v) {
        if (v == null) return null;
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime().toInstant(ZoneOffset.UTC);
        if (v instanceof LocalDateTime l) return l.toInstant(ZoneOffset.UTC);
        return Instant.parse(v.toString());
    }

    public static Long lng(ResultSet r, String col) throws SQLException {
        long v = r.getLong(col);
        return r.wasNull() ? null : v;
    }

    public static Long lng(Map<String, Object> row, String col) {
        Object v = row.get(col);
        return v == null ? null : ((Number) v).longValue();
    }

    public static long insert(JdbcTemplate jdbc, String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public static AuthenticatedUser user() {
        AuthenticatedUser u = SecurityActorAdapters.currentUser();
        if (u == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        return u;
    }

    public static AuthenticatedUser require(RoleCode... roles) {
        AuthenticatedUser u = user();
        if (Arrays.stream(roles).noneMatch(u::hasRole))
            throw new BusinessException(ErrorCode.FORBIDDEN, "This operation is not permitted for your role.");
        return u;
    }

    public static BusinessException notFound(String what) { return new BusinessException(ErrorCode.NOT_FOUND, what + " not found."); }
    public static BusinessException invalid(String msg) { return new BusinessException(ErrorCode.VALIDATION_ERROR, msg); }
    public static BusinessException conflict(String msg) { return new BusinessException(ErrorCode.CONFLICT, msg); }
    public static BusinessException rule(String msg) { return new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, msg); }
}
