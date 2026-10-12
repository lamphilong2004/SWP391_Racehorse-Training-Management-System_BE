package com.racehorse.registration;

import com.racehorse.identity.AppUser;
import com.racehorse.identity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, Long> {

    Optional<RegistrationRequest> findFirstByEmailNormalizedAndStatus(String emailNormalized, RegistrationStatus status);

    boolean existsByEmailNormalizedAndStatus(String emailNormalized, RegistrationStatus status);

    Optional<RegistrationRequest> findFirstByEmailNormalizedOrderByRequestedAtDesc(String emailNormalized);

    long countByEmailNormalizedAndStatus(String emailNormalized, RegistrationStatus status);

    @EntityGraph(attributePaths = {"reviewedBy", "approvedRole"})
    Page<RegistrationRequest> findByStatus(RegistrationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"reviewedBy", "approvedRole"})
    Page<RegistrationRequest> findAllBy(Pageable pageable);

    /**
     * R3: the decision is applied only if the request is still PENDING_APPROVAL; the hash is cleared in the same
     * statement (ck_registration_request__hash_only_while_pending). Returns 0 when someone decided first.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RegistrationRequest r set r.status = com.racehorse.registration.RegistrationStatus.APPROVED, "
            + "r.passwordHash = null, r.reviewedBy = :reviewer, r.reviewedAt = :at, r.approvedRole = :role, "
            + "r.createdUserId = :createdUserId, r.updatedAt = :at "
            + "where r.id = :id and r.status = com.racehorse.registration.RegistrationStatus.PENDING_APPROVAL")
    int markApproved(@Param("id") Long id, @Param("reviewer") AppUser reviewer, @Param("role") Role role,
                     @Param("createdUserId") Long createdUserId, @Param("at") Instant at);

    /** R4: conditional rejection; the hash is cleared immediately. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RegistrationRequest r set r.status = com.racehorse.registration.RegistrationStatus.REJECTED, "
            + "r.passwordHash = null, r.reviewedBy = :reviewer, r.reviewedAt = :at, r.decisionReason = :reason, "
            + "r.updatedAt = :at "
            + "where r.id = :id and r.status = com.racehorse.registration.RegistrationStatus.PENDING_APPROVAL")
    int markRejected(@Param("id") Long id, @Param("reviewer") AppUser reviewer, @Param("reason") String reason,
                     @Param("at") Instant at);
}
