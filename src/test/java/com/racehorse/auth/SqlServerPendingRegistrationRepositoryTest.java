package com.racehorse.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class SqlServerPendingRegistrationRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void bindsNormalizedEmailAndPendingStatusWithoutComparingPasswordInSql() {
        String normalizedEmail = "user@example.test";
        String passwordHash = "$2a$10$test-hash-value";
        when(jdbcTemplate.queryForList(
                eq(SqlServerPendingRegistrationRepository.FIND_PENDING_BY_NORMALIZED_EMAIL),
                eq(String.class),
                eq(normalizedEmail),
                eq("PENDING_APPROVAL")))
                .thenReturn(List.of(passwordHash));

        Optional<PendingRegistrationRepository.PendingCredentials> result =
                new SqlServerPendingRegistrationRepository(jdbcTemplate)
                        .findPendingByNormalizedEmail(normalizedEmail);

        assertEquals(passwordHash, result.orElseThrow().passwordHash());
        verify(jdbcTemplate).queryForList(
                SqlServerPendingRegistrationRepository.FIND_PENDING_BY_NORMALIZED_EMAIL,
                String.class,
                normalizedEmail,
                "PENDING_APPROVAL");
        String sql = SqlServerPendingRegistrationRepository.FIND_PENDING_BY_NORMALIZED_EMAIL
                .toLowerCase(java.util.Locale.ROOT);
        assertEquals(2, sql.chars().filter(character -> character == '?').count());
        assertFalse(sql.contains("password_hash ="));
        assertFalse(sql.contains("password ="));
    }
}
