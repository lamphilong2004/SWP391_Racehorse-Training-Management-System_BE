package com.racehorse.account;

import com.racehorse.common.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import static com.racehorse.account.AccountModels.*;

@Service
public class PasswordResetService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PasswordResetService.class);
    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final PasswordEncoder passwords;
    private final SecretKey key;
    private final DevPasswordOutbox outbox;
    private final Map<String,Window> limits=new HashMap<>();
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public PasswordResetService(JdbcTemplate jdbc,AccountService accounts,PasswordEncoder passwords,
                                SecretKey key,DevPasswordOutbox outbox) {
        this(jdbc,accounts,passwords,key,outbox,Clock.systemUTC());
    }
    PasswordResetService(JdbcTemplate jdbc,AccountService accounts,PasswordEncoder passwords,
                         SecretKey key,DevPasswordOutbox outbox,Clock clock) {
        this.jdbc=jdbc;this.accounts=accounts;this.passwords=passwords;this.key=key;this.outbox=outbox;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public void forgot(Forgot request,String ip) {
        String email=AccountService.normalize(request.email());
        reserve(email,ip);
        if(!outbox.enabled()) return; // No approved production email provider. Never claim actual delivery.
        List<Long> users=jdbc.queryForList("SELECT id FROM dbo.app_user WHERE email=? AND account_status='ACTIVE'",Long.class,email);
        if(users.size()!=1) return;
        AccountService.Credentials user=accounts.credentials(users.getFirst());
        String payload=user.id+"."+clock.instant().plusSeconds(1800).getEpochSecond()+"."+fingerprint(user);
        String token=payload+"."+sign(payload);
        try { outbox.deliver(email,token); }
        catch(IllegalStateException deliveryFailure) {
            // R9: delivery failure must not turn active-account lookup into an enumeration oracle.
            // The constant warning deliberately excludes recipient, token, path and exception details.
            log.warn("Development password reset delivery unavailable; check private outbox access.");
        }
    }
    @Transactional
    public void reset(Reset request) {
        try {
            String[] parts=request.token().split("\\.",-1);
            if(parts.length!=4 || request.token().length()>1024) throw invalid();
            String payload=parts[0]+"."+parts[1]+"."+parts[2];
            if(!MessageDigest.isEqual(decode(parts[3]),decode(sign(payload)))) throw invalid();
            long id=Long.parseLong(parts[0]), expiry=Long.parseLong(parts[1]);
            if(id<=0 || expiry<=clock.instant().getEpochSecond() || expiry>clock.instant().plusSeconds(1800).getEpochSecond()) throw invalid();
            AccountService.Credentials user=accounts.credentials(id);
            if(!"ACTIVE".equals(user.status)||!MessageDigest.isEqual(decode(parts[2]),decode(fingerprint(user)))) throw invalid();
            AccountService.validatePassword(request.newPassword(),request.confirmPassword(),user.email);
            accounts.replacePassword(user,passwords.encode(request.newPassword()),"PASSWORD_RESET");
        } catch(NumberFormatException ex) { throw invalid(); }
        catch(IllegalArgumentException ex) { throw invalid(); }
        catch(BusinessException ex) {
            if(ex.code()==ErrorCode.NOT_FOUND) throw invalid();
            throw ex;
        }
    }
    private synchronized void reserve(String email,String ip) {
        Instant now=clock.instant();limits.entrySet().removeIf(e->!e.getValue().end.isAfter(now));
        String emailKey="email:"+sign(email),ipKey="ip:"+sign(ip==null?"unknown":ip);
        Window mail=limits.get(emailKey),address=limits.get(ipKey);
        // In-memory per-instance limits per DEC-008 R8. A shared limiter is required before production.
        if(mail!=null&&mail.count>=3||address!=null&&address.count>=10||limits.size()>=20000)
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,"Too many attempts. Try again later.");
        increment(emailKey,now);increment(ipKey,now);
    }
    private void increment(String key,Instant now) {
        Window old=limits.get(key);limits.put(key,old==null?new Window(now.plusSeconds(3600),1):new Window(old.end,old.count+1));
    }
    private String fingerprint(AccountService.Credentials user) {
        return sign("fingerprint:"+user.id+":"+Objects.toString(user.hash,"<NULL>")+":"+(user.updated==null?"<NULL>":user.updated.toInstant(ZoneOffset.UTC).toString()));
    }
    private String sign(String payload) {
        try {
            Mac hmac=Mac.getInstance("HmacSHA256");hmac.init(key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac.doFinal(("racehorse-password-reset-v1:"+payload).getBytes(StandardCharsets.UTF_8)));
        } catch(GeneralSecurityException ex) { throw new IllegalStateException("Password reset signing is unavailable."); }
    }
    private static byte[] decode(String value) { return Base64.getUrlDecoder().decode(value); }
    private static BusinessException invalid() {return new BusinessException(ErrorCode.VALIDATION_ERROR,"The reset link is invalid or has expired.");}
    private record Window(Instant end,int count) {}
}