package com.racehorse.audit;

import com.racehorse.auth.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Writes audit_log rows (MF-20). The actor is taken from the authenticated request; with no authenticated user
 * (automatic backend behaviour) actor_user_id and actor_role stay NULL — there is no SYSTEM role.
 * Which actions are audited is decided per feature (Q-15, list proposed in CORE-051); this class is only the writer.
 */
@Service
public class AuditService {

    /** Default source for requests coming through the REST API. */
    public static final String SOURCE_API = "API";

    // Rejects summaries that look like they carry credentials: key words, BCrypt hashes, JWTs, bearer headers.
    private static final Pattern SECRET_LIKE = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|bearer\\s)|\\$2[abxy]?\\$\\d{2}\\$|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.");

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records one audit event in the caller's transaction (or a new one when there is none).
     *
     * @param action      stable action code, e.g. {@code HEAD_TRAINER_READINESS_REVIEWED} (≤ 100 characters)
     * @param targetType  entity name, e.g. {@code HORSE}; set together with targetId (ck_audit_log__target_pair)
     * @param targetId    entity id
     * @param safeSummary human-readable summary without any secret; rejected if it looks like it contains one
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog record(String action, String targetType, Long targetId, String safeSummary) {
        if (action == null || action.isBlank() || action.length() > 100) {
            throw new IllegalArgumentException("Audit action must be 1–100 characters.");
        }
        if ((targetType == null) != (targetId == null)) {
            throw new IllegalArgumentException("targetType and targetId must be set together.");
        }
        if (targetType != null && targetType.length() > 50) {
            throw new IllegalArgumentException("targetType must be at most 50 characters.");
        }
        if (safeSummary != null && SECRET_LIKE.matcher(safeSummary).find()) {
            throw new IllegalArgumentException("Audit summaries must not contain credentials or tokens.");
        }
        Long actorId = null;
        String actorRole = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            actorId = user.id();
            actorRole = user.primaryRole() == null ? null : user.primaryRole().name();
        }
        return repository.save(new AuditLog(actorId, actorRole, action, targetType, targetId, safeSummary, SOURCE_API));
    }
}
