package com.racehorse.registration;

import com.racehorse.audit.AuditService;
import com.racehorse.auth.AuthenticatedUser;
import com.racehorse.auth.EmailAddresses;
import com.racehorse.common.api.ErrorDetail;
import com.racehorse.common.api.PageResponse;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.identity.AccountStatus;
import com.racehorse.identity.AppUser;
import com.racehorse.identity.AppUserRepository;
import com.racehorse.identity.Role;
import com.racehorse.identity.RoleCode;
import com.racehorse.identity.RoleRepository;
import com.racehorse.identity.UserCodeGenerator;
import com.racehorse.registration.RegistrationDtos.RegistrationRequestView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * DEC-008 registration as an access request (§2.1) with the §6 Amendment A policy:
 * R1 duplicate-email rules, R2 hashing at submission, R3 approval, R4 rejection, R7 no role, R8 neutral 202 + limits,
 * R11 masked logging, R12 hash only while PENDING. The applicant receives no email or notification (§3 item 7).
 */
@Service
public class RegistrationService {

    public static final String NEUTRAL_MESSAGE = "If the details can be accepted, your request is waiting for Club Manager approval.";
    static final String TOO_MANY_ATTEMPTS = "Too many attempts. Try again later.";
    static final String ALREADY_DECIDED = "This registration request has already been decided.";
    static final String EMAIL_TAKEN = "An account with this email already exists. Reject the request instead.";

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final RegistrationRequestRepository requests;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final UserCodeGenerator userCodes;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationRateLimiter rateLimiter;
    private final RegistrationProperties properties;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RegistrationService(RegistrationRequestRepository requests, AppUserRepository users, RoleRepository roles,
                               UserCodeGenerator userCodes, PasswordEncoder passwordEncoder, RegistrationRateLimiter rateLimiter,
                               RegistrationProperties properties, AuditService audit, TransactionTemplate tx, Clock clock) {
        this.requests = requests;
        this.users = users;
        this.roles = roles;
        this.userCodes = userCodes;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    /** Always ends with the same neutral answer unless the fields are invalid (400) or a limit is hit (429). */
    public void register(String fullName, String email, String password, String confirmPassword, String clientIp) {
        List<ErrorDetail> errors = PasswordPolicy.check(password, confirmPassword, email);
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed.", errors);
        }
        String normalized = EmailAddresses.normalize(email);
        if (!rateLimiter.tryAcquire(clientIp, normalized)) {
            log.warn("Registration rate limit reached for {}", EmailAddresses.mask(normalized));
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, TOO_MANY_ATTEMPTS);
        }
        // R2: hash immediately, also when no request will be created, so timing does not reveal the outcome.
        String hash = passwordEncoder.encode(password);
        try {
            Boolean created = tx.execute(status -> createIfAllowed(fullName.trim(), email.trim(), normalized, hash));
            log.info("Registration {} for {}", Boolean.TRUE.equals(created) ? "accepted" : "not created (policy)",
                    EmailAddresses.mask(normalized));
        } catch (DataIntegrityViolationException e) {
            // Concurrent submission for the same email: ux_registration_request__pending_email kept one PENDING row.
            log.info("Registration not created (concurrent pending request) for {}", EmailAddresses.mask(normalized));
        }
    }

    private boolean createIfAllowed(String fullName, String email, String normalized, String hash) {
        Instant now = clock.instant();
        if (users.existsByEmailIgnoreCase(normalized)) {
            return false;                                              // R1 (1): an app_user exists, any status
        }
        if (requests.existsByEmailNormalizedAndStatus(normalized, RegistrationStatus.PENDING_APPROVAL)) {
            return false;                                              // R1 (2): already waiting
        }
        if (requests.countByEmailNormalizedAndStatus(normalized, RegistrationStatus.REJECTED) >= properties.maxRejections()) {
            return false;                                              // R1 (4): only a Club Manager can create it now
        }
        boolean inCooldown = requests.findFirstByEmailNormalizedOrderByRequestedAtDesc(normalized)
                .filter(r -> r.getStatus() == RegistrationStatus.REJECTED)
                .map(r -> r.getReviewedAt().plus(properties.rejectionCooldown()).isAfter(now))
                .orElse(false);
        if (inCooldown) {
            return false;                                              // R1 (3): < 7 days since the rejection
        }
        requests.saveAndFlush(new RegistrationRequest(fullName, email, normalized, hash, now));
        return true;
    }

    public PageResponse<RegistrationRequestView> list(RegistrationStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed.",
                    List.of(new ErrorDetail(page < 0 ? "page" : "size", page < 0 ? "must be ≥ 0" : "must be 1–100")));
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt"));
        var result = status == null ? requests.findAllBy(pageable) : requests.findByStatus(status, pageable);
        return PageResponse.of(result, RegistrationRequestView::of);
    }

    /**
     * R3 in one transaction: still PENDING, email re-check (409, stays PENDING), app_user ACTIVE with the request's
     * hash and a U-nnn code, user_role assigned by the Club Manager, conditional update to APPROVED with the hash
     * cleared, audit REGISTRATION_APPROVED + ROLE_ASSIGNED. Any conflict rolls the whole transaction back.
     */
    public RegistrationRequestView approve(Long id, RoleCode roleCode, AuthenticatedUser clubManager) {
        try {
            return tx.execute(status -> {
                RegistrationRequest request = requests.findById(id).orElseThrow(RegistrationService::notFound);
                if (request.getStatus() != RegistrationStatus.PENDING_APPROVAL) {
                    throw new BusinessException(ErrorCode.CONFLICT, ALREADY_DECIDED);
                }
                if (users.existsByEmailIgnoreCase(request.getEmailNormalized())) {
                    throw new BusinessException(ErrorCode.CONFLICT, EMAIL_TAKEN);
                }
                Role role = roles.findByRoleCode(roleCode).orElseThrow(() ->
                        new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "The role " + roleCode + " is not configured."));
                AppUser reviewer = users.getReferenceById(clubManager.id());

                AppUser user = new AppUser(userCodes.next(), request.getFullName(), request.getEmail(),
                        request.getPasswordHash(), AccountStatus.ACTIVE);
                user.addRole(role, reviewer);
                users.saveAndFlush(user);

                if (requests.markApproved(id, reviewer, role, user.getId(), clock.instant()) != 1) {
                    throw new BusinessException(ErrorCode.CONFLICT, ALREADY_DECIDED);
                }
                audit.record("REGISTRATION_APPROVED", "REGISTRATION_REQUEST", id,
                        "Registration request approved; account " + user.getUserCode() + " created.");
                audit.record("ROLE_ASSIGNED", "APP_USER", user.getId(),
                        "Role " + roleCode + " assigned to " + user.getUserCode() + " on registration approval.");
                log.info("Registration {} approved as {}", id, roleCode);
                return reload(id);
            });
        } catch (DataIntegrityViolationException e) {
            // Concurrent approval or a concurrent account with the same email / user code: nothing was written.
            throw new BusinessException(ErrorCode.CONFLICT, ALREADY_DECIDED);
        }
    }

    /** R4: conditional rejection; hash cleared immediately; row kept; reason visible to Club Managers only. */
    public RegistrationRequestView reject(Long id, String reason, AuthenticatedUser clubManager) {
        return tx.execute(status -> {
            requests.findById(id).orElseThrow(RegistrationService::notFound);
            AppUser reviewer = users.getReferenceById(clubManager.id());
            String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
            if (requests.markRejected(id, reviewer, cleanReason, clock.instant()) != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, ALREADY_DECIDED);
            }
            audit.record("REGISTRATION_REJECTED", "REGISTRATION_REQUEST", id, "Registration request rejected.");
            log.info("Registration {} rejected", id);
            return reload(id);
        });
    }

    private RegistrationRequestView reload(Long id) {
        RegistrationRequest r = requests.findById(id).orElseThrow(RegistrationService::notFound);
        return RegistrationRequestView.of(r);
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "The registration request was not found.");
    }
}
