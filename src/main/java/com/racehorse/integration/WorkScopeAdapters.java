package com.racehorse.integration;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.core026.Contracts;
import com.racehorse.core026.IntegrationPorts;
import com.racehorse.core035.MedicalPorts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DEMO INTEGRATION: work-scope adapters for CORE-026 / CORE-035.
 * Groom pool (proposal P-11, pending DEC-011): DB-01 has no Groom column on training_session or care_task, so
 * every ACTIVE Groom works the shared queue. Incident link sets incident.medical_case_id (same horse only).
 */
public class WorkScopeAdapters {
    private WorkScopeAdapters() {}

    @Component
    public static class GroomPool implements IntegrationPorts.GroomWorkScope, MedicalPorts.GroomTaskScope {
        private final JdbcTemplate jdbc;
        public GroomPool(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Override
        public boolean mayCheck(Contracts.Actor actor, long sessionId) {
            return actor.has(Contracts.Role.GROOM);
        }

        @Override
        public boolean mayReportCareTask(Contracts.Actor actor, long taskId) {
            return actor.has(Contracts.Role.GROOM);
        }

        @Override
        public List<Long> sessionIds(Contracts.Actor actor, LocalDate date) {
            LocalDateTime from = date.atStartOfDay();
            return jdbc.queryForList("SELECT id FROM dbo.training_session WHERE status='SCHEDULED' "
                    + "AND scheduled_start>=? AND scheduled_start<? ORDER BY scheduled_start, id", Long.class,
                    from, from.plusDays(1));
        }

        @Override
        public Set<Long> assignedTaskIds(MedicalPorts.Actor actor) {
            return new HashSet<>(jdbc.queryForList(
                    "SELECT id FROM dbo.care_task WHERE treatment_plan_id IS NOT NULL", Long.class));
        }
    }

    @Component
    public static class IncidentCaseLink implements MedicalPorts.IncidentLink {
        private final JdbcTemplate jdbc;
        public IncidentCaseLink(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Override
        public void link(long incidentId, long horseId, long caseId, MedicalPorts.Actor actor) {
            var rows = jdbc.queryForList("SELECT horse_id, medical_case_id FROM dbo.incident WITH (UPDLOCK) WHERE id=?",
                    incidentId);
            if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "Incident not found.");
            var row = rows.getFirst();
            if (((Number) row.get("horse_id")).longValue() != horseId)
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Incident belongs to another horse.");
            if (row.get("medical_case_id") != null)
                throw new BusinessException(ErrorCode.CONFLICT, "Incident is already linked to a medical case.");
            jdbc.update("UPDATE dbo.incident SET medical_case_id=?, reviewed_by_user_id=?, status='UNDER_REVIEW', "
                    + "updated_at=SYSUTCDATETIME() WHERE id=?", caseId, actor.id(), incidentId);
        }
    }
}
