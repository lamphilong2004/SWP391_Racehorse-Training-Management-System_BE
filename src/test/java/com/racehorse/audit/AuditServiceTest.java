package com.racehorse.audit;

import com.racehorse.auth.AuthenticatedUser;
import com.racehorse.identity.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CORE-007 audit writer: actor from the authenticated request, no SYSTEM role, no secrets in rows. */
@SpringBootTest
@ActiveProfiles("test")
class AuditServiceTest {

    @Autowired AuditService auditService;
    @Autowired AuditLogRepository repository;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void writesRowWithActorAndRoleFromTheAuthenticatedUser() {
        var principal = new AuthenticatedUser(42L, "U-042", List.of(RoleCode.HEAD_TRAINER));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(RoleCode.HEAD_TRAINER.authority()))));

        AuditLog saved = auditService.record("HEAD_TRAINER_READINESS_REVIEWED", "HORSE", 7L, "Readiness acknowledged for H-0007");

        AuditLog row = repository.findById(saved.getId()).orElseThrow();
        assertThat(row.getActorUserId()).isEqualTo(42L);
        assertThat(row.getActorRole()).isEqualTo("HEAD_TRAINER");
        assertThat(row.getAction()).isEqualTo("HEAD_TRAINER_READINESS_REVIEWED");
        assertThat(row.getTargetType()).isEqualTo("HORSE");
        assertThat(row.getTargetId()).isEqualTo(7L);
        assertThat(row.getOccurredAt()).isNotNull();
        assertThat(row.getSource()).isEqualTo(AuditService.SOURCE_API);
    }

    @Test
    void automaticBehaviourHasNoActorAndNoSystemRole() {
        AuditLog row = repository.findById(auditService.record("HORSE_ACTIVATED", "HORSE", 9L, "All activation checks complete").getId()).orElseThrow();
        assertThat(row.getActorUserId()).isNull();
        assertThat(row.getActorRole()).isNull();
    }

    @Test
    void rejectsSummariesThatLookLikeSecrets() {
        assertThatThrownBy(() -> auditService.record("X", null, null, "password=Racehorse2026"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditService.record("X", null, null, "hash $2a$10$abcdefghijklmnopqrstuv"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditService.record("X", null, null, "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditService.record("X", null, null, "Authorization: Bearer abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesTheTargetPairAndLengths() {
        assertThatThrownBy(() -> auditService.record("X", "HORSE", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditService.record("A".repeat(101), null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
