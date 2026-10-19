package com.racehorse.horse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import java.sql.*;
import java.time.*;
import java.util.*;

/** SQL-backed projections only; no lifecycle transition or synthetic medical metrics. */
@Repository
public class HorseRepository {
    private final JdbcTemplate jdbc;
    public HorseRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    private static final String FROM="""
        FROM dbo.horse h
        LEFT JOIN dbo.horse sire ON sire.id=h.sire_horse_id
        LEFT JOIN dbo.horse dam ON dam.id=h.dam_horse_id
        LEFT JOIN dbo.medical_restriction mr ON mr.horse_id=h.id AND mr.lifted_at IS NULL
        LEFT JOIN dbo.horse_stable_assignment a ON a.horse_id=h.id AND a.effective_to IS NULL
        LEFT JOIN dbo.stable_box b ON b.id=a.stable_box_id
        LEFT JOIN dbo.app_user assigned ON assigned.id=a.assigned_by_user_id
        """;
    private static final String SELECT="""
        SELECT h.*, sire.horse_code AS sire_code,sire.name AS sire_display_name,
        dam.horse_code AS dam_code,dam.name AS dam_display_name,
        COALESCE(mr.restriction_level,'AVAILABLE') AS availability,
        b.box_code,b.yard,a.id AS assignment_id,a.stable_box_id,a.effective_from,a.effective_to,
        a.reason,a.assigned_by_user_id,assigned.user_code AS assigned_code,assigned.full_name AS assigned_name
        """+FROM;

    public long insert(HorseIdentityRequest v) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO dbo.horse(horse_code,name,breed,sex,foaled_date,country_of_origin,registration_no,lifecycle)
            VALUES(?,?,?,?,?,?,?,'DRAFT')
            """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, v.horseCode());
            statement.setString(2, v.name());
            statement.setString(3, v.breed());
            statement.setString(4, v.sex());
            statement.setObject(5, v.foaledDate());
            statement.setString(6, v.countryOfOrigin());
            statement.setString(7, v.registrationNo());
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }
    public void updatePedigree(long horseId, PedigreeRequest request) {
        jdbc.update("""
            UPDATE dbo.horse
            SET sire_horse_id=?, sire_name=?, dam_horse_id=?, dam_name=?, updated_at=?
            WHERE id=?
            """, request.sire().horseId(), normalized(request.sire().name()),
                request.dam().horseId(), normalized(request.dam().name()),
                LocalDateTime.now(Clock.systemUTC()), horseId);
    }
    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
    public boolean ownerIsActiveHorseOwner(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT CASE WHEN EXISTS (
                SELECT 1 FROM dbo.app_user u
                JOIN dbo.user_role ur ON ur.user_id=u.id
                JOIN dbo.role r ON r.id=ur.role_id
                WHERE u.id=? AND u.account_status='ACTIVE' AND r.role_code='HORSE_OWNER'
            ) THEN 1 ELSE 0 END
            """, Boolean.class, userId));
    }
    public long insertOwnership(long horseId, OwnershipRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO dbo.horse_ownership
                (horse_id, owner_user_id, share_percentage, effective_from, is_managing_owner)
            VALUES (?, ?, ?, ?, ?)
            """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, horseId);
            statement.setLong(2, request.ownerUserId());
            statement.setBigDecimal(3, request.sharePercentage());
            statement.setObject(4, request.effectiveFrom());
            statement.setBoolean(5, Boolean.TRUE.equals(request.isManagingOwner()));
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }
    private static long generatedId(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        Object id = keys == null ? null : keys.entrySet().stream()
                .filter(entry -> "id".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (!(id instanceof Number generatedId)) {
            throw new IllegalStateException("The database did not return a generated identity.");
        }
        return generatedId.longValue();
    }
    public List<Map<String, Object>> ownershipHistory(long horseId) {
        return jdbc.query("""
            SELECT o.*,u.user_code,u.full_name FROM dbo.horse_ownership o
            JOIN dbo.app_user u ON u.id=o.owner_user_id
            WHERE o.horse_id=?
            ORDER BY CASE WHEN o.effective_to IS NULL THEN 0 ELSE 1 END, o.effective_from DESC, o.id DESC
            """, OWNERSHIP_ROW, horseId);
    }
    public boolean exists(long id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT CASE WHEN EXISTS(SELECT 1 FROM dbo.horse WHERE id=?) THEN 1 ELSE 0 END",Boolean.class,id)); }
    private static String scope(Long trainer,List<Object> args) {
        if(trainer==null)return "";
        args.add(trainer);
        return " AND EXISTS (SELECT 1 FROM dbo.horse_training_program p JOIN dbo.trainer_assignment ta ON ta.horse_training_program_id=p.id WHERE p.horse_id=h.id AND ta.trainer_user_id=? AND ta.effective_to IS NULL)";
    }
    public Optional<Map<String,Object>> find(long id,Long trainer) {
        List<Object> args=new ArrayList<>();args.add(id);
        return jdbc.query(SELECT+" WHERE h.id=?"+scope(trainer,args),ROW,args.toArray()).stream().findFirst();
    }
    public record Page(List<Map<String,Object>> items,int page,int size,long totalItems,long totalPages) {}
    public Page list(String q,String lifecycle,String health,String availability,int page,int size,Long trainer) {
        List<Object> args=new ArrayList<>();StringBuilder where=new StringBuilder(" WHERE 1=1");
        where.append(scope(trainer,args));
        if(q!=null&&!q.isBlank()) {
            String escaped=q.strip().replace("\\","\\\\").replace("%","\\%").replace("_","\\_").replace("[","\\[");
            where.append(" AND (h.name LIKE ? ESCAPE '\\' OR h.horse_code LIKE ? ESCAPE '\\')");args.add("%"+escaped+"%");args.add("%"+escaped+"%");
        }
        if(lifecycle!=null&&!lifecycle.isBlank()){where.append(" AND h.lifecycle=?");args.add(lifecycle);}
        if(health!=null&&!health.isBlank()){where.append(" AND h.health_status=?");args.add(health);}
        if(availability!=null&&!availability.isBlank()){where.append(" AND COALESCE(mr.restriction_level,'AVAILABLE')=?");args.add(availability);}
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT_BIG(*) "+FROM+where,Long.class,args.toArray()));
        args.add((long)page*size);args.add(size);
        List<Map<String,Object>> rows=jdbc.query(SELECT+where+" ORDER BY h.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",ROW,args.toArray());
        return new Page(rows,page,size,total,total/size+(total%size==0?0:1));
    }
    public List<Map<String,Object>> ownerships(long id) {
        return jdbc.query("""
            SELECT o.*,u.user_code,u.full_name FROM dbo.horse_ownership o
            JOIN dbo.app_user u ON u.id=o.owner_user_id
            WHERE o.horse_id=? AND o.effective_to IS NULL ORDER BY o.id
            """, OWNERSHIP_ROW, id);
    }
    private static final RowMapper<Map<String,Object>> OWNERSHIP_ROW=(r,n)->{
        Map<String,Object> o=new LinkedHashMap<>();o.put("id",r.getLong("id"));
        o.put("owner",user(r.getLong("owner_user_id"),r.getString("user_code"),r.getString("full_name")));
        o.put("sharePercentage",r.getBigDecimal("share_percentage"));o.put("effectiveFrom",date(r,"effective_from"));
        o.put("effectiveTo",date(r,"effective_to"));o.put("isManagingOwner",r.getBoolean("is_managing_owner"));return o;
    };
    private static Map<String,Object> user(long id,String code,String name){Map<String,Object> u=new LinkedHashMap<>();u.put("id",id);u.put("userCode",code);u.put("fullName",name);return u;}
    private static Object date(ResultSet r,String field)throws SQLException {java.sql.Date d=r.getDate(field);return d==null?null:d.toLocalDate();}
    private static Object instant(ResultSet r,String field)throws SQLException {LocalDateTime t=r.getObject(field,LocalDateTime.class);return t==null?null:t.toInstant(ZoneOffset.UTC);}
    private static Map<String,Object> parent(ResultSet r,String prefix)throws SQLException {
        Map<String,Object> p=new LinkedHashMap<>();p.put("horseId",r.getObject(prefix+"_horse_id"));p.put("horseCode",r.getString(prefix+"_code"));
        p.put("name",r.getObject(prefix+"_horse_id")==null?r.getString(prefix+"_name"):r.getString(prefix+"_display_name"));return p;
    }
    private static final RowMapper<Map<String,Object>> ROW=(r,n)->{
        Map<String,Object> h=new LinkedHashMap<>();h.put("id",r.getLong("id"));h.put("horseCode",r.getString("horse_code"));h.put("name",r.getString("name"));
        h.put("breed",r.getString("breed"));h.put("sex",r.getString("sex"));h.put("foaledDate",date(r,"foaled_date"));h.put("countryOfOrigin",r.getString("country_of_origin"));
        h.put("registrationNo",r.getString("registration_no"));h.put("lifecycle",r.getString("lifecycle"));h.put("healthStatus",r.getString("health_status"));
        h.put("trainingAvailability",r.getString("availability"));h.put("activatedAt",instant(r,"activated_at"));h.put("createdAt",instant(r,"created_at"));h.put("updatedAt",instant(r,"updated_at"));
        h.put("currentBoxCode",r.getString("box_code"));h.put("pedigree",Map.of("sire",parent(r,"sire"),"dam",parent(r,"dam")));
        Map<String,Object> a=null;
        if(r.getObject("assignment_id")!=null){a=new LinkedHashMap<>();a.put("id",r.getLong("assignment_id"));a.put("stableBoxId",r.getLong("stable_box_id"));a.put("boxCode",r.getString("box_code"));a.put("yard",r.getString("yard"));a.put("effectiveFrom",date(r,"effective_from"));a.put("effectiveTo",date(r,"effective_to"));a.put("reason",r.getString("reason"));a.put("assignedBy",user(r.getLong("assigned_by_user_id"),r.getString("assigned_code"),r.getString("assigned_name")));}
        h.put("currentAssignment",a);return h;
    };
}
