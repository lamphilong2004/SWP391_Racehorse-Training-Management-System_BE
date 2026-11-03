package com.racehorse.medical.groom;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.core035.MedicalPorts.TreatmentTaskMaterializer;
import com.racehorse.core035.MedicalPorts.TreatmentTaskSpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Idempotently appends the Vet-approved task set without discarding existing task history. */
@Component
public class GroomTreatmentTaskMaterializer implements TreatmentTaskMaterializer {
    private final JdbcTemplate jdbc;
    private final GroomTreatmentTaskAssignmentRepository assignments;

    public GroomTreatmentTaskMaterializer(JdbcTemplate jdbc, GroomTreatmentTaskAssignmentRepository assignments) {
        this.jdbc = jdbc;
        this.assignments = assignments;
    }

    @Override
    @Transactional
    public void reconcile(long treatmentPlanId, long horseId, List<TreatmentTaskSpec> requestedTasks) {
        if (treatmentPlanId <= 0 || horseId <= 0 || requestedTasks == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Treatment task reconciliation input is invalid.");
        }
        assignments.requireAssignmentMigration();

        Set<TaskKey> requested = new HashSet<>();
        for (TreatmentTaskSpec task : requestedTasks) {
            if (task == null || task.instruction() == null || task.instruction().isBlank()
                    || task.instruction().length() > 2000 || task.taskDate() == null
                    || task.scheduledSlot() != null && task.scheduledSlot().length() > 50
                    || task.assignedGroomUserId() <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Treatment task fields are invalid.");
            }
            assignments.requireActiveGroom(task.assignedGroomUserId());
            if (!requested.add(TaskKey.of(task))) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Duplicate treatment task entries are not allowed.");
            }
        }

        List<ExistingTask> existing = jdbc.query("""
                        SELECT id, horse_id, assigned_groom_user_id, instruction_snapshot, task_date,
                               scheduled_slot
                        FROM dbo.care_task
                        WHERE treatment_plan_id = ?
                        ORDER BY id
                        """,
                GroomTreatmentTaskMaterializer::mapTask, treatmentPlanId);
        Set<TaskKey> materialized = new HashSet<>();
        for (ExistingTask task : existing) {
            if (task.horseId() != horseId || task.assignedGroomUserId() == null
                    || task.instruction() == null || task.taskDate() == null) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "An existing treatment task cannot be reconciled safely; no task history was changed.");
            }
            TaskKey key = new TaskKey(task.assignedGroomUserId(), task.instruction(), task.taskDate(), task.scheduledSlot());
            if (!requested.contains(key)) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "Existing treatment tasks cannot be removed or changed by plan replacement.");
            }
            materialized.add(key);
        }

        for (TreatmentTaskSpec task : requestedTasks) {
            TaskKey key = TaskKey.of(task);
            if (!materialized.contains(key)) {
                assignments.insertTreatmentTask(treatmentPlanId, horseId, task.assignedGroomUserId(),
                        task.instruction(), task.taskDate(), task.scheduledSlot());
                materialized.add(key);
            }
        }
    }

    private static ExistingTask mapTask(ResultSet rs, int row) throws SQLException {
        long groomId = rs.getLong("assigned_groom_user_id");
        Long assignedGroomUserId = rs.wasNull() ? null : groomId;
        return new ExistingTask(rs.getLong("horse_id"), assignedGroomUserId,
                rs.getString("instruction_snapshot"), rs.getObject("task_date", LocalDate.class),
                rs.getString("scheduled_slot"));
    }

    private record ExistingTask(long horseId, Long assignedGroomUserId, String instruction, LocalDate taskDate,
                                String scheduledSlot) {}

    private record TaskKey(long groomId, String instruction, LocalDate taskDate, String scheduledSlot) {
        static TaskKey of(TreatmentTaskSpec task) {
            return new TaskKey(task.assignedGroomUserId(), task.instruction(), task.taskDate(), task.scheduledSlot());
        }
    }
}
