package com.racehorse.management.audit;

import com.racehorse.auth.*;
import com.racehorse.common.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** CORE-051 read model; never reads credentials, clinical tables or arbitrary stored payloads. */
@Service
public class AuditQueryService {
    private final JdbcTemplate jdbc;
    private final AuthRepository auth;
    public AuditQueryService(JdbcTemplate jdbc,AuthRepository auth){this.jdbc=jdbc;this.auth=auth;}
    @Transactional(readOnly=true)
    public Page list(long reader,Long actor,String action,String targetType,Instant from,Instant to,int page,int size){
        CurrentUser user=auth.findCurrentUser(reader).orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED,"Authentication required."));
        if(user.accountStatus()!=AccountStatus.ACTIVE || !user.roles().contains(RoleCode.CLUB_MANAGER))
            throw new BusinessException(ErrorCode.FORBIDDEN,"An active Club Manager is required.");
        if(page<0 || size<1 || size>100 || actor!=null&&actor<1 || action!=null&&action.length()>100
                || targetType!=null&&targetType.length()>50 || from!=null&&to!=null&&from.isAfter(to))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,"Invalid audit filters or pagination.");
        StringBuilder where=new StringBuilder(" WHERE 1=1"); List<Object> args=new ArrayList<>();
        if(actor!=null){where.append(" AND a.actor_user_id=?");args.add(actor);}
        if(action!=null){where.append(" AND a.action=?");args.add(action);}
        if(targetType!=null){where.append(" AND a.target_type=?");args.add(targetType);}
        if(from!=null){where.append(" AND a.occurred_at>=?");args.add(LocalDateTime.ofInstant(from,ZoneOffset.UTC));}
        if(to!=null){where.append(" AND a.occurred_at<=?");args.add(LocalDateTime.ofInstant(to,ZoneOffset.UTC));}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log a"+where,Long.class,args.toArray());
        args.add((long)page*size);args.add(size);
        var rows=jdbc.query("""
            SELECT a.id,a.occurred_at,a.actor_role,a.action,a.target_type,a.target_id,a.source,
                   u.id AS user_id,u.user_code,u.full_name
            FROM dbo.audit_log a LEFT JOIN dbo.app_user u ON u.id=a.actor_user_id
            """+where+" ORDER BY a.occurred_at DESC,a.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",(rs,n)->{
                Long userId=rs.getObject("user_id",Long.class);
                // Q-15 is not finalized. Do not expose unverified free-text summaries from legacy/domain writers.
                return new Entry(rs.getLong("id"),rs.getObject("occurred_at",LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        userId==null?null:new Actor(userId,rs.getString("user_code"),rs.getString("full_name")),
                        rs.getString("actor_role"),rs.getString("action"),rs.getString("target_type"),
                        rs.getObject("target_id",Long.class),null,rs.getString("source"));
            },args.toArray());
        return new Page(page,size,total,(total+size-1)/size,rows);
    }
    public record Actor(long id,String userCode,String fullName){}
    public record Entry(long id,Instant occurredAt,Actor actor,String actorRole,String action,String targetType,
                        Long targetId,String safeSummary,String source){}
    public record Page(int page,int size,long totalItems,long totalPages,List<Entry> items){}
}
