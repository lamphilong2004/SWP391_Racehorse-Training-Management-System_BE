package com.racehorse.horse.stable;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class StableBoxReadRepository {

    private static final String STATUS_EXPRESSION =
            "CASE WHEN assignment.id IS NULL THEN 'FREE' ELSE 'OCCUPIED' END";

    private static final String SELECT_BOXES = """
            SELECT stable_box.id AS stable_box_id,
                   stable_box.box_code,
                   stable_box.yard,
                   """ + STATUS_EXPRESSION + """
             AS box_status,
                   horse.id AS current_horse_id,
                   horse.horse_code AS current_horse_code,
                   horse.name AS current_horse_name,
                   yard.yard_code,
                   stable_box.row_no,
                   stable_box.column_no,
                   stable_box.box_status AS operating_status
            FROM dbo.stable_box stable_box
            LEFT JOIN dbo.stable_yard yard
              ON yard.id = stable_box.stable_yard_id
            LEFT JOIN dbo.horse_stable_assignment assignment
              ON assignment.stable_box_id = stable_box.id
             AND assignment.effective_to IS NULL
            LEFT JOIN dbo.horse horse
              ON horse.id = assignment.horse_id
            """;

    /** Retired boxes are history only; a box outside every yard is listed only while a horse still occupies it. */
    private static final String VISIBLE = """
             WHERE stable_box.box_status <> 'RETIRED'
               AND (stable_box.stable_yard_id IS NOT NULL OR assignment.id IS NOT NULL)""";

    /** DEC-016: a free box is one that can receive a horse: inside a yard, ACTIVE and unoccupied. */
    private static final String ASSIGNABLE = """
              AND stable_box.box_status = 'ACTIVE' AND stable_box.stable_yard_id IS NOT NULL""";

    private static final String ORDER = " ORDER BY stable_box.box_code";

    private static final RowMapper<StableBoxResponse> ROW_MAPPER = (resultSet, rowNumber) -> {
        long horseId = resultSet.getLong("current_horse_id");
        StableBoxResponse.CurrentHorse currentHorse = resultSet.wasNull()
                ? null
                : new StableBoxResponse.CurrentHorse(
                        horseId,
                        resultSet.getString("current_horse_code"),
                        resultSet.getString("current_horse_name"));

        return new StableBoxResponse(
                resultSet.getLong("stable_box_id"),
                resultSet.getString("box_code"),
                resultSet.getString("yard"),
                BoxStatus.valueOf(resultSet.getString("box_status")),
                currentHorse,
                resultSet.getString("yard_code"),
                (Integer) resultSet.getObject("row_no", Integer.class),
                (Integer) resultSet.getObject("column_no", Integer.class),
                resultSet.getString("operating_status"));
    };

    private final JdbcTemplate jdbcTemplate;

    public StableBoxReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StableBoxResponse> findAll(BoxStatus status) {
        if (status == null) {
            return jdbcTemplate.query(SELECT_BOXES + VISIBLE + ORDER, ROW_MAPPER);
        }
        String extra = status == BoxStatus.FREE ? ASSIGNABLE : "";
        return jdbcTemplate.query(
                SELECT_BOXES + VISIBLE + " AND " + STATUS_EXPRESSION + " = ?" + extra + ORDER,
                ROW_MAPPER,
                status.name());
    }
}
