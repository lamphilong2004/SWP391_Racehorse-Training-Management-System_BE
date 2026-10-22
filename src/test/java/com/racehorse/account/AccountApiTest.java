package com.racehorse.account;

import com.racehorse.TestJwtProperties;
import com.racehorse.auth.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import javax.crypto.SecretKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:core049;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
    "spring.sql.init.schema-locations=classpath:schema.sql,classpath:account-schema.sql"})
@ActiveProfiles("test")
@Transactional
class AccountApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountService accounts;
    @Autowired SecretKey key;
    @MockitoBean DevPasswordOutbox outbox;
    MockMvc mvc;
    final ObjectMapper json=new ObjectMapper();
    final AtomicReference<String> deliveredToken=new AtomicReference<>();
    String manager,owner;
    @DynamicPropertySource static void jwt(DynamicPropertyRegistry r) {TestJwtProperties.register(r);}
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for(RoleCode role:RoleCode.values()) jdbc.update("MERGE INTO dbo.role(id,role_code,display_name) KEY(id) VALUES(?,?,?)",role.ordinal()+1,role.name(),role.name());
        manager=add(10000,RoleCode.CLUB_MANAGER,"manager@example.test","ACTIVE",encoder.encode("Initial123"));
        owner=add(10001,RoleCode.HORSE_OWNER,"owner@example.test","ACTIVE",encoder.encode("Initial123"));
        jdbc.update("INSERT INTO dbo.skill(id,name,kind) VALUES(10000,'Endurance','TRAINER_SPECIALIZATION'),(10001,'Track riding','RIDER_SKILL')");
        when(outbox.enabled()).thenReturn(true);deliveredToken.set(null);
        doAnswer(call->{deliveredToken.set(call.getArgument(1));return null;}).when(outbox).deliver(anyString(),anyString());
    }
    String add(long id,RoleCode role,String email,String status,String hash) {
        jdbc.update("INSERT INTO dbo.app_user(id,user_code,full_name,email,password_hash,account_status) VALUES(?,?,?,?,?,?)",id,"U-"+id,"Fixture user",email,hash,status);
        jdbc.update("INSERT INTO dbo.user_role(id,user_id,role_id) VALUES(?,?,?)",id,id,role.ordinal()+1);
        return tokens.issue(id,List.of(role)).value();
    }
    @Test void ownProfileAndOnlyNameEditable() throws Exception {
        mvc.perform(get("/api/account").header("Authorization","Bearer "+owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(10001)).andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        mvc.perform(put("/api/account").header("Authorization","Bearer "+owner).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"Updated name\",\"email\":\"intruder@example.test\",\"roles\":[\"CLUB_MANAGER\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Updated name"))
            .andExpect(jsonPath("$.data.email").value("owner@example.test")).andExpect(jsonPath("$.data.roles[0]").value("HORSE_OWNER"));
    }
    @Test void accountRequiresActiveAuthenticationAndAdminRequiresManager() throws Exception {
        mvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
        for(RoleCode role:RoleCode.values()) if(role!=RoleCode.CLUB_MANAGER) {
            String token=role==RoleCode.HORSE_OWNER?owner:add(11000+role.ordinal(),role,"role"+role.ordinal()+"@example.test","ACTIVE",null);
            mvc.perform(get("/api/admin/roles").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/users").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(userJson("x@example.test","TRAINER","[]"))).andExpect(status().isForbidden());
        }
        jdbc.update("UPDATE dbo.app_user SET account_status='INACTIVE' WHERE id=10001");
        mvc.perform(get("/api/account").header("Authorization","Bearer "+owner)).andExpect(status().isForbidden());
    }
    @Test void createNullHashAssignsSingleRoleAndNeverAcceptsManagerPassword() throws Exception {
        String body=mvc.perform(post("/api/admin/users").header("Authorization","Bearer "+manager).contentType(MediaType.APPLICATION_JSON)
            .content(userJson("New@Example.test","TRAINER","[10000]").replace("}",",\"password\":\"Injected123\"}")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.roles.length()").value(1))
            .andExpect(jsonPath("$.data.skills[0].id").value(10000)).andExpect(content().string(not(containsString("password"))))
            .andReturn().getResponse().getContentAsString();
        long id=json.readTree(body).path("data").path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM dbo.app_user WHERE id=?",String.class,id)).isNull();
        mvc.perform(get("/api/admin/users/"+id).header("Authorization","Bearer "+manager)).andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value("new@example.test"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE target_id=? AND action IN ('USER_CREATED','ROLE_ASSIGNED')",Integer.class,id)).isEqualTo(2);
    }
    @Test void updateReplacesRoleAndSkillsWithoutChangingPassword() throws Exception {
        String before=jdbc.queryForObject("SELECT password_hash FROM dbo.app_user WHERE id=10001",String.class);
        mvc.perform(put("/api/admin/users/10001").header("Authorization","Bearer "+manager).contentType(MediaType.APPLICATION_JSON).content(userJson("owner@example.test","WORK_RIDER","[10001]")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.roles[0]").value("WORK_RIDER")).andExpect(jsonPath("$.data.skills[0].kind").value("RIDER_SKILL"));
        assertThat(jdbc.queryForObject("SELECT password_hash FROM dbo.app_user WHERE id=10001",String.class)).isEqualTo(before);
        mvc.perform(get("/api/admin/skills").param("kind","RIDER_SKILL").header("Authorization","Bearer "+manager)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }
    @Test void duplicateEmailMissingUserAndIncompatibleSkillsAreRejected() throws Exception {
        mvc.perform(post("/api/admin/users").header("Authorization","Bearer "+manager).contentType(MediaType.APPLICATION_JSON).content(userJson("OWNER@example.test","GROOM","[]"))).andExpect(status().isConflict());
        mvc.perform(get("/api/admin/users/999999").header("Authorization","Bearer "+manager)).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/users").header("Authorization","Bearer "+manager).contentType(MediaType.APPLICATION_JSON).content(userJson("new@example.test","TRAINER","[10001]"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/users").header("Authorization","Bearer "+manager).contentType(MediaType.APPLICATION_JSON).content(userJson("new@example.test","GROOM","[10001]"))).andExpect(status().isBadRequest());
    }
    @Test void selfDeactivationAndSelfRoleRemovalAreConflicts() throws Exception {
        mvc.perform(patch("/api/admin/users/10000/status").header("Authorization","Bearer "+manager).contentType(MediaType.APPLICATION_JSON).content("{\"accountStatus\":\"INACTIVE\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/users/10000").header("Authorization","Bearer "+manager).contentType(MediaType.APPLICATION_JSON).content(userJson("manager@example.test","GROOM","[]"))).andExpect(status().isConflict());
    }
    @Test void statusChangesPersistAndAreAudited() throws Exception {
        mvc.perform(patch("/api/admin/users/10001/status").header("Authorization","Bearer "+manager).contentType(MediaType.APPLICATION_JSON).content("{\"accountStatus\":\"INACTIVE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.accountStatus").value("INACTIVE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE action='USER_DEACTIVATED' AND target_id=10001",Integer.class)).isEqualTo(1);
    }
    @Test void sevenRolesExposeApprovedScoping() throws Exception {
        mvc.perform(get("/api/admin/roles").header("Authorization","Bearer "+manager)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(7))
            .andExpect(jsonPath("$.data[?(@.roleCode=='HORSE_OWNER')].scoping",hasItem("ROW_LEVEL_OWN")))
            .andExpect(jsonPath("$.data[?(@.roleCode=='TRAINER')].scoping",hasItem("ASSIGNMENT_SCOPED")));
    }
    @Test void passwordChangeNeedsCurrentPasswordAndValidatesConfirmation() throws Exception {
        mvc.perform(put("/api/account/password").header("Authorization","Bearer "+owner).contentType(MediaType.APPLICATION_JSON).content(passwordJson("wrong","NewPassword123","NewPassword123")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("currentPassword"));
        mvc.perform(put("/api/account/password").header("Authorization","Bearer "+owner).contentType(MediaType.APPLICATION_JSON).content(passwordJson("Initial123","NewPassword123","different"))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/account/password").header("Authorization","Bearer "+owner).contentType(MediaType.APPLICATION_JSON).content(passwordJson("Initial123","NewPassword123","NewPassword123"))).andExpect(status().isNoContent());
        assertThat(encoder.matches("NewPassword123",jdbc.queryForObject("SELECT password_hash FROM dbo.app_user WHERE id=10001",String.class))).isTrue();
    }
    @Test void forgotIsNeutralAndDoesNotDiscloseOrDeliverForUnknownOrInactive() throws Exception {
        PasswordResetService reset=freshReset(Clock.systemUTC());
        reset.forgot(new AccountModels.Forgot("missing@example.test"),"192.0.2.11");
        jdbc.update("UPDATE dbo.app_user SET account_status='INACTIVE' WHERE id=10001");
        reset.forgot(new AccountModels.Forgot("owner@example.test"),"192.0.2.11");
        verify(outbox,never()).deliver(anyString(),anyString());
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"missing-rest@example.test\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.message").value("If an account exists for this email, a reset link has been sent."))
            .andExpect(content().string(not(containsString("token"))));
    }
    @Test void failedPrivateOutboxStillReturnsTheIdenticalNeutralResponse() throws Exception {
        doThrow(new IllegalStateException("PRIVATE_DELIVERY_DIAGNOSTIC"))
            .when(outbox).deliver(anyString(),anyString());
        String active=mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"owner@example.test\"}"))
            .andExpect(status().isAccepted()).andExpect(content().string(not(containsString("PRIVATE_DELIVERY_DIAGNOSTIC"))))
            .andReturn().getResponse().getContentAsString();
        String unknown=mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"missing-outbox@example.test\"}"))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(active).isEqualTo(unknown).doesNotContain("token","password_hash");
        verify(outbox,times(1)).deliver(anyString(),anyString());
    }
    @Test void activeNullHashFirstPasswordTokenIsSingleUseAndDoesNotChangeRoleStatus() {
        add(10002,RoleCode.GROOM,"first@example.test","ACTIVE",null);
        PasswordResetService reset=freshReset(Clock.systemUTC());
        reset.forgot(new AccountModels.Forgot("first@example.test"),"192.0.2.12");
        String token=deliveredToken.get();assertThat(token).isNotBlank();
        reset.reset(new AccountModels.Reset(token,"FirstPassword123","FirstPassword123"));
        assertThat(encoder.matches("FirstPassword123",jdbc.queryForObject("SELECT password_hash FROM dbo.app_user WHERE id=10002",String.class))).isTrue();
        assertThatThrownBy(()->reset.reset(new AccountModels.Reset(token,"SecondPassword123","SecondPassword123"))).isInstanceOf(com.racehorse.common.error.BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT account_status FROM dbo.app_user WHERE id=10002",String.class)).isEqualTo("ACTIVE");
    }
    @Test void resetRejectsExpiredTamperedAndMalformedTokens() {
        Instant issued=Instant.now().minusSeconds(1900);
        PasswordResetService old=freshReset(Clock.fixed(issued,ZoneOffset.UTC));
        old.forgot(new AccountModels.Forgot("owner@example.test"),"192.0.2.13");String token=deliveredToken.get();
        PasswordResetService current=freshReset(Clock.systemUTC());
        for(String invalid:List.of(token,token+"x","not-a-reset-token"))
            assertThatThrownBy(()->current.reset(new AccountModels.Reset(invalid,"NewPassword123","NewPassword123"))).isInstanceOf(com.racehorse.common.error.BusinessException.class);
    }
    @Test void passwordPolicyIncludesUtf8ByteLimitAndEmailExclusion() {
        for(String invalid:List.of("short1","NoDigitsHere","123456789","owner@example.test","漢".repeat(30)+"1"))
            assertThatThrownBy(()->AccountService.validatePassword(invalid,invalid,"owner@example.test")).isInstanceOf(com.racehorse.common.error.BusinessException.class);
    }
    @Test void forgotLimitsBothEmailAndClientIpWithoutExistenceDisclosure() {
        PasswordResetService reset=freshReset(Clock.systemUTC());
        for(int n=0;n<3;n++) reset.forgot(new AccountModels.Forgot("unknown@example.test"),"192.0.2.14");
        assertThatThrownBy(()->reset.forgot(new AccountModels.Forgot("unknown@example.test"),"192.0.2.15")).isInstanceOf(com.racehorse.common.error.BusinessException.class);
        PasswordResetService perIp=freshReset(Clock.systemUTC());
        for(int n=0;n<10;n++) perIp.forgot(new AccountModels.Forgot("missing"+n+"@example.test"),"192.0.2.16");
        assertThatThrownBy(()->perIp.forgot(new AccountModels.Forgot("another@example.test"),"192.0.2.16")).isInstanceOf(com.racehorse.common.error.BusinessException.class);
    }
    @Test void disabledOutboxNeverIssuesTokensAndSensitiveDtosRedactToString() {
        when(outbox.enabled()).thenReturn(false);
        freshReset(Clock.systemUTC()).forgot(new AccountModels.Forgot("owner@example.test"),"192.0.2.17");
        verify(outbox,never()).deliver(anyString(),anyString());
        assertThat(new AccountModels.Reset("sensitive-token","Sensitive123","Sensitive123").toString()).doesNotContain("sensitive-token","Sensitive123");
        assertThat(new AccountModels.PasswordChange("Old123456","Sensitive123","Sensitive123").toString()).doesNotContain("Sensitive123","Old123456");
    }
    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("java.util.TimeZone.default")
    void resetFingerprintAndDatabaseTimestampRemainUtcAcrossHostTimezoneChanges() {
        TimeZone original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            jdbc.update("UPDATE dbo.app_user SET updated_at=? WHERE id=10001",LocalDateTime.of(2026,10,21,10,20,30));
            PasswordResetService reset=freshReset(Clock.systemUTC());
            reset.forgot(new AccountModels.Forgot("owner@example.test"),"192.0.2.18");
            String token=deliveredToken.get();
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            LocalDateTime earliest=LocalDateTime.ofInstant(Instant.now().minusSeconds(1),ZoneOffset.UTC);
            reset.reset(new AccountModels.Reset(token,"UtcPassword123","UtcPassword123"));
            LocalDateTime persisted=jdbc.queryForObject("SELECT updated_at FROM dbo.app_user WHERE id=10001",LocalDateTime.class);
            assertThat(persisted).isBetween(earliest,LocalDateTime.ofInstant(Instant.now().plusSeconds(1),ZoneOffset.UTC));
            assertThat(encoder.matches("UtcPassword123",jdbc.queryForObject("SELECT password_hash FROM dbo.app_user WHERE id=10001",String.class))).isTrue();
        } finally {TimeZone.setDefault(original);}
    }
    @Test void secretRequestFieldsAreWriteOnly() throws Exception {
        var change=json.readValue(passwordJson("Old123456","Sensitive123","Sensitive123"),AccountModels.PasswordChange.class);
        assertThat(change.currentPassword()).isEqualTo("Old123456");
        assertThat(change.newPassword()).isEqualTo("Sensitive123");
        assertThat(json.readTree(json.writeValueAsString(change)).size()).isZero();
        var reset=json.readValue("{\"token\":\"private-token\",\"newPassword\":\"Sensitive123\",\"confirmPassword\":\"Sensitive123\"}",AccountModels.Reset.class);
        assertThat(reset.token()).isEqualTo("private-token");
        assertThat(json.readTree(json.writeValueAsString(reset)).size()).isZero();
    }
    @Test void stalePasswordChangeReportsCurrentPasswordRatherThanResetToken() {
        var before=accounts.credentials(10001);
        jdbc.update("UPDATE dbo.app_user SET password_hash=? WHERE id=10001",encoder.encode("Changed123"));
        assertThatThrownBy(()->accounts.replacePassword(before,encoder.encode("Other12345"),"PASSWORD_CHANGED"))
            .isInstanceOf(com.racehorse.common.error.BusinessException.class)
            .hasMessage("Your account changed during this request. Reload and try again.");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE action='PASSWORD_CHANGED'",Integer.class)).isZero();
    }
    PasswordResetService freshReset(Clock clock) {return new PasswordResetService(jdbc,accounts,encoder,key,outbox,clock);}
    static String userJson(String email,String role,String skills) {return "{\"fullName\":\"New user\",\"email\":\""+email+"\",\"roleCode\":\""+role+"\",\"skillIds\":"+skills+"}";}
    static String passwordJson(String current,String value,String confirmation) {return "{\"currentPassword\":\""+current+"\",\"newPassword\":\""+value+"\",\"confirmPassword\":\""+confirmation+"\"}";}
}
