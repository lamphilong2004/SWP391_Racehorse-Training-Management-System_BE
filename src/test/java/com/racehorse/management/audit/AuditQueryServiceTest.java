package com.racehorse.management.audit;

import com.racehorse.auth.AuthRepository;
import com.racehorse.common.error.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AuditQueryServiceTest {
    JdbcTemplate jdbc;AuditQueryService service;
    Instant date=Instant.parse("2026-10-21T08:00:00Z");
    @BeforeEach void setup(){
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:auditquery_"+UUID.randomUUID()+";MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE SCHEMA dbo");
        jdbc.execute("CREATE TABLE dbo.role(id BIGINT PRIMARY KEY,role_code VARCHAR(30))");
        jdbc.execute("CREATE TABLE dbo.app_user(id BIGINT PRIMARY KEY,user_code VARCHAR(20),full_name VARCHAR(150),email VARCHAR(254),account_status VARCHAR(20),password_hash VARCHAR(255))");
        jdbc.execute("CREATE TABLE dbo.user_role(user_id BIGINT,role_id BIGINT)");
        jdbc.execute("CREATE TABLE dbo.audit_log(id BIGINT PRIMARY KEY,occurred_at TIMESTAMP,actor_user_id BIGINT,actor_role VARCHAR(30),action VARCHAR(100),target_type VARCHAR(50),target_id BIGINT,safe_summary VARCHAR(1000),source VARCHAR(30))");
        jdbc.update("INSERT INTO dbo.role VALUES(1,'CLUB_MANAGER'),(2,'GROOM')");
        jdbc.update("INSERT INTO dbo.app_user VALUES(1,'U-001','Manager','cm@example.test','ACTIVE','DO_NOT_EXPOSE_HASH'),(2,'U-002','Groom','groom@example.test','ACTIVE',NULL)");
        jdbc.update("INSERT INTO dbo.user_role VALUES(1,1),(2,2)");
        jdbc.update("INSERT INTO dbo.audit_log VALUES(1,?,1,'CLUB_MANAGER','REGISTRATION_APPROVED','REGISTRATION_REQUEST',5,'UNVERIFIED_SECRET_OR_CLINICAL_TEXT','WEB')",java.time.LocalDateTime.ofInstant(date,java.time.ZoneOffset.UTC));
        jdbc.update("INSERT INTO dbo.audit_log VALUES(2,?,NULL,NULL,'SESSION_STARTED','TRAINING_SESSION',9,'Unverified legacy data','WEB')",java.time.LocalDateTime.ofInstant(date,java.time.ZoneOffset.UTC));
        jdbc.update("INSERT INTO dbo.audit_log VALUES(3,?,2,'GROOM','USER_LOGGED_OUT',NULL,NULL,'Logged out','WEB')",java.time.LocalDateTime.ofInstant(date.plusSeconds(1),java.time.ZoneOffset.UTC));
        service=new AuditQueryService(jdbc,new AuthRepository(jdbc));
    }
    @Test void stablePaginationNewestFirstAndSafeProjection(){
        var first=service.list(1,null,null,null,null,null,0,2);
        assertEquals(3,first.totalItems());assertEquals(2,first.totalPages());
        assertEquals(3,first.items().getFirst().id());assertEquals(2,first.items().get(1).id());
        assertNull(first.items().get(1).actor());
        assertTrue(first.items().stream().allMatch(e->e.safeSummary()==null));
        var second=service.list(1,null,null,null,null,null,1,2);
        assertEquals(1,second.items().getFirst().id());assertEquals("U-001",second.items().getFirst().actor().userCode());
        assertFalse(second.toString().contains("DO_NOT_EXPOSE_HASH"));assertFalse(second.toString().contains("UNVERIFIED_SECRET"));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log",Integer.class));
    }
    @Test void exactBoundFiltersAndInclusiveDates(){
        var result=service.list(1,1L,"REGISTRATION_APPROVED","REGISTRATION_REQUEST",date,date,0,20);
        assertEquals(1,result.totalItems());assertEquals(1,result.items().getFirst().id());
        assertEquals(0,service.list(1,null,"' OR 1=1 --",null,null,null,0,20).totalItems());
    }
    @Test void roleAndAccountStateEnforced(){
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->service.list(2,null,null,null,null,null,0,20)).code());
        jdbc.update("UPDATE dbo.app_user SET account_status='INACTIVE' WHERE id=1");
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->service.list(1,null,null,null,null,null,0,20)).code());
        assertEquals(ErrorCode.UNAUTHORIZED,assertThrows(BusinessException.class,()->service.list(999,null,null,null,null,null,0,20)).code());
    }
    @Test void malformedFiltersAndDatesRejected(){
        assertEquals(ErrorCode.VALIDATION_ERROR,assertThrows(BusinessException.class,()->service.list(1,null,null,null,date.plusSeconds(1),date,0,20)).code());
        assertEquals(ErrorCode.VALIDATION_ERROR,assertThrows(BusinessException.class,()->service.list(1,null,null,null,null,null,-1,20)).code());
        assertEquals(ErrorCode.VALIDATION_ERROR,assertThrows(BusinessException.class,()->service.list(1,null,"A".repeat(101),null,null,null,0,20)).code());
        assertEquals(ErrorCode.VALIDATION_ERROR,assertThrows(BusinessException.class,()->service.list(1,0L,null,null,null,null,0,20)).code());
        assertEquals(ErrorCode.VALIDATION_ERROR,assertThrows(BusinessException.class,()->service.list(1,null,null,null,null,null,0,101)).code());
    }
}
