package com.racehorse.registration;

import com.racehorse.auth.*;
import com.racehorse.common.api.ErrorDetail;
import com.racehorse.common.error.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import static com.racehorse.registration.RegistrationDtos.*;

@Service
public class RegistrationService {
    private final RegistrationRepository repository;
    private final AuthRepository auth;
    private final PasswordEncoder passwords;
    private final RegistrationRateLimiter limiter;
    private final TransactionTemplate transaction;
    private final Clock clock;
    @Autowired
    public RegistrationService(RegistrationRepository repository,AuthRepository auth,PasswordEncoder passwords,
                               RegistrationRateLimiter limiter,PlatformTransactionManager manager) {
        this(repository,auth,passwords,limiter,manager,Clock.systemUTC());
    }
    RegistrationService(RegistrationRepository repository,AuthRepository auth,PasswordEncoder passwords,
                        RegistrationRateLimiter limiter,PlatformTransactionManager manager,Clock clock) {
        this.repository=repository;this.auth=auth;this.passwords=passwords;this.limiter=limiter;this.clock=clock;
        this.transaction=new TransactionTemplate(manager);transaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }
    public NeutralMessage register(Register request,String ip) {
        String email=Normalizer.normalize(request.email.trim(),Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        String password=request.password;
        if(password==null || password.length()<8 || password.length()>64 || password.getBytes(StandardCharsets.UTF_8).length>72
                || password.codePoints().noneMatch(Character::isLetter) || password.codePoints().noneMatch(Character::isDigit)
                || password.equalsIgnoreCase(email) || !password.equals(request.confirmPassword)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,"Request validation failed.",
                    List.of(new ErrorDetail("password","Use a matching password with 8–64 characters, a letter and a digit, at most 72 UTF-8 bytes, distinct from email.")));
        }
        if(ip==null || ip.isBlank()) throw new IllegalStateException("The request does not contain a remote client address.");
        Instant now=clock.instant(); limiter.consume(ip,email,now);
        String hash=passwords.encode(password); // Hash even when a duplicate/cool-down request is discarded (DEC-008 R2).
        try {
            transaction.executeWithoutResult(s->{if(repository.canRegister(email,now))repository.create(request.fullName.trim(),email,hash,now);});
        } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
            // Neutral only after proving another request/account won the race; unrelated failures remain errors.
            if(repository.canRegister(email,now)) throw ex;
        }
        return new NeutralMessage("If the details can be accepted, your request is waiting for Club Manager approval.");
    }
    public Page<Request> list(long actor,Status status,int page,int size) {
        manager(actor);page(page,size); return repository.list(status,page,size);
    }
    public Request approve(long actor,long id,RoleCode role) {
        if(id<1 || role==null)throw new BusinessException(ErrorCode.VALIDATION_ERROR,"Invalid request or role.");
        try {
            return transaction.execute(s->{
                manager(actor);
                Request request=required(id);
                var pending=repository.pending(id).orElseThrow(RegistrationService::conflict);
                if(repository.accountExists(pending.email()))throw conflict();
                long roleId=repository.roleId(role);Instant now=clock.instant();
                long user=repository.createUser(request,pending.hash(),now);
                repository.assign(user,roleId,actor,now);
                if(repository.approve(id,user,roleId,actor,now)!=1)throw conflict();
                repository.audit(actor,"REGISTRATION_APPROVED","REGISTRATION_REQUEST",id,"Registration approved.",now);
                repository.audit(actor,"ROLE_ASSIGNED","APP_USER",user,"Approved role assigned.",now);
                return required(id);
            });
        } catch(DataIntegrityViolationException | ConcurrencyFailureException ex) { throw conflict(); }
    }
    public Request reject(long actor,long id,String reason) {
        if(id<1 || (reason!=null && reason.length()>500))throw new BusinessException(ErrorCode.VALIDATION_ERROR,"Invalid request or reason.");
        try {
            return transaction.execute(s->{
                manager(actor);
                required(id);Instant now=clock.instant();
                if(repository.reject(id,reason,actor,now)!=1)throw conflict();
                repository.audit(actor,"REGISTRATION_REJECTED","REGISTRATION_REQUEST",id,"Registration rejected.",now);
                return required(id);
            });
        } catch(ConcurrencyFailureException ex) { throw conflict(); }
    }
    private Request required(long id) {return repository.find(id).orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND,"Registration request not found."));}
    private void manager(long id) {
        CurrentUser actor=auth.findCurrentUser(id).orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED,"Authentication required."));
        if(actor.accountStatus()!=AccountStatus.ACTIVE || !actor.roles().contains(RoleCode.CLUB_MANAGER))
            throw new BusinessException(ErrorCode.FORBIDDEN,"An active Club Manager is required.");
    }
    private static BusinessException conflict(){return new BusinessException(ErrorCode.CONFLICT,"The request was already decided or its email is in use.");}
    private static void page(int page,int size){if(page<0 || size<1 || size>100)throw new BusinessException(ErrorCode.VALIDATION_ERROR,"Invalid pagination.");}
}
