package com.racehorse.horse.stable;

import com.racehorse.TestJwtProperties;
import com.racehorse.auth.RoleCode;
import com.racehorse.testsupport.TestAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DEC-016: fixed-capacity yards, stable map, maintenance and assignment validation. */
@SpringBootTest
@ActiveProfiles("test")
class StableLayoutApiTest {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JsonMapper json;
    private MockMvc mvc;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestJwtProperties.register(registry);
    }

    @org.junit.jupiter.api.AfterEach
    void cleanAfter() {
        clean();
    }

    @BeforeEach
    void clean() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("DELETE FROM dbo.horse_stable_assignment WHERE horse_id BETWEEN 7600 AND 7699");
        jdbc.update("DELETE FROM dbo.stable_box WHERE stable_yard_id IS NOT NULL OR box_code LIKE 'LEG-%'");
        jdbc.update("DELETE FROM dbo.stable_yard");
        jdbc.update("DELETE FROM dbo.horse WHERE id BETWEEN 7600 AND 7699");
        jdbc.update("DELETE FROM dbo.audit_log WHERE actor_user_id BETWEEN 7600 AND 7699");
        jdbc.update("DELETE FROM dbo.user_role WHERE user_id BETWEEN 7600 AND 7699");
        jdbc.update("DELETE FROM dbo.app_user WHERE id BETWEEN 7600 AND 7699");
    }

    @Test
    void managerCreatesAYardWhoseGridIsTheCapacityAndStaffSeeTheMap() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7601, RoleCode.CLUB_MANAGER).token();
        mvc.perform(post("/api/stable-yards").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yardCode\":\"x\",\"name\":\"Khu chuồng X\",\"rowCount\":2,\"columnCount\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalCapacity").value(6))
                .andExpect(jsonPath("$.data.free").value(6))
                .andExpect(jsonPath("$.data.yards[0].yardCode").value("X"))
                .andExpect(jsonPath("$.data.yards[0].boxes.length()").value(6))
                .andExpect(jsonPath("$.data.yards[0].boxes[0].boxCode").value("X-01"))
                .andExpect(jsonPath("$.data.yards[0].boxes[3].boxCode").value("X-04"))
                .andExpect(jsonPath("$.data.yards[0].boxes[3].rowNo").value(2))
                .andExpect(jsonPath("$.data.yards[0].boxes[3].columnNo").value(1));

        for (RoleCode role : new RoleCode[]{RoleCode.HEAD_TRAINER, RoleCode.GROOM, RoleCode.VETERINARIAN}) {
            String token = TestAccounts.create(mvc, jdbc, encoder, json, 7610 + role.ordinal(), role).token();
            mvc.perform(get("/api/stable-map").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.yards[0].boxes.length()").value(6));
        }
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7620, RoleCode.HORSE_OWNER).token();
        mvc.perform(get("/api/stable-map").header("Authorization", "Bearer " + owner)).andExpect(status().isForbidden());
        String hd = TestAccounts.create(mvc, jdbc, encoder, json, 7621, RoleCode.HEAD_TRAINER).token();
        mvc.perform(post("/api/stable-yards").header("Authorization", "Bearer " + hd)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yardCode\":\"Y\",\"name\":\"Y\",\"rowCount\":1,\"columnCount\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void capacityLimitsAreValidated() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7602, RoleCode.CLUB_MANAGER).token();
        for (String body : new String[]{
                "{\"yardCode\":\"X\",\"name\":\"X\",\"rowCount\":5,\"columnCount\":3}",
                "{\"yardCode\":\"X\",\"name\":\"X\",\"rowCount\":2,\"columnCount\":11}",
                "{\"yardCode\":\"XY\",\"name\":\"X\",\"rowCount\":2,\"columnCount\":3}",
                "{\"yardCode\":\"X\",\"name\":\" \",\"rowCount\":2,\"columnCount\":3}"}) {
            mvc.perform(post("/api/stable-yards").header("Authorization", "Bearer " + cm)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        for (String code : new String[]{"U", "V", "W", "X", "Y", "Z"}) {
            mvc.perform(post("/api/stable-yards").header("Authorization", "Bearer " + cm)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"yardCode\":\"" + code + "\",\"name\":\"Khu " + code + "\",\"rowCount\":1,\"columnCount\":2}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/stable-yards").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yardCode\":\"T\",\"name\":\"Khu T\",\"rowCount\":1,\"columnCount\":2}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/stable-yards").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yardCode\":\"U\",\"name\":\"Khu U\",\"rowCount\":1,\"columnCount\":2}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/stable-map").header("Authorization", "Bearer " + cm))
                .andExpect(jsonPath("$.data.totalCapacity").value(12))
                .andExpect(jsonPath("$.data.limits.maxYards").value(6));
    }

    @Test
    void maintenanceBlocksAssignmentAndAnOccupiedBoxCannotGoUnderMaintenance() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7603, RoleCode.CLUB_MANAGER).token();
        mvc.perform(post("/api/stable-yards").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yardCode\":\"X\",\"name\":\"Khu X\",\"rowCount\":1,\"columnCount\":2}"))
                .andExpect(status().isCreated());
        long box1 = jdbc.queryForObject("SELECT id FROM dbo.stable_box WHERE box_code = 'X-01'", Long.class);
        long box2 = jdbc.queryForObject("SELECT id FROM dbo.stable_box WHERE box_code = 'X-02'", Long.class);
        jdbc.update("INSERT INTO dbo.horse (id, horse_code, name, lifecycle) VALUES (7650, 'RH-7650', 'Map Horse', 'DRAFT')");

        mvc.perform(patch("/api/stable-boxes/" + box1 + "/status").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/stable-boxes/" + box1 + "/status").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MAINTENANCE\",\"reason\":\"Sửa máng ăn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maintenance").value(1))
                .andExpect(jsonPath("$.data.free").value(1));
        mvc.perform(get("/api/stable-boxes").param("status", "FREE").header("Authorization", "Bearer " + cm))
                .andExpect(jsonPath("$.data[?(@.boxCode == 'X-01')]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.boxCode == 'X-02')].yardCode").value("X"));

        String today = LocalDate.now().toString();
        mvc.perform(post("/api/horses/7650/stable-assignments").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stableBoxId\":" + box1 + ",\"effectiveFrom\":\"" + today + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/horses/7650/stable-assignments").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stableBoxId\":" + box2 + ",\"effectiveFrom\":\"" + today + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/stable-boxes/" + box2 + "/status").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MAINTENANCE\",\"reason\":\"x\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/stable-map").header("Authorization", "Bearer " + cm))
                .andExpect(jsonPath("$.data.occupied").value(1))
                .andExpect(jsonPath("$.data.free").value(0))
                .andExpect(jsonPath("$.data.yards[0].boxes[1].horse.horseCode").value("RH-7650"));
    }

    @Test
    void releasingABoxFreesItTodayKeepsHistoryAndRequiresAReason() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7605, RoleCode.CLUB_MANAGER).token();
        String groom = TestAccounts.create(mvc, jdbc, encoder, json, 7606, RoleCode.GROOM).token();
        mvc.perform(post("/api/stable-yards").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yardCode\":\"X\",\"name\":\"Khu X\",\"rowCount\":1,\"columnCount\":1}"))
                .andExpect(status().isCreated());
        long box = jdbc.queryForObject("SELECT id FROM dbo.stable_box WHERE box_code = 'X-01'", Long.class);
        jdbc.update("INSERT INTO dbo.horse (id, horse_code, name, lifecycle) VALUES (7652, 'RH-7652', 'Leaving Horse', 'ACTIVE')");
        jdbc.update("INSERT INTO dbo.horse (id, horse_code, name, lifecycle) VALUES (7653, 'RH-7653', 'Next Horse', 'DRAFT')");
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        jdbc.update("""
                INSERT INTO dbo.horse_stable_assignment (horse_id, stable_box_id, assigned_by_user_id, effective_from)
                VALUES (7652, ?, 7605, ?)
                """, box, today.minusDays(10));

        mvc.perform(post("/api/stable-boxes/" + box + "/release").header("Authorization", "Bearer " + groom)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Bán ngựa\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/stable-boxes/" + box + "/release").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/stable-boxes/" + box + "/release").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Bán ngựa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.free").value(1))
                .andExpect(jsonPath("$.data.yards[0].boxes[0].horse").doesNotExist());
        org.junit.jupiter.api.Assertions.assertEquals(today.minusDays(1), jdbc.queryForObject(
                "SELECT effective_to FROM dbo.horse_stable_assignment WHERE horse_id = 7652", LocalDate.class));
        mvc.perform(post("/api/stable-boxes/" + box + "/release").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Lần nữa\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/horses/7653/stable-assignments").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stableBoxId\":" + box + ",\"effectiveFrom\":\"" + today + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void aLegacyBoxOutsideTheLayoutIsShownUntilItsHorseMovesAndIsThenRetired() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7604, RoleCode.CLUB_MANAGER).token();
        mvc.perform(post("/api/stable-yards").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yardCode\":\"X\",\"name\":\"Khu X\",\"rowCount\":1,\"columnCount\":1}"))
                .andExpect(status().isCreated());
        jdbc.update("INSERT INTO dbo.stable_box (box_code, yard) VALUES ('LEG-90', 'Cũ')");
        long legacy = jdbc.queryForObject("SELECT id FROM dbo.stable_box WHERE box_code = 'LEG-90'", Long.class);
        long inYard = jdbc.queryForObject("SELECT id FROM dbo.stable_box WHERE box_code = 'X-01'", Long.class);
        jdbc.update("INSERT INTO dbo.horse (id, horse_code, name, lifecycle) VALUES (7651, 'RH-7651', 'Old Box Horse', 'DRAFT')");
        jdbc.update("""
                INSERT INTO dbo.horse_stable_assignment (horse_id, stable_box_id, assigned_by_user_id, effective_from)
                VALUES (7651, ?, 7604, ?)
                """, legacy, LocalDate.now().minusDays(10));

        mvc.perform(get("/api/stable-map").header("Authorization", "Bearer " + cm))
                .andExpect(jsonPath("$.data.outsideLayout[?(@.boxCode == 'LEG-90')].horse.horseCode").value("RH-7651"));
        mvc.perform(post("/api/horses/7651/stable-assignments").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stableBoxId\":" + inYard + ",\"effectiveFrom\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/stable-map").header("Authorization", "Bearer " + cm))
                .andExpect(jsonPath("$.data.outsideLayout[?(@.boxCode == 'LEG-90')]").isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals("RETIRED",
                jdbc.queryForObject("SELECT box_status FROM dbo.stable_box WHERE id = ?", String.class, legacy));
    }
}
