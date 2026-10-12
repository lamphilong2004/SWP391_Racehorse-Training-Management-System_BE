package com.racehorse.auth;

import com.racehorse.auth.AuthDtos.CurrentUser;
import com.racehorse.auth.AuthDtos.LoginResponse;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.identity.AccountStatus;
import com.racehorse.identity.AppUser;
import com.racehorse.identity.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Login per DEC-008 §6 R5, in this order:
 * <ol>
 *   <li>app_user found and password correct: ACTIVE → JWT; INACTIVE → 403 "Your account is deactivated."</li>
 *   <li>no app_user: {@link RegistrationLoginCheck} (CORE-047) may answer PENDING → 403 "waiting for approval", no token</li>
 *   <li>everything else (unknown email, wrong password, REJECTED request, NULL hash) → one identical 401, with a dummy
 *       BCrypt verification so the response time does not reveal whether the account exists</li>
 * </ol>
 * Failed attempts are rate-limited per IP + email (R8). Passwords and full emails are never logged (R11).
 */
@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "Invalid email or password.";
    static final String ACCOUNT_DEACTIVATED = "Your account is deactivated.";
    static final String PENDING_APPROVAL = "Your account is waiting for Club Manager approval.";
    static final String TOO_MANY_ATTEMPTS = "Too many attempts. Try again later.";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;
    private final ObjectProvider<RegistrationLoginCheck> registrationCheck;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginRateLimiter rateLimiter, ObjectProvider<RegistrationLoginCheck> registrationCheck, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.registrationCheck = registrationCheck;
        this.clock = clock;
        // Random per process: never matches a real password, costs the same as a real verification.
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResponse login(String email, String password, String clientIp) {
        String normalized = EmailAddresses.normalize(email);
        if (rateLimiter.isBlocked(clientIp, normalized)) {
            log.warn("Login rate limit reached for {}", EmailAddresses.mask(normalized));
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, TOO_MANY_ATTEMPTS);
        }

        Optional<AppUser> found = users.findWithRolesByEmail(normalized);
        if (found.isPresent()) {
            AppUser user = found.get();
            boolean passwordOk = user.getPasswordHash() != null
                    ? passwordEncoder.matches(password, user.getPasswordHash())
                    : dummyCheck(password); // NULL hash (MF-17 user before first reset): always 401, same timing
            if (!passwordOk) {
                return fail(clientIp, normalized);
            }
            if (user.getAccountStatus() != AccountStatus.ACTIVE) {
                log.info("Login refused for deactivated account {}", EmailAddresses.mask(normalized));
                throw new BusinessException(ErrorCode.FORBIDDEN, ACCOUNT_DEACTIVATED);
            }
            rateLimiter.reset(clientIp, normalized);
            user.recordLogin(clock.instant());
            JwtService.IssuedToken token = jwtService.issue(user);
            log.info("Login succeeded for {}", EmailAddresses.mask(normalized));
            return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), CurrentUser.of(user));
        }

        RegistrationLoginCheck.Outcome outcome = registrationCheck.getIfAvailable(() -> (e, p) -> RegistrationLoginCheck.Outcome.NO_PENDING_REQUEST)
                .check(normalized, password);
        switch (outcome) {
            case PENDING_PASSWORD_MATCHES -> {
                log.info("Login refused, registration pending for {}", EmailAddresses.mask(normalized));
                throw new BusinessException(ErrorCode.FORBIDDEN, PENDING_APPROVAL);
            }
            case PENDING_PASSWORD_MISMATCH -> {
                return fail(clientIp, normalized);
            }
            default -> {
                dummyCheck(password);
                return fail(clientIp, normalized);
            }
        }
    }

    @Transactional(readOnly = true)
    public CurrentUser currentUser(AuthenticatedUser principal) {
        return users.findWithRolesById(principal.id())
                .filter(AppUser::canAuthenticate)
                .map(CurrentUser::of)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required."));
    }

    private boolean dummyCheck(String password) {
        passwordEncoder.matches(password, dummyHash);
        return false;
    }

    private LoginResponse fail(String clientIp, String normalized) {
        rateLimiter.recordFailure(clientIp, normalized);
        log.info("Login failed for {}", EmailAddresses.mask(normalized));
        throw new BusinessException(ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS);
    }
}
