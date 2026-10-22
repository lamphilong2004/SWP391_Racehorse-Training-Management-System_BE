package com.racehorse.management;

import com.racehorse.TestJwtProperties;
import com.racehorse.auth.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:owner_directory;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class UserDirectoryApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    MockMvc mvc;
    @DynamicPropertySource static void jwt(DynamicPropertyRegistry r){TestJwtProperties.register(r);}
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    String user(long id,RoleCode role,String status) {
        jdbc.update("INSERT INTO dbo.app_user(id,user_code,full_name,email,password_hash,account_status) VALUES(?,?,?,?,?,?)",
                id,"U"+id,"User "+id,"user"+id+"@example.test","private-fixture-hash",status);
        jdbc.update("MERGE INTO dbo.role KEY(id) VALUES(?,?)",role.ordinal()+1,role.name());
        jdbc.update("INSERT INTO dbo.user_role(id,user_id,role_id) VALUES(?,?,?)",id,id,role.ordinal()+1);
        return tokens.issue(id,List.of(role)).value();
    }
    @Test void ownerPickerFiltersPagesAndNeverReturnsCredentials() throws Exception {
        String manager=user(100,RoleCode.CLUB_MANAGER,"ACTIVE");
        user(201,RoleCode.HORSE_OWNER,"ACTIVE");user(202,RoleCode.HORSE_OWNER,"ACTIVE");
        user(203,RoleCode.HORSE_OWNER,"INACTIVE");user(204,RoleCode.TRAINER,"ACTIVE");
        mvc.perform(get("/api/admin/users").param("role","HORSE_OWNER").param("status","ACTIVE").param("size","1").header("Authorization","Bearer "+manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(201))
                .andExpect(jsonPath("$.data.items[0].roles[0]").value("HORSE_OWNER"))
                .andExpect(jsonPath("$.data.totalItems").value(2)).andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(content().string(not(containsString("password")))).andExpect(content().string(not(containsString("private-fixture-hash"))));
        mvc.perform(get("/api/admin/users").param("role","HORSE_OWNER").param("status","ACTIVE").param("q","USER202@EXAMPLE.TEST").header("Authorization","Bearer "+manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(202)).andExpect(jsonPath("$.data.totalItems").value(1));
        mvc.perform(get("/api/admin/users").param("size","101").header("Authorization","Bearer "+manager)).andExpect(status().isBadRequest());
    }
    @Test void onlyManagerMayReadDirectory() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        for(RoleCode role:RoleCode.values()) if(role!=RoleCode.CLUB_MANAGER) {
            String token=user(300+role.ordinal(),role,"ACTIVE");
            mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        }
    }
}
