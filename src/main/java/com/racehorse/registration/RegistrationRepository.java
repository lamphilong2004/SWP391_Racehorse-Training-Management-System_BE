package com.racehorse.registration;

import com.racehorse.auth.RoleCode;
import com.racehorse.common.jdbc.GeneratedIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import static com.racehorse.registration.RegistrationDtos.*;

@Repository
public class RegistrationRepository {
    private final JdbcTemplate jdbc;
    public RegistrationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String SELECT = """
        SELECT rr.id, rr.full_name, rr.email, rr.status, rr.requested_at, rr.reviewed_at,
               rr.reviewed_by_user_id, rr.created_user_id, rr.decision_reason,
               reviewer.user_code AS reviewer_code, reviewer.full_name AS reviewer_name, r.role_code
        FROM dbo.registration_request rr
        LEFT JOIN dbo.app_user reviewer ON reviewer.id=rr.reviewed_by_user_id
        LEFT JOIN dbo.role r ON r.id=rr.approved_role_id
        """;
    public Page<Request> list(Status status, int page, int size) {
        String where = status == null ? "" : " WHERE rr.status=?";
        List<Object> args = new ArrayList<>(); if (status != null) args.add(status.name());
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.registration_request rr" + where, Long.class, args.toArray());
        args.add((long)page * size); args.add(size);
        var items = jdbc.query(SELECT + where + " ORDER BY rr.requested_at DESC, rr.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs,n) -> map(rs), args.toArray());
        return new Page<>(page,size,total,(total + size - 1)/size,items);
    }
    public Optional<Request> find(long id) {
        return jdbc.query(SELECT + " WHERE rr.id=?", (rs,n) -> map(rs), id).stream().findFirst();
    }
    public Optional<Pending> pending(long id) {
        return jdbc.query("SELECT id,email,password_hash FROM dbo.registration_request WHERE id=? AND status='PENDING_APPROVAL'",
                (rs,n)->new Pending(rs.getLong("id"),rs.getString("email"),rs.getString("password_hash")),id).stream().findFirst();
    }
    public boolean accountExists(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.app_user WHERE email=?",Long.class,email)>0;
    }
    public boolean canRegister(String email, Instant now) {
        if (accountExists(email)) return false;
        var rows=jdbc.query("SELECT status,reviewed_at FROM dbo.registration_request WHERE email_normalized=? ORDER BY requested_at DESC,id DESC",
                (rs,n)->new Prior(rs.getString("status"),instant(rs,"reviewed_at")),email);
        if(rows.stream().anyMatch(r->r.status.equals("PENDING_APPROVAL"))) return false;
        if(rows.stream().filter(r->r.status.equals("REJECTED")).count()>=3) return false;
        return rows.isEmpty() || !rows.getFirst().status.equals("REJECTED")
                || (rows.getFirst().reviewedAt!=null && !rows.getFirst().reviewedAt.plusSeconds(7*24*3600L).isAfter(now));
    }
    public void create(String name,String email,String hash,Instant now) {
        jdbc.update("INSERT INTO dbo.registration_request(full_name,email,email_normalized,password_hash,status,requested_at,created_at) VALUES(?,?,?,?,'PENDING_APPROVAL',?,?)",
                name,email,email,hash,utc(now),utc(now));
    }
    public long roleId(RoleCode role) {
        return jdbc.queryForObject("SELECT id FROM dbo.role WHERE role_code=?",Long.class,role.name());
    }
    public long createUser(Request request,String hash,Instant now) {
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(c->{
            PreparedStatement ps=c.prepareStatement("INSERT INTO dbo.app_user(user_code,full_name,email,password_hash,account_status,created_at) VALUES(?,?,?,?,'ACTIVE',?)",new String[]{"id"});
            ps.setString(1,"U-R"+UUID.randomUUID().toString().replace("-","").substring(0,16));
            ps.setString(2,request.fullName()); ps.setString(3,request.email()); ps.setString(4,hash);ps.setObject(5,utc(now));return ps;
        },keys);
        long id=GeneratedIdentity.requireId(keys);
        jdbc.update("UPDATE dbo.app_user SET user_code=? WHERE id=?",String.format(Locale.ROOT,"U-%03d",id),id);
        return id;
    }
    public void assign(long user,long role,long actor,Instant now) {
        jdbc.update("INSERT INTO dbo.user_role(user_id,role_id,assigned_by_user_id,assigned_at,created_at) VALUES(?,?,?,?,?)",
                user,role,actor,utc(now),utc(now));
    }
    public int approve(long id,long user,long role,long actor,Instant now) {
        return jdbc.update("""
            UPDATE dbo.registration_request SET status='APPROVED',password_hash=NULL,reviewed_by_user_id=?,
                reviewed_at=?,approved_role_id=?,created_user_id=?,updated_at=? WHERE id=? AND status='PENDING_APPROVAL'
            """,actor,utc(now),role,user,utc(now),id);
    }
    public int reject(long id,String reason,long actor,Instant now) {
        return jdbc.update("""
            UPDATE dbo.registration_request SET status='REJECTED',password_hash=NULL,reviewed_by_user_id=?,
                reviewed_at=?,decision_reason=?,updated_at=? WHERE id=? AND status='PENDING_APPROVAL'
            """,actor,utc(now),reason,utc(now),id);
    }
    public void audit(long actor,String action,String target,long id,String summary,Instant now) {
        jdbc.update("INSERT INTO dbo.audit_log(actor_user_id,actor_role,action,target_type,target_id,safe_summary,source,occurred_at) VALUES(?,'CLUB_MANAGER',?,?,?,?, 'WEB',?)",
                actor,action,target,id,summary,utc(now));
    }
    private Request map(ResultSet rs)throws SQLException {
        Long reviewer=rs.getObject("reviewed_by_user_id",Long.class);
        String role=rs.getString("role_code");
        return new Request(rs.getLong("id"),rs.getString("full_name"),rs.getString("email"),Status.valueOf(rs.getString("status")),
                instant(rs,"requested_at"),instant(rs,"reviewed_at"),reviewer==null?null:new UserRef(reviewer,rs.getString("reviewer_code"),rs.getString("reviewer_name")),
                role==null?null:RoleCode.valueOf(role),rs.getObject("created_user_id",Long.class),rs.getString("decision_reason"));
    }
    private static Instant instant(ResultSet rs,String column)throws SQLException {
        LocalDateTime t=rs.getObject(column,LocalDateTime.class);return t==null?null:t.toInstant(ZoneOffset.UTC);
    }
    private static LocalDateTime utc(Instant instant){return LocalDateTime.ofInstant(instant,ZoneOffset.UTC);}
    private record Prior(String status,Instant reviewedAt) { }
    public record Pending(long id,String email,String hash) { @Override public String toString(){return "Pending[id="+id+"]";} }
}
