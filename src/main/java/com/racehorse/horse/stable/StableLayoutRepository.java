package com.racehorse.horse.stable;

import com.racehorse.common.jdbc.GeneratedIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class StableLayoutRepository {

    public record YardRow(long id, String yardCode, String name, int rowCount, int columnCount) {
    }

    public record BoxRow(long id, String boxCode, Long yardId, Integer rowNo, Integer columnNo, String boxStatus,
                         Long horseId, String horseCode, String horseName, String lifecycle) {
    }

    public record BoxState(long id, String boxCode, Long yardId, String boxStatus, boolean occupied) {
    }

    private final JdbcTemplate jdbc;

    public StableLayoutRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<YardRow> yards() {
        return jdbc.query("""
                SELECT id, yard_code, name, row_count, column_count FROM dbo.stable_yard ORDER BY yard_code
                """, (r, n) -> new YardRow(r.getLong("id"), r.getString("yard_code"), r.getString("name"),
                r.getInt("row_count"), r.getInt("column_count")));
    }

    /** Boxes inside a yard plus legacy boxes outside the layout that still hold a horse. Retired boxes are excluded. */
    public List<BoxRow> mapBoxes() {
        return jdbc.query("""
                SELECT b.id, b.box_code, b.stable_yard_id, b.row_no, b.column_no, b.box_status,
                       h.id AS horse_id, h.horse_code, h.name AS horse_name, h.lifecycle
                FROM dbo.stable_box b
                LEFT JOIN dbo.horse_stable_assignment a ON a.stable_box_id = b.id AND a.effective_to IS NULL
                LEFT JOIN dbo.horse h ON h.id = a.horse_id
                WHERE b.box_status <> 'RETIRED' AND (b.stable_yard_id IS NOT NULL OR a.id IS NOT NULL)
                ORDER BY b.box_code
                """, (r, n) -> new BoxRow(r.getLong("id"), r.getString("box_code"),
                r.getObject("stable_yard_id", Long.class), r.getObject("row_no", Integer.class),
                r.getObject("column_no", Integer.class), r.getString("box_status"),
                r.getObject("horse_id", Long.class), r.getString("horse_code"), r.getString("horse_name"),
                r.getString("lifecycle")));
    }

    public int yardCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.stable_yard", Integer.class);
        return count == null ? 0 : count;
    }

    public boolean yardCodeExists(String yardCode) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.stable_yard WHERE yard_code = ?) THEN 1 ELSE 0 END
                """, Boolean.class, yardCode));
    }

    public long insertYard(String yardCode, String name, int rows, int columns, long actorUserId) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.stable_yard (yard_code, name, row_count, column_count, created_by_user_id)
                    VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, yardCode);
            statement.setString(2, name);
            statement.setInt(3, rows);
            statement.setInt(4, columns);
            statement.setLong(5, actorUserId);
            return statement;
        }, keys);
        return GeneratedIdentity.requireId(keys);
    }

    public Optional<BoxState> boxByCode(String boxCode) {
        return jdbc.query("""
                SELECT b.id, b.box_code, b.stable_yard_id, b.box_status,
                       CASE WHEN EXISTS (SELECT 1 FROM dbo.horse_stable_assignment a
                                         WHERE a.stable_box_id = b.id AND a.effective_to IS NULL) THEN 1 ELSE 0 END AS occupied
                FROM dbo.stable_box b WHERE b.box_code = ?
                """, (r, n) -> new BoxState(r.getLong("id"), r.getString("box_code"),
                r.getObject("stable_yard_id", Long.class), r.getString("box_status"), r.getInt("occupied") == 1),
                boxCode).stream().findFirst();
    }

    public Optional<BoxState> box(long boxId) {
        return jdbc.query("""
                SELECT b.id, b.box_code, b.stable_yard_id, b.box_status,
                       CASE WHEN EXISTS (SELECT 1 FROM dbo.horse_stable_assignment a
                                         WHERE a.stable_box_id = b.id AND a.effective_to IS NULL) THEN 1 ELSE 0 END AS occupied
                FROM dbo.stable_box b WHERE b.id = ?
                """, (r, n) -> new BoxState(r.getLong("id"), r.getString("box_code"),
                r.getObject("stable_yard_id", Long.class), r.getString("box_status"), r.getInt("occupied") == 1),
                boxId).stream().findFirst();
    }

    public void insertBox(String boxCode, String yardName, long yardId, int row, int column) {
        jdbc.update("""
                INSERT INTO dbo.stable_box (box_code, yard, stable_yard_id, row_no, column_no, box_status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """, boxCode, yardName, yardId, row, column);
    }

    /** Places an existing legacy box (same code, outside every yard) into its cell instead of inserting a duplicate. */
    public int placeLegacyBox(long boxId, String yardName, long yardId, int row, int column) {
        return jdbc.update("""
                UPDATE dbo.stable_box
                SET yard = ?, stable_yard_id = ?, row_no = ?, column_no = ?, box_status = 'ACTIVE', updated_at = ?
                WHERE id = ? AND stable_yard_id IS NULL
                """, yardName, yardId, row, column, now(), boxId);
    }

    public int updateBoxStatus(long boxId, String expectedStatus, String newStatus) {
        return jdbc.update("""
                UPDATE dbo.stable_box SET box_status = ?, updated_at = ?
                WHERE id = ? AND box_status = ? AND stable_yard_id IS NOT NULL
                """, newStatus, now(), boxId, expectedStatus);
    }

    public record OpenAssignment(long id, long horseId, String horseCode, String horseName, java.time.LocalDate effectiveFrom) {
    }

    public Optional<OpenAssignment> openAssignment(long boxId) {
        return jdbc.query("""
                SELECT a.id, h.id AS horse_id, h.horse_code, h.name, a.effective_from
                FROM dbo.horse_stable_assignment a JOIN dbo.horse h ON h.id = a.horse_id
                WHERE a.stable_box_id = ? AND a.effective_to IS NULL
                """, (r, n) -> new OpenAssignment(r.getLong("id"), r.getLong("horse_id"), r.getString("horse_code"),
                r.getString("name"), r.getObject("effective_from", java.time.LocalDate.class)), boxId).stream().findFirst();
    }

    /** A horse riding right now (IN_PROGRESS or PAUSED session) cannot leave its box. */
    public boolean horseInActiveSession(long horseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.training_session ts
                    JOIN dbo.horse_program_phase p ON p.id = ts.horse_program_phase_id
                    JOIN dbo.horse_training_program hp ON hp.id = p.horse_training_program_id
                    WHERE hp.horse_id = ? AND ts.status IN ('IN_PROGRESS','PAUSED')) THEN 1 ELSE 0 END
                """, Boolean.class, horseId));
    }

    public int closeAssignment(long assignmentId, java.time.LocalDate effectiveTo) {
        return jdbc.update("""
                UPDATE dbo.horse_stable_assignment SET effective_to = ?, updated_at = ?
                WHERE id = ? AND effective_to IS NULL
                """, effectiveTo, now(), assignmentId);
    }

    public void retireIfOutsideLayout(long boxId) {
        jdbc.update("""
                UPDATE dbo.stable_box SET box_status = 'RETIRED', updated_at = ?
                WHERE id = ? AND stable_yard_id IS NULL AND box_status = 'ACTIVE'
                """, now(), boxId);
    }

    public void audit(long actorUserId, String actorRole, String action, String targetType, long targetId, String summary) {
        jdbc.update("""
                INSERT INTO dbo.audit_log (actor_user_id, actor_role, action, target_type, target_id, safe_summary, source)
                VALUES (?, ?, ?, ?, ?, ?, 'WEB')
                """, actorUserId, actorRole, action, targetType, targetId, summary);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(Clock.systemUTC());
    }
}
