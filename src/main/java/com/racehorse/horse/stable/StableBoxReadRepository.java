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
                   horse.name AS current_horse_name
            FROM dbo.stable_box stable_box
            LEFT JOIN dbo.horse_stable_assignment assignment
              ON assignment.stable_box_id = stable_box.id
             AND assignment.effective_to IS NULL
            LEFT JOIN dbo.horse horse
              ON horse.id = assignment.horse_id
            """;

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
                currentHorse);
    };

    private final JdbcTemplate jdbcTemplate;

    public StableBoxReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StableBoxResponse> findAll(BoxStatus status) {
        if (status == null) {
            return jdbcTemplate.query(
                    SELECT_BOXES + " ORDER BY stable_box.box_code",
                    ROW_MAPPER);
        }

        return jdbcTemplate.query(
                SELECT_BOXES + " WHERE " + STATUS_EXPRESSION + " = ? ORDER BY stable_box.box_code",
                ROW_MAPPER,
                status.name());
    }
}
