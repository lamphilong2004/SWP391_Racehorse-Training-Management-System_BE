package com.racehorse.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class AuthRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByEmail(String email) {
        List<UserAccount> users = jdbcTemplate.query("""
                SELECT id, user_code, full_name, email, password_hash, account_status
                FROM dbo.app_user
                WHERE email = ?
                """, (rs, rowNum) -> mapUserAccount(rs), email);
        return users.stream().findFirst();
    }

    public Optional<CurrentUser> findCurrentUser(long id) {
        List<CurrentUserRow> rows = jdbcTemplate.query("""
                SELECT u.id, u.user_code, u.full_name, u.email, u.account_status, r.role_code
                FROM dbo.app_user u
                LEFT JOIN dbo.user_role ur ON ur.user_id = u.id
                LEFT JOIN dbo.role r ON r.id = ur.role_id
                WHERE u.id = ?
                ORDER BY r.role_code
                """, (rs, rowNum) -> new CurrentUserRow(
                rs.getLong("id"),
                rs.getString("user_code"),
                rs.getString("full_name"),
                rs.getString("email"),
                AccountStatus.valueOf(rs.getString("account_status")),
                rs.getString("role_code")), id);
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        CurrentUserRow user = rows.get(0);
        List<RoleCode> roles = rows.stream()
                .map(CurrentUserRow::roleCode)
                .filter(role -> role != null)
                .distinct()
                .map(RoleCode::valueOf)
                .toList();
        return Optional.of(new CurrentUser(
                user.id(),
                user.userCode(),
                user.fullName(),
                user.email(),
                roles,
                user.accountStatus()));
    }

    public List<RoleCode> findRoleCodes(long userId) {
        return jdbcTemplate.query("""
                SELECT r.role_code
                FROM dbo.user_role ur
                JOIN dbo.role r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                ORDER BY r.role_code
                """, (rs, rowNum) -> RoleCode.valueOf(rs.getString("role_code")), userId);
    }

    public int updateLastLoginAt(long userId, Instant loggedInAt) {
        return jdbcTemplate.update("""
                UPDATE dbo.app_user
                SET last_login_at = ?
                WHERE id = ? AND account_status = 'ACTIVE'
                """, Timestamp.from(loggedInAt), userId);
    }

    private UserAccount mapUserAccount(ResultSet rs) throws SQLException {
        return new UserAccount(
                rs.getLong("id"),
                rs.getString("user_code"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                AccountStatus.valueOf(rs.getString("account_status")));
    }

    private record CurrentUserRow(
            long id,
            String userCode,
            String fullName,
            String email,
            AccountStatus accountStatus,
            String roleCode) {
    }

    public static final class UserAccount {

        private final long id;
        private final String userCode;
        private final String fullName;
        private final String email;
        private final String passwordHash;
        private final AccountStatus accountStatus;

        private UserAccount(
                long id,
                String userCode,
                String fullName,
                String email,
                String passwordHash,
                AccountStatus accountStatus) {
            this.id = id;
            this.userCode = userCode;
            this.fullName = fullName;
            this.email = email;
            this.passwordHash = passwordHash;
            this.accountStatus = accountStatus;
        }

        public long id() {
            return id;
        }

        public String userCode() {
            return userCode;
        }

        public String fullName() {
            return fullName;
        }

        public String email() {
            return email;
        }

        public String passwordHash() {
            return passwordHash;
        }

        public AccountStatus accountStatus() {
            return accountStatus;
        }

        public CurrentUser toCurrentUser(List<RoleCode> roles) {
            return new CurrentUser(id, userCode, fullName, email, roles, accountStatus);
        }

        @Override
        public String toString() {
            return "UserAccount[id=" + id + "]";
        }
    }
}
