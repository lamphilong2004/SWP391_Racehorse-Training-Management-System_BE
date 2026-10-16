package com.racehorse.core035;

import com.racehorse.core035.MedicalDtos.ExamView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Narrow SQL access until CORE-014's existing examination mapping is merged. */
@Repository
class ExaminationStore {
    record HistoryItem(ExamType type, ExamView view) {}
    private final JdbcTemplate jdbc;
    ExaminationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
    }
    long insert(long horseId, long caseId, long actorId, ExamType type, Instant examinedAt,
                String findings, String diagnosis, ClinicalSeverity severity, RecoveryStatus recovery,
                HealthStatus health, String bodyCondition, Instant createdAt) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into dbo.veterinary_examination(horse_id,medical_case_id,performed_by_user_id,exam_type,examined_at,findings,diagnosis,clinical_severity,recovery_outcome,health_status_set,body_condition,created_at) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            Object[] values = {horseId, caseId, actorId, type.name(), utc(examinedAt), findings, diagnosis,
                    severity == null ? null : severity.name(), recovery == null ? null : recovery.name(),
                    health == null ? null : health.name(), bodyCondition, utc(createdAt)};
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    boolean belongs(long id, long caseId, long horseId) {
        Integer count = jdbc.queryForObject("select count(*) from dbo.veterinary_examination where id=? and medical_case_id=? and horse_id=?", Integer.class, id, caseId, horseId);
        return count != null && count > 0;
    }
    List<HistoryItem> history(long caseId) {
        return jdbc.query("select * from dbo.veterinary_examination where medical_case_id=? order by examined_at desc,id desc", (row, n) ->
                new HistoryItem(ExamType.valueOf(row.getString("exam_type")), new ExamView(
                        row.getLong("id"), row.getLong("medical_case_id"), row.getLong("horse_id"),
                        row.getObject("examined_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        row.getString("findings"), row.getString("diagnosis"),
                        value(row.getString("clinical_severity"), ClinicalSeverity.class),
                        value(row.getString("health_status_set"), HealthStatus.class),
                        row.getString("body_condition"), value(row.getString("recovery_outcome"), RecoveryStatus.class))), caseId);
    }
    private static <T extends Enum<T>> T value(String text, Class<T> type) {
        return text == null ? null : Enum.valueOf(type, text);
    }
}
