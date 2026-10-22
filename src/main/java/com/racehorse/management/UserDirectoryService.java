package com.racehorse.management;

import com.racehorse.auth.AccountStatus;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class UserDirectoryService {
    private final JdbcTemplate jdbc;
    public UserDirectoryService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(readOnly=true)
    public Page list(String query, RoleCode role, AccountStatus status, int page, int size) {
        if(page<0||size<1||size>100||query!=null&&query.length()>100)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,"Invalid user directory filters or pagination.");
        StringBuilder where=new StringBuilder(" WHERE 1=1");
        List<Object> args=new ArrayList<>();
        if(status!=null) { where.append(" AND u.account_status=?");args.add(status.name()); }
        if(role!=null) {
            where.append(" AND EXISTS(SELECT 1 FROM dbo.user_role ur JOIN dbo.role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code=?)");
            args.add(role.name());
        }
        if(query!=null&&!query.isBlank()) {
            where.append(" AND (LOWER(u.full_name) LIKE ? OR LOWER(u.user_code) LIKE ? OR LOWER(u.email) LIKE ?)");
            String pattern="%"+query.strip().toLowerCase(Locale.ROOT)+"%";args.add(pattern);args.add(pattern);args.add(pattern);
        }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.app_user u"+where,Long.class,args.toArray());
        List<Object> pageArgs=new ArrayList<>(args);pageArgs.add((long)page*size);pageArgs.add(size);
        List<UserRow> rows=jdbc.query("SELECT u.id,u.user_code,u.full_name,u.email,u.account_status,u.last_login_at FROM dbo.app_user u"+where+
                " ORDER BY u.full_name,u.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",(rs,n)-> {
            var last=rs.getTimestamp("last_login_at");
            return new UserRow(rs.getLong("id"),rs.getString("user_code"),rs.getString("full_name"),rs.getString("email"),
                    AccountStatus.valueOf(rs.getString("account_status")),last==null?null:last.toInstant());
        },pageArgs.toArray());
        Map<Long,List<RoleCode>> roles=new HashMap<>();
        if(!rows.isEmpty()) {
            String placeholders=String.join(",",Collections.nCopies(rows.size(),"?"));
            jdbc.query("SELECT ur.user_id,r.role_code FROM dbo.user_role ur JOIN dbo.role r ON r.id=ur.role_id WHERE ur.user_id IN ("+placeholders+") ORDER BY r.role_code",
                    rs->{roles.computeIfAbsent(rs.getLong("user_id"),id->new ArrayList<>()).add(RoleCode.valueOf(rs.getString("role_code")));},rows.stream().map(UserRow::id).toArray());
        }
        return new Page(rows.stream().map(u->new UserSummary(u.id(),u.userCode(),u.fullName(),u.email(),
                List.copyOf(roles.getOrDefault(u.id(),List.of())),u.accountStatus(),u.lastLoginAt())).toList(),page,size,total,
                total==0?0:(int)((total+size-1)/size));
    }
    private record UserRow(long id,String userCode,String fullName,String email,AccountStatus accountStatus,Instant lastLoginAt) {}
    public record UserSummary(long id,String userCode,String fullName,String email,List<RoleCode> roles,AccountStatus accountStatus,Instant lastLoginAt) {}
    public record Page(List<UserSummary> items,int page,int size,long totalItems,int totalPages) {}
}
