package com.racehorse.medical.groom;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;

final class GroomTaskDatabaseSupport {
    private GroomTaskDatabaseSupport() {}

    static boolean isSqlServer(JdbcTemplate jdbc) {
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT)
                    .contains("microsoft sql server");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not determine the database product for Groom treatment tasks.", exception);
        }
    }

    static boolean hasAssignedGroomColumn(JdbcTemplate jdbc, boolean sqlServer) {
        if (sqlServer) {
            Integer present = jdbc.queryForObject("""
                    SELECT CASE WHEN COL_LENGTH(N'dbo.care_task', N'assigned_groom_user_id') IS NULL
                                THEN 0 ELSE 1 END
                    """, Integer.class);
            return present != null && present == 1;
        }
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            try (var columns = connection.getMetaData().getColumns(null, "DBO", "CARE_TASK", "ASSIGNED_GROOM_USER_ID")) {
                if (columns.next()) return true;
            }
            try (var columns = connection.getMetaData().getColumns(null, "dbo", "care_task", "assigned_groom_user_id")) {
                return columns.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not verify the approved Groom assignment migration.", exception);
        }
    }
}
