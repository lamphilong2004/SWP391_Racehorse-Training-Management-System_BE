package com.racehorse.auth;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Service
public class AuthenticationService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";
    private static final String PENDING_REGISTRATION =
            "Your account is waiting for Club Manager approval.";

    private final AuthRepository authRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final LoginRateLimiter rateLimiter;
    private final String dummyPasswordHash;
    private final Clock clock = Clock.systemUTC();

    public AuthenticationService(
            AuthRepository authRepository,
            PendingRegistrationRepository pendingRegistrationRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            LoginRateLimiter rateLimiter,
            DummyPasswordHash dummyPasswordHash) {
        this.authRepository = authRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.rateLimiter = rateLimiter;
        this.dummyPasswordHash = dummyPasswordHash.value();
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String remoteAddress) {
        String normalizedEmail = EmailAddressNormalizer.normalize(request.getEmail());
        LoginRateLimiter.Permit permit = rateLimiter.reserve(remoteAddress, normalizedEmail, clock.instant());
        if (permit == null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Too many attempts. Try again later.");
        }
        try {
            AuthRepository.UserAccount account = authRepository.findByEmail(normalizedEmail).orElse(null);
            if (account == null) {
                Optional<PendingRegistrationRepository.PendingCredentials> pendingRegistration =
                        pendingRegistrationRepository.findPendingByNormalizedEmail(normalizedEmail);
                if (pendingRegistration.isPresent()) {
                    if (passwordEncoder.matches(
                            request.getPassword(), pendingRegistration.orElseThrow().passwordHash())) {
                        throw failed(permit, ErrorCode.FORBIDDEN, PENDING_REGISTRATION);
                    }
                    throw failed(permit, ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS);
                }
                passwordEncoder.matches(request.getPassword(), dummyPasswordHash);
                throw failed(permit, ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS);
            }
            if (account.passwordHash() == null) {
                passwordEncoder.matches(request.getPassword(), dummyPasswordHash);
                throw failed(permit, ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS);
            }
            if (!passwordEncoder.matches(request.getPassword(), account.passwordHash())) {
                throw failed(permit, ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS);
            }
            if (account.accountStatus() == AccountStatus.INACTIVE) {
                throw failed(permit, ErrorCode.FORBIDDEN, "Your account is deactivated.");
            }

            List<RoleCode> roles = authRepository.findRoleCodes(account.id());
            if (roles.isEmpty()) {
                throw failed(permit, ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS);
            }

            if (authRepository.updateLastLoginAt(account.id(), clock.instant()) != 1) {
                throw new IllegalStateException("Could not update last_login_at for the authenticated user.");
            }
            JwtTokenService.IssuedToken token = jwtTokenService.issue(account.id(), roles);
            rateLimiter.recordSuccess(permit);
            return new LoginResponse(token.value(), "Bearer", token.expiresIn(), account.toCurrentUser(roles));
        } finally {
            rateLimiter.release(permit);
        }
    }

    private BusinessException failed(LoginRateLimiter.Permit permit, ErrorCode code, String message) {
        rateLimiter.recordFailure(permit, clock.instant());
        return new BusinessException(code, message);
    }

    public record DummyPasswordHash(String value) {
    }
}
