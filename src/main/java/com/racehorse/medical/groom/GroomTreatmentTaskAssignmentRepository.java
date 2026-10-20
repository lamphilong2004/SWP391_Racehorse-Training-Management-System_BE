package com.racehorse.medical.groom;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/** Persistence boundary for CORE-035 treatment-plan task materialization. */
@Repository
public class GroomTreatmentTaskAssignmentRepository {
    private final JdbcTemplate jdbc;
    private final boolean sqlServer;

    public GroomTreatmentTaskAssignmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        try (Connection connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            this.sqlServer = connection.getMetaData().getDatabaseProductName()
                    .toLowerCase(Locale.ROOT).contains("microsoft sql server");
        } catch (java.sql.SQLException error) {
            throw new IllegalStateException("Could not determine the database product for treatment task insertion.", error);
        }
    }

    public void requireActiveGroom(long groomUserId) {
        if (groomUserId <= 0) throw validation("assignedGroomUserId must be positive.");
        Long count = jdbc.queryForObject("""
                SELECT COUNT_BIG(*)
                FROM dbo.app_user u
                JOIN dbo.user_role ur ON ur.user_id = u.id
                JOIN dbo.role r ON r.id = ur.role_id
                WHERE u.id = ? AND u.account_status = 'ACTIVE' AND r.role_code = 'GROOM'
                """, Long.class, groomUserId);
        if (count == null || count != 1) {
            throw validation("assignedGroomUserId must identify an active GROOM account.");
        }
    }

    @Transactional
    public long insertTreatmentTask(long treatmentPlanId, long horseId, long groomUserId,
                                    String instruction, LocalDate taskDate, String scheduledSlot) {
        if (treatmentPlanId <= 0 || horseId <= 0 || taskDate == null
                || instruction == null || instruction.isBlank() || instruction.length() > 2000
                || scheduledSlot != null && scheduledSlot.length() > 50) {
            throw validation("Treatment task fields are invalid.");
        }
        requireActiveGroom(groomUserId);
        Long validPlan = jdbc.queryForObject("""
                SELECT COUNT_BIG(*)
                FROM dbo.treatment_plan tp
                JOIN dbo.medical_case mc ON mc.id = tp.medical_case_id
                WHERE tp.id = ? AND mc.horse_id = ? AND mc.status = 'OPEN'
                """, Long.class, treatmentPlanId, horseId);
        if (validPlan == null || validPlan != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "An open treatment plan for this horse was not found.");
        }

        if (sqlServer) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO dbo.care_task
                        (horse_id, treatment_plan_id, assigned_groom_user_id, task_date,
                         scheduled_slot, instruction_snapshot)
                    OUTPUT INSERTED.id
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Long.class, horseId, treatmentPlanId, groomUserId,
                    taskDate, scheduledSlot, instruction);
            if (id == null) throw new IllegalStateException("SQL Server did not return the inserted treatment task id.");
            return id;
        }

        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.care_task
                        (horse_id, treatment_plan_id, assigned_groom_user_id, task_date,
                         scheduled_slot, instruction_snapshot)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, horseId);
            statement.setLong(2, treatmentPlanId);
            statement.setLong(3, groomUserId);
            statement.setObject(4, taskDate);
            statement.setString(5, scheduledSlot);
            statement.setString(6, instruction);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("Database did not return the inserted treatment task id.");
        return id.longValue();
    }

    private static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
