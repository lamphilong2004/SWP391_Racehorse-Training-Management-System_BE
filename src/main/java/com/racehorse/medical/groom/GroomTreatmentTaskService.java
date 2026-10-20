package com.racehorse.medical.groom;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class GroomTreatmentTaskService {
    private static final int MAX_PAGE_SIZE = 100;

    private static final String ASSIGNED_TREATMENT_TASK = """
            FROM dbo.care_task ct
            WHERE ct.treatment_plan_id IS NOT NULL
              AND ct.assigned_groom_user_id = ?
            """;

    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;

    public GroomTreatmentTaskService(JdbcTemplate jdbc, CurrentActorProvider actors) {
        this.jdbc = jdbc;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public GroomTreatmentTasksPage list(int page, int size) {
        validatePage(page, size);
        ActorContext actor = requireGroom();
        long total = jdbc.queryForObject(
                "SELECT COUNT_BIG(*) " + ASSIGNED_TREATMENT_TASK,
                Long.class,
                actor.userId());
        List<GroomTreatmentTask> items = jdbc.query("""
                        SELECT ct.id, ct.horse_id, ct.assigned_groom_user_id,
                               ct.instruction_snapshot, ct.task_date, ct.scheduled_slot,
                               ct.completed_at, ct.completed_by_user_id
                        """ + ASSIGNED_TREATMENT_TASK + """
                        ORDER BY ct.task_date, ct.id
                        OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                        """,
                GroomTreatmentTaskService::mapTask,
                actor.userId(), (long) page * size, size);
        return new GroomTreatmentTasksPage(items, page, size, total);
    }

    @Transactional
    public GroomTreatmentTask complete(long taskId, Map<String, Object> request) {
        if (taskId <= 0) throw validation("taskId must be positive.");
        if (request == null || !request.isEmpty()) {
            throw validation("The completion request must be an empty object.");
        }
        ActorContext actor = requireGroom();

        Long assignedCount = jdbc.queryForObject("""
                SELECT COUNT_BIG(*)
                FROM dbo.care_task
                WHERE id = ? AND treatment_plan_id IS NOT NULL
                  AND assigned_groom_user_id = ?
                """, Long.class, taskId, actor.userId());
        if (assignedCount == null || assignedCount == 0) {
            Long treatmentTaskCount = jdbc.queryForObject("""
                    SELECT COUNT_BIG(*) FROM dbo.care_task
                    WHERE id = ? AND treatment_plan_id IS NOT NULL
                    """, Long.class, taskId);
            if (treatmentTaskCount != null && treatmentTaskCount > 0) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "This treatment task is outside the authenticated Groom's assignment.");
            }
            throw notFound("Treatment task was not found.");
        }

        int changed = jdbc.update("""
                UPDATE dbo.care_task
                SET completed_at = SYSUTCDATETIME(),
                    completed_by_user_id = ?,
                    updated_at = SYSUTCDATETIME()
                WHERE id = ? AND treatment_plan_id IS NOT NULL
                  AND assigned_groom_user_id = ?
                  AND completed_at IS NULL AND completed_by_user_id IS NULL
                """, actor.userId(), taskId, actor.userId());
        if (changed != 1) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "The treatment task has already been completed or changed.");
        }
        return findAssigned(taskId, actor.userId());
    }

    private GroomTreatmentTask findAssigned(long taskId, long groomId) {
        List<GroomTreatmentTask> rows = jdbc.query("""
                        SELECT ct.id, ct.horse_id, ct.assigned_groom_user_id,
                               ct.instruction_snapshot, ct.task_date, ct.scheduled_slot,
                               ct.completed_at, ct.completed_by_user_id
                        FROM dbo.care_task ct
                        WHERE ct.id = ? AND ct.treatment_plan_id IS NOT NULL
                          AND ct.assigned_groom_user_id = ?
                        """,
                GroomTreatmentTaskService::mapTask, taskId, groomId);
        if (rows.isEmpty()) throw notFound("Treatment task was not found.");
        return rows.getFirst();
    }

    private ActorContext requireGroom() {
        ActorContext actor = actors.currentActor().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required."));
        if (!actor.roles().contains(RoleCode.GROOM)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Only a Groom may access assigned treatment tasks.");
        }
        return actor;
    }

    private static GroomTreatmentTask mapTask(ResultSet rs, int row) throws SQLException {
        LocalDateTime completedAt = rs.getObject("completed_at", LocalDateTime.class);
        long completedBy = rs.getLong("completed_by_user_id");
        boolean hasCompletedBy = !rs.wasNull();
        boolean complete = completedAt != null && hasCompletedBy;
        return new GroomTreatmentTask(
                rs.getLong("id"),
                rs.getLong("horse_id"),
                rs.getLong("assigned_groom_user_id"),
                rs.getString("instruction_snapshot"),
                rs.getDate("task_date").toLocalDate(),
                rs.getString("scheduled_slot"),
                complete ? "COMPLETED" : "PENDING",
                completedAt == null ? null : completedAt.toInstant(ZoneOffset.UTC));
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw validation("page must be non-negative and size must be between 1 and 100.");
        }
    }

    private static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }
}
