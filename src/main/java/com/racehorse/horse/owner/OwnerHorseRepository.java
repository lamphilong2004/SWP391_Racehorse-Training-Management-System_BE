package com.racehorse.horse.owner;

import com.racehorse.horse.owner.OwnerHorseDtos.HealthStatus;
import com.racehorse.horse.owner.OwnerHorseDtos.HorseCard;
import com.racehorse.horse.owner.OwnerHorseDtos.HorseProfile;
import com.racehorse.horse.owner.OwnerHorseDtos.Lifecycle;
import com.racehorse.horse.owner.OwnerHorseDtos.Ownership;
import com.racehorse.horse.owner.OwnerHorseDtos.ParentRef;
import com.racehorse.horse.owner.OwnerHorseDtos.Pedigree;
import com.racehorse.horse.owner.OwnerHorseDtos.ProgramStatus;
import com.racehorse.horse.owner.OwnerHorseDtos.ProgramSummary;
import com.racehorse.horse.owner.OwnerHorseDtos.RecoveryStatus;
import com.racehorse.horse.owner.OwnerHorseDtos.SessionStatus;
import com.racehorse.horse.owner.OwnerHorseDtos.SessionSummary;
import com.racehorse.horse.owner.OwnerHorseDtos.TrainingSummary;
import com.racehorse.horse.owner.OwnerHorseDtos.TrainingAvailability;
import com.racehorse.horse.owner.OwnerHorseDtos.WeightPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;
import org.springframework.dao.support.DataAccessUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class OwnerHorseRepository {

    private static final String OWNER_EXISTS = """
            EXISTS (
                SELECT 1
                FROM dbo.horse_ownership ho
                WHERE ho.horse_id = h.id
                  AND ho.owner_user_id = ?
                  AND ho.effective_to IS NULL
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public OwnerHorseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isActiveHorseOwner(long ownerUserId) {
        Boolean active = jdbcTemplate.queryForObject("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM dbo.app_user u
                    JOIN dbo.user_role ur ON ur.user_id = u.id
                    JOIN dbo.role r ON r.id = ur.role_id
                    WHERE u.id = ?
                      AND u.account_status = 'ACTIVE'
                      AND r.role_code = 'HORSE_OWNER'
                ) THEN 1 ELSE 0 END
                """, Boolean.class, ownerUserId);
        return Boolean.TRUE.equals(active);
    }

    public List<HorseCard> findOwnedHorseCards(long ownerUserId) {
        String sql = """
                SELECT h.id, h.horse_code, h.name, h.breed, h.sex, h.foaled_date,
                       h.lifecycle, h.health_status,
                       COALESCE(mr.restriction_level, 'AVAILABLE') AS training_availability
                FROM dbo.horse h
                LEFT JOIN dbo.medical_restriction mr
                  ON mr.horse_id = h.id AND mr.lifted_at IS NULL
                WHERE
                """ + OWNER_EXISTS + """
                ORDER BY h.id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> horseCard(rs), ownerUserId);
    }

    public Optional<HorseProfile> findOwnedProfile(long ownerUserId, long horseId) {
        String sql = """
                SELECT h.id, h.horse_code, h.name, h.breed, h.sex, h.foaled_date,
                       h.lifecycle, h.health_status,
                       COALESCE(mr.restriction_level, 'AVAILABLE') AS training_availability,
                       h.country_of_origin, h.registration_no, h.activated_at,
                       h.sire_horse_id, h.sire_name, sire.horse_code AS sire_horse_code,
                       h.dam_horse_id, h.dam_name, dam.horse_code AS dam_horse_code,
                       ho.share_percentage, ho.effective_from AS ownership_effective_from,
                       ho.is_managing_owner, b.box_code, a.effective_from AS stable_effective_from
                FROM dbo.horse h
                JOIN dbo.horse_ownership ho
                  ON ho.horse_id = h.id
                 AND ho.owner_user_id = ?
                 AND ho.effective_to IS NULL
                LEFT JOIN dbo.medical_restriction mr
                  ON mr.horse_id = h.id AND mr.lifted_at IS NULL
                LEFT JOIN dbo.horse sire ON sire.id = h.sire_horse_id
                LEFT JOIN dbo.horse dam ON dam.id = h.dam_horse_id
                LEFT JOIN dbo.horse_stable_assignment a ON a.horse_id=h.id AND a.effective_to IS NULL
                LEFT JOIN dbo.stable_box b ON b.id=a.stable_box_id
                WHERE h.id = ?
                """;
        List<HorseProfile> profiles = jdbcTemplate.query(sql, (rs, rowNum) -> {
            HorseCard horse = horseCard(rs);
            ParentRef sire = new ParentRef(
                    nullableLong(rs, "sire_horse_id"),
                    rs.getString("sire_horse_code"),
                    rs.getString("sire_name"));
            ParentRef dam = new ParentRef(
                    nullableLong(rs, "dam_horse_id"),
                    rs.getString("dam_horse_code"),
                    rs.getString("dam_name"));
            Ownership ownership = new Ownership(
                    rs.getBigDecimal("share_percentage"),
                    rs.getObject("ownership_effective_from", LocalDate.class),
                    rs.getBoolean("is_managing_owner"));
            return new HorseProfile(
                    horse,
                    rs.getString("country_of_origin"),
                    rs.getString("registration_no"),
                    instant(rs, "activated_at"),
                    rs.getString("box_code"),
                    rs.getObject("stable_effective_from", LocalDate.class),
                    new Pedigree(sire, dam),
                    ownership);
        }, ownerUserId, horseId);
        return Optional.ofNullable(DataAccessUtils.singleResult(profiles));
    }

    public Optional<HealthSnapshot> findOwnedHealthSnapshot(long ownerUserId, long horseId) {
        String sql = """
                SELECT h.health_status,
                       COALESCE(mr.restriction_level, 'AVAILABLE') AS training_availability,
                       mr.review_date AS restriction_review_date
                FROM dbo.horse h
                LEFT JOIN dbo.medical_restriction mr
                  ON mr.horse_id = h.id AND mr.lifted_at IS NULL
                WHERE h.id = ?
                  AND
                """ + OWNER_EXISTS;
        List<HealthSnapshot> snapshots = jdbcTemplate.query(sql, (rs, rowNum) -> new HealthSnapshot(
                enumValue(HealthStatus.class, rs.getString("health_status")),
                enumValue(TrainingAvailability.class, rs.getString("training_availability")),
                rs.getObject("restriction_review_date", LocalDate.class)), horseId, ownerUserId);
        return Optional.ofNullable(DataAccessUtils.singleResult(snapshots));
    }

    public Optional<RecoveryStatus> findLatestOpenCaseRecoveryStatus(long ownerUserId, long horseId) {
        String sql = """
                SELECT ve.recovery_outcome
                FROM dbo.veterinary_examination ve
                JOIN dbo.medical_case mc ON mc.id = ve.medical_case_id
                WHERE ve.horse_id = ?
                  AND mc.status = 'OPEN'
                  AND ve.exam_type = 'RE_EXAMINATION'
                  AND ve.recovery_outcome IS NOT NULL
                  AND EXISTS (
                      SELECT 1
                      FROM dbo.horse_ownership ho
                      WHERE ho.horse_id = ve.horse_id
                        AND ho.owner_user_id = ?
                        AND ho.effective_to IS NULL
                  )
                ORDER BY ve.examined_at DESC, ve.id DESC
                OFFSET 0 ROWS FETCH NEXT 1 ROW ONLY
                """;
        List<RecoveryStatus> statuses = jdbcTemplate.query(sql, (rs, rowNum) ->
                enumValue(RecoveryStatus.class, rs.getString("recovery_outcome")), horseId, ownerUserId);
        return Optional.ofNullable(DataAccessUtils.singleResult(statuses));
    }

    public Optional<Instant> findLatestExaminedAt(long ownerUserId, long horseId) {
        String sql = """
                SELECT ve.examined_at
                FROM dbo.veterinary_examination ve
                WHERE ve.horse_id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM dbo.horse_ownership ho
                      WHERE ho.horse_id = ve.horse_id
                        AND ho.owner_user_id = ?
                        AND ho.effective_to IS NULL
                  )
                ORDER BY ve.examined_at DESC, ve.id DESC
                OFFSET 0 ROWS FETCH NEXT 1 ROW ONLY
                """;
        List<Instant> timestamps = jdbcTemplate.query(sql,
                (rs, rowNum) -> instant(rs, "examined_at"), horseId, ownerUserId);
        return Optional.ofNullable(DataAccessUtils.singleResult(timestamps));
    }

    public List<WeightPoint> findRecentWeights(long ownerUserId, long horseId) {
        String sql = """
                SELECT wr.weight, wr.measured_at
                FROM dbo.weight_record wr
                WHERE wr.horse_id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM dbo.horse_ownership ho
                      WHERE ho.horse_id = wr.horse_id
                        AND ho.owner_user_id = ?
                        AND ho.effective_to IS NULL
                  )
                ORDER BY wr.measured_at DESC, wr.id DESC
                OFFSET 0 ROWS FETCH NEXT 12 ROWS ONLY
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new WeightPoint(rs.getBigDecimal("weight"), instant(rs, "measured_at")), horseId, ownerUserId);
    }

    public Optional<TrainingSummary> findOwnedTrainingSummary(long ownerUserId, long horseId) {
        if (!isOwnedBy(ownerUserId, horseId)) {
            return Optional.empty();
        }

        LocalDate today = LocalDate.now(Clock.systemUTC());
        ProgramSummary currentProgram = findCurrentProgram(ownerUserId, horseId, today).orElse(null);
        List<SessionSummary> recentSessions = findRecentSessions(ownerUserId, horseId);
        return Optional.of(new TrainingSummary(currentProgram, recentSessions));
    }

    private boolean isOwnedBy(long ownerUserId, long horseId) {
        Boolean owned = jdbcTemplate.queryForObject("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM dbo.horse_ownership ho
                    WHERE ho.horse_id = ?
                      AND ho.owner_user_id = ?
                      AND ho.effective_to IS NULL
                ) THEN 1 ELSE 0 END
                """, Boolean.class, horseId, ownerUserId);
        return Boolean.TRUE.equals(owned);
    }

    private Optional<ProgramSummary> findCurrentProgram(long ownerUserId, long horseId, LocalDate today) {
        List<ProgramSummary> programs = jdbcTemplate.query("""
                SELECT p.name, p.status, p.period_start, p.period_end,
                       (
                           SELECT CASE WHEN COUNT(*) = 1 THEN MAX(ph.name) ELSE NULL END
                           FROM dbo.horse_program_phase ph
                           WHERE ph.horse_training_program_id = p.id
                             AND ph.start_date <= ?
                             AND ph.end_date >= ?
                       ) AS current_phase_name,
                       (
                           SELECT COUNT(*)
                           FROM dbo.horse_program_phase ph
                           JOIN dbo.training_session s ON s.horse_program_phase_id = ph.id
                           WHERE ph.horse_training_program_id = p.id
                             AND s.status = 'COMPLETED'
                       ) AS sessions_completed,
                       (
                           SELECT COUNT(*)
                           FROM dbo.horse_program_phase ph
                           JOIN dbo.training_session s ON s.horse_program_phase_id = ph.id
                           WHERE ph.horse_training_program_id = p.id
                       ) AS sessions_planned
                FROM dbo.horse_training_program p
                WHERE p.horse_id = ?
                  AND p.status = 'ACTIVE'
                  AND p.period_start <= ?
                  AND p.period_end >= ?
                  AND EXISTS (
                      SELECT 1
                      FROM dbo.horse_ownership ho
                      WHERE ho.horse_id = p.horse_id
                        AND ho.owner_user_id = ?
                        AND ho.effective_to IS NULL
                  )
                ORDER BY p.activated_at DESC, p.id DESC
                OFFSET 0 ROWS FETCH NEXT 1 ROW ONLY
                """, (rs, rowNum) -> new ProgramSummary(
                rs.getString("name"),
                ProgramStatus.valueOf(rs.getString("status")),
                rs.getObject("period_start", LocalDate.class),
                rs.getObject("period_end", LocalDate.class),
                rs.getString("current_phase_name"),
                rs.getInt("sessions_completed"),
                rs.getInt("sessions_planned")),
                today, today, horseId, today, today, ownerUserId);
        return Optional.ofNullable(DataAccessUtils.singleResult(programs));
    }

    private List<SessionSummary> findRecentSessions(long ownerUserId, long horseId) {
        return jdbcTemplate.query("""
                SELECT s.session_code, s.scheduled_start, s.status, sr.evaluation_label
                FROM dbo.training_session s
                JOIN dbo.horse_program_phase ph ON ph.id = s.horse_program_phase_id
                JOIN dbo.horse_training_program p ON p.id = ph.horse_training_program_id
                LEFT JOIN dbo.session_result sr ON sr.training_session_id = s.id
                WHERE p.horse_id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM dbo.horse_ownership ho
                      WHERE ho.horse_id = p.horse_id
                        AND ho.owner_user_id = ?
                        AND ho.effective_to IS NULL
                  )
                ORDER BY s.scheduled_start DESC, s.id DESC
                OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY
                """, (rs, rowNum) -> new SessionSummary(
                rs.getString("session_code"),
                instant(rs, "scheduled_start"),
                SessionStatus.valueOf(rs.getString("status")),
                rs.getString("evaluation_label")), horseId, ownerUserId);
    }

    public boolean horseExists(long horseId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.horse WHERE id = ?", Integer.class, horseId);
        return count != null && count > 0;
    }

    private static HorseCard horseCard(ResultSet rs) throws SQLException {
        return new HorseCard(
                rs.getLong("id"),
                rs.getString("horse_code"),
                rs.getString("name"),
                rs.getString("breed"),
                rs.getString("sex"),
                rs.getObject("foaled_date", LocalDate.class),
                enumValue(Lifecycle.class, rs.getString("lifecycle")),
                enumValue(HealthStatus.class, rs.getString("health_status")),
                enumValue(TrainingAvailability.class, rs.getString("training_availability")));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumType, String value) {
        return value == null ? null : Enum.valueOf(enumType, value);
    }

    record HealthSnapshot(
            HealthStatus healthStatus,
            TrainingAvailability trainingAvailability,
            LocalDate restrictionReviewDate) {

        HealthSnapshot {
            Assert.notNull(trainingAvailability, "Training availability must be derived.");
        }
    }
}
