package com.racehorse.account;

import com.racehorse.auth.*;
import com.racehorse.common.api.ErrorDetail;
import com.racehorse.common.error.*;
import com.racehorse.common.jdbc.GeneratedIdentity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import static com.racehorse.account.AccountModels.*;

@Service
public class AccountService {
    private final JdbcTemplate jdbc;
    private final AuthRepository auth;
    private final CurrentActorProvider actors;
    private final PasswordEncoder passwords;
    public AccountService(JdbcTemplate jdbc, AuthRepository auth, CurrentActorProvider actors, PasswordEncoder passwords) {
        this.jdbc=jdbc; this.auth=auth; this.actors=actors; this.passwords=passwords;
    }
    @Transactional(readOnly=true)
    public Profile own() { return profile(activeActor().userId()); }
    @Transactional
    public Profile updateOwn(UpdateProfile input) {
        long id=activeActor().userId();
        jdbc.update("UPDATE dbo.app_user SET full_name=?,updated_at=? WHERE id=?", input.fullName().strip(), now(), id);
        audit(id, "USER_UPDATED", id);
        return profile(id);
    }
    @Transactional
    public void changePassword(PasswordChange input) {
        long id=activeActor().userId();
        Credentials current=credentials(id);
        if(current.hash==null || !passwords.matches(input.currentPassword(), current.hash))
            throw invalid("currentPassword", "The current password is incorrect.");
        validatePassword(input.newPassword(),input.confirmPassword(),current.email);
        replacePassword(current, passwords.encode(input.newPassword()), "PASSWORD_CHANGED");
    }
    @Transactional(readOnly=true)
    public UserDetail detail(long id) { manager(); return user(id); }
    @Transactional(isolation=Isolation.SERIALIZABLE)
    public UserDetail create(UserInput input) {
        ActorContext actor=manager(); validateSkills(input);
        String email=normalize(input.email());
        String initialHash=managerPassword(input,email);
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement ps=connection.prepareStatement("INSERT INTO dbo.app_user (user_code,full_name,email,password_hash,account_status,experience_years,experience_level,created_at) VALUES (?,?,?,?,'ACTIVE',?,?,?)", new String[]{"id"});
                ps.setString(1,"NEW-"+UUID.randomUUID().toString().replace("-","").substring(0,16));
                ps.setString(2,input.fullName().strip()); ps.setString(3,email); ps.setString(4,initialHash);
                ps.setObject(5,input.experienceYears());ps.setString(6,input.experienceLevel());ps.setObject(7,now());return ps;
            },keys);
        } catch(DataIntegrityViolationException ex) { throw duplicateEmail(); }
        long id=GeneratedIdentity.requireId(keys);
        jdbc.update("UPDATE dbo.app_user SET user_code=? WHERE id=?", "U-"+String.format(Locale.ROOT,"%03d",id), id);
        assignRole(id,input.roleCode(),actor.userId()); replaceSkills(id,input.skillIds());
        audit(actor.userId(),"USER_CREATED",id);
        if(initialHash!=null) audit(actor.userId(),"USER_PASSWORD_SET_BY_MANAGER",id);
        return user(id);
    }
    @Transactional(isolation=Isolation.SERIALIZABLE)
    public UserDetail update(long id, UserInput input) {
        ActorContext actor=manager(); UserDetail before=user(id); validateSkills(input);
        if(id==actor.userId() && input.roleCode()!=RoleCode.CLUB_MANAGER)
            throw conflict("You cannot remove your own Club Manager role.");
        try {
            jdbc.update("UPDATE dbo.app_user SET full_name=?,email=?,experience_years=?,experience_level=?,updated_at=? WHERE id=?",
                input.fullName().strip(),normalize(input.email()),input.experienceYears(),input.experienceLevel(),now(),id);
        } catch(DataIntegrityViolationException ex) { throw duplicateEmail(); }
        if(!before.roles().equals(List.of(input.roleCode()))) assignRole(id,input.roleCode(),actor.userId());
        replaceSkills(id,input.skillIds()); audit(actor.userId(),"USER_UPDATED",id);
        String newHash=managerPassword(input,normalize(input.email()));
        if(newHash!=null) {
            jdbc.update("UPDATE dbo.app_user SET password_hash=?,updated_at=? WHERE id=?",newHash,now(),id);
            audit(actor.userId(),"USER_PASSWORD_SET_BY_MANAGER",id);
        }
        return user(id);
    }
    @Transactional(isolation=Isolation.SERIALIZABLE)
    public UserDetail status(long id, StatusInput input) {
        ActorContext actor=manager(); UserDetail before=user(id);
        if(id==actor.userId() && input.accountStatus()==AccountStatus.INACTIVE)
            throw conflict("You cannot deactivate your own account.");
        if(before.accountStatus()!=input.accountStatus()) {
            jdbc.update("UPDATE dbo.app_user SET account_status=?,updated_at=? WHERE id=?",input.accountStatus().name(),now(),id);
            audit(actor.userId(),input.accountStatus()==AccountStatus.ACTIVE?"USER_ACTIVATED":"USER_DEACTIVATED",id);
        }
        return user(id);
    }
    @Transactional(readOnly=true)
    public List<Skill> skills(SkillKind kind) {
        manager(); return jdbc.query("SELECT id,name,kind FROM dbo.skill"+(kind==null?"":" WHERE kind=?")+" ORDER BY name,id",
            (rs,n)->new Skill(rs.getLong(1),rs.getString(2),SkillKind.valueOf(rs.getString(3))),kind==null?new Object[]{}:new Object[]{kind.name()});
    }
    @Transactional(readOnly=true)
    public List<RoleInfo> roles() {
        manager(); return jdbc.query("SELECT role_code,display_name FROM dbo.role ORDER BY role_code",(rs,n)-> {
            RoleCode role=RoleCode.valueOf(rs.getString(1));
            String scoping=role==RoleCode.HORSE_OWNER?"ROW_LEVEL_OWN":
                role==RoleCode.TRAINER||role==RoleCode.WORK_RIDER?"ASSIGNMENT_SCOPED":"ROLE_LEVEL";
            return new RoleInfo(role,rs.getString(2),scoping);
        });
    }
    /** Shared with CORE-047. Caller must already be the active Club Manager; no arbitrary actor impersonation. */
    @Transactional
    public void assignRole(long userId, RoleCode role, long actorId) {
        ActorContext actor=manager();
        if(actor.userId()!=actorId) throw new BusinessException(ErrorCode.FORBIDDEN,"Invalid assignment actor.");
        if(userId==actorId && role!=RoleCode.CLUB_MANAGER) throw conflict("You cannot remove your own Club Manager role.");
        List<Long> ids=jdbc.queryForList("SELECT id FROM dbo.role WHERE role_code=?",Long.class,role.name());
        if(ids.size()!=1) throw invalid("roleCode","The requested role is unavailable.");
        jdbc.update("DELETE FROM dbo.user_role WHERE user_id=?",userId);
        jdbc.update("INSERT INTO dbo.user_role(user_id,role_id,assigned_by_user_id,assigned_at) VALUES(?,?,?,?)",userId,ids.getFirst(),actorId,now());
        audit(actorId,"ROLE_ASSIGNED",userId);
    }
    private void validateSkills(UserInput input) {
        List<Long> ids=input.skillIds()==null?List.of():input.skillIds();
        if(new HashSet<>(ids).size()!=ids.size()) throw invalid("skillIds","Duplicate skills are not allowed.");
        String kind=input.roleCode()==RoleCode.TRAINER?"TRAINER_SPECIALIZATION":input.roleCode()==RoleCode.WORK_RIDER?"RIDER_SKILL":null;
        for(Long id:ids) {
            List<String> kinds=jdbc.queryForList("SELECT kind FROM dbo.skill WHERE id=?",String.class,id);
            if(kind==null||kinds.size()!=1||!kind.equals(kinds.getFirst())) throw invalid("skillIds","A skill is unavailable or incompatible with the role.");
        }
    }
    private void replaceSkills(long id,List<Long> ids) {
        jdbc.update("DELETE FROM dbo.user_skill WHERE user_id=?",id);
        if(ids!=null) for(Long skill:ids) jdbc.update("INSERT INTO dbo.user_skill(user_id,skill_id) VALUES(?,?)",id,skill);
    }
    private Profile profile(long id) {
        var current=auth.findCurrentUser(id).orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND,"User not found."));
        LocalDateTime last=jdbc.queryForObject("SELECT last_login_at FROM dbo.app_user WHERE id=?",LocalDateTime.class,id);
        return new Profile(id,current.userCode(),current.fullName(),current.email(),current.roles(),current.accountStatus(),instant(last));
    }
    private UserDetail user(long id) {
        Profile p=profile(id);
        return jdbc.queryForObject("SELECT experience_years,experience_level,created_at,updated_at FROM dbo.app_user WHERE id=?",(rs,n)->
            new UserDetail(id,p.userCode(),p.fullName(),p.email(),p.roles(),p.accountStatus(),p.lastLoginAt(),
                rs.getObject("experience_years",Integer.class),rs.getString("experience_level"),
                jdbc.query("SELECT s.id,s.name,s.kind FROM dbo.user_skill us JOIN dbo.skill s ON s.id=us.skill_id WHERE us.user_id=? ORDER BY s.name,s.id",
                    (skill,index)->new Skill(skill.getLong(1),skill.getString(2),SkillKind.valueOf(skill.getString(3))),id),
                instant(rs.getObject("created_at",LocalDateTime.class)),instant(rs.getObject("updated_at",LocalDateTime.class))),id);
    }
    ActorContext activeActor() {
        ActorContext actor=actors.currentActor().orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED,"Authentication is required."));
        CurrentUser current=auth.findCurrentUser(actor.userId()).orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED,"Authentication is required."));
        if(current.accountStatus()!=AccountStatus.ACTIVE) throw new BusinessException(ErrorCode.FORBIDDEN,"Your account is deactivated.");
        return new ActorContext(current.id(),current.roles());
    }
    private ActorContext manager() {
        ActorContext actor=activeActor();
        if(!actor.roles().contains(RoleCode.CLUB_MANAGER)) throw new BusinessException(ErrorCode.FORBIDDEN,"Club Manager access is required.");
        return actor;
    }
    Credentials credentials(long id) {
        var rows=jdbc.query("SELECT id,email,password_hash,updated_at,account_status FROM dbo.app_user WHERE id=?",(rs,n)->
            new Credentials(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getObject(4,LocalDateTime.class),rs.getString(5)),id);
        if(rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"User not found.");
        return rows.getFirst();
    }
    void replacePassword(Credentials before,String hash,String action) {
        int changed=jdbc.update("UPDATE dbo.app_user SET password_hash=?,updated_at=? WHERE id=? AND account_status='ACTIVE' AND ((password_hash IS NULL AND ? IS NULL) OR password_hash=?) AND ((updated_at IS NULL AND ? IS NULL) OR updated_at=?)",
            hash,now(),before.id,before.hash,before.hash,before.updated,before.updated);
        if(changed!=1) {
            if ("PASSWORD_CHANGED".equals(action))
                throw invalid("currentPassword","Your account changed during this request. Reload and try again.");
            throw invalid("token","The reset link is invalid or has expired.");
        }
        audit(before.id,action,before.id);
    }
    private void audit(long actor,String action,long target) {
        jdbc.update("INSERT INTO dbo.audit_log(actor_user_id,actor_role,action,target_type,target_id,safe_summary,source) VALUES(?,NULL,?,'USER',?,'Account change.','WEB')",actor,action,target);
    }
    static String normalize(String email) { return Normalizer.normalize(email.strip(),Normalizer.Form.NFC).toLowerCase(Locale.ROOT); }
    /** BCrypt hash of a Club Manager-set password, or null when none was given (DEC-012). */
    private String managerPassword(UserInput input,String email) {
        if(input.password()==null||input.password().isEmpty()) return null;
        try { validatePassword(input.password(),input.confirmPassword(),email); }
        catch(BusinessException ex) {
            String field="newPassword".equals(ex.details().isEmpty()?null:ex.details().getFirst().field())?"password":"confirmPassword";
            throw invalid(field,ex.getMessage());
        }
        return passwords.encode(input.password());
    }
    static void validatePassword(String value,String confirmation,String email) {
        if(value==null || value.length()<8 || value.length()>64 || value.getBytes(StandardCharsets.UTF_8).length>72 ||
            !value.codePoints().anyMatch(Character::isLetter)||!value.codePoints().anyMatch(Character::isDigit)||value.equalsIgnoreCase(email))
            throw invalid("newPassword","Use 8–64 characters, a letter and a digit, at most 72 UTF-8 bytes, and not your email.");
        if(!value.equals(confirmation)) throw invalid("confirmPassword","Passwords must match.");
    }
    static BusinessException invalid(String field,String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR,message,List.of(new ErrorDetail(field,message))); }
    private static BusinessException conflict(String message) { return new BusinessException(ErrorCode.CONFLICT,message); }
    private static BusinessException duplicateEmail() { return conflict("A user with this email already exists."); }
    // DB-01 DATETIME2 stores UTC wall-clock values; JDBC mapping must not depend on the host timezone.
    private static LocalDateTime now() { return LocalDateTime.ofInstant(Instant.now(),ZoneOffset.UTC); }
    private static Instant instant(LocalDateTime timestamp) { return timestamp==null?null:timestamp.toInstant(ZoneOffset.UTC); }
    static final class Credentials {
        final long id;final String email,hash,status;final LocalDateTime updated;
        Credentials(long id,String email,String hash,LocalDateTime updated,String status) {this.id=id;this.email=email;this.hash=hash;this.updated=updated;this.status=status;}
        @Override public String toString() { return "Credentials[REDACTED]"; }
    }
}
