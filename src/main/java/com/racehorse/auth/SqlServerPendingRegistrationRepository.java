package com.racehorse.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SqlServerPendingRegistrationRepository implements PendingRegistrationRepository {

    static final String FIND_PENDING_BY_NORMALIZED_EMAIL = """
            SELECT password_hash
            FROM dbo.registration_request
            WHERE email_normalized = ? AND status = ?
            """;

    private static final String PENDING_APPROVAL = "PENDING_APPROVAL";

    private final JdbcTemplate jdbcTemplate;

    public SqlServerPendingRegistrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PendingCredentials> findPendingByNormalizedEmail(String normalizedEmail) {
        List<String> passwordHashes = jdbcTemplate.queryForList(
                FIND_PENDING_BY_NORMALIZED_EMAIL,
                String.class,
                normalizedEmail,
                PENDING_APPROVAL);
        return passwordHashes.stream()
                .findFirst()
                .map(PendingCredentials::new);
    }
}
