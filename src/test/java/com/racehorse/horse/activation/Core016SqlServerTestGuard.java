package com.racehorse.horse.activation;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class Core016SqlServerTestGuard {

    private static final Pattern DATABASE_NAME =
            Pattern.compile("racehorse_core016_test_[0-9]{8}_[0-9]{4}");
    private static final Pattern LOCAL_URL =
            Pattern.compile("(?i)^jdbc:sqlserver://(localhost|127\\.0\\.0\\.1)(:[0-9]{1,5})?;(.*)$");

    private Core016SqlServerTestGuard() {
    }

    static Configuration configuration() {
        return validateConfiguration(
                System.getenv("CORE016_SQLSERVER_APPROVED"),
                System.getenv("CORE016_SQLSERVER_DATABASE"),
                System.getenv("CORE016_SQLSERVER_URL"),
                System.getenv("CORE016_SQLSERVER_EXPECTED_SERVER"),
                environmentOrEmpty("CORE016_SQLSERVER_USERNAME"),
                environmentOrEmpty("CORE016_SQLSERVER_PASSWORD"));
    }

    static Configuration validateConfiguration(
            String approved,
            String database,
            String url,
            String expectedServer,
            String username,
            String password) {
        if (!"true".equalsIgnoreCase(approved)) {
            throw new IllegalStateException("CORE016_SQLSERVER_APPROVED must be true after operator approval.");
        }
        if (database == null || !DATABASE_NAME.matcher(database).matches()) {
            throw new IllegalStateException(
                    "CORE016_SQLSERVER_DATABASE must be a dedicated racehorse_core016_test_YYYYMMDD_HHmm database.");
        }
        var matcher = url == null ? null : LOCAL_URL.matcher(url);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalStateException("CORE016_SQLSERVER_URL must target local SQL Server over TCP.");
        }
        Map<String, String> properties = properties(matcher.group(3));
        if (!database.equalsIgnoreCase(properties.get("databasename"))
                || properties.containsKey("failoverpartner")
                || properties.containsKey("servername")
                || properties.containsKey("user")
                || properties.containsKey("username")
                || properties.containsKey("password")) {
            throw new IllegalStateException("CORE016_SQLSERVER_URL must target only the approved database without URL credentials or failover.");
        }
        if (!"true".equalsIgnoreCase(properties.get("encrypt"))
                || !"false".equalsIgnoreCase(properties.get("trustservercertificate"))) {
            throw new IllegalStateException(
                    "CORE016_SQLSERVER_URL must use encrypt=true and trustServerCertificate=false.");
        }
        if (expectedServer == null || expectedServer.isBlank()) {
            throw new IllegalStateException("CORE016_SQLSERVER_EXPECTED_SERVER is required.");
        }
        return new Configuration(expectedServer, database, url,
                username == null ? "" : username, password == null ? "" : password);
    }

    static void verifyConnection(JdbcTemplate jdbc, Configuration configuration) {
        String actualServer = jdbc.queryForObject(
                "SELECT CONVERT(nvarchar(128), SERVERPROPERTY('ServerName'))", String.class);
        String actualDatabase = jdbc.queryForObject("SELECT DB_NAME()", String.class);
        if (!configuration.expectedServer().equalsIgnoreCase(actualServer)
                || !configuration.database().equalsIgnoreCase(actualDatabase)) {
            throw new IllegalStateException("Connected SQL Server identity does not match the approved CORE-016 target.");
        }
        Map<String, Object> session = jdbc.queryForMap(
                "SELECT encrypt_option, net_transport FROM sys.dm_exec_connections WHERE session_id = @@SPID");
        if (!"TRUE".equalsIgnoreCase(String.valueOf(session.get("encrypt_option")))
                || !"TCP".equalsIgnoreCase(String.valueOf(session.get("net_transport")))) {
            throw new IllegalStateException("CORE-016 SQL tests require an encrypted TCP session.");
        }
    }

    static void verifySchemaAndEmptyHorseData(JdbcTemplate jdbc) {
        String[][] requiredColumns = {
                {"role", "id"}, {"role", "role_code"}, {"role", "display_name"},
                {"app_user", "id"}, {"app_user", "user_code"}, {"app_user", "full_name"},
                {"app_user", "email"}, {"app_user", "account_status"},
                {"user_role", "user_id"}, {"user_role", "role_id"}, {"user_role", "assigned_at"},
                {"horse", "id"}, {"horse", "horse_code"}, {"horse", "name"}, {"horse", "lifecycle"},
                {"horse", "health_status"}, {"horse", "sire_horse_id"}, {"horse", "sire_name"},
                {"horse", "dam_horse_id"}, {"horse", "dam_name"}, {"horse", "activated_at"},
                {"horse_ownership", "horse_id"}, {"horse_ownership", "owner_user_id"},
                {"horse_ownership", "effective_to"},
                {"stable_box", "id"}, {"horse_stable_assignment", "horse_id"},
                {"horse_stable_assignment", "stable_box_id"}, {"horse_stable_assignment", "effective_to"},
                {"veterinary_examination", "horse_id"}, {"veterinary_examination", "medical_case_id"},
                {"veterinary_examination", "exam_type"}, {"veterinary_examination", "health_status_set"},
                {"weight_record", "horse_id"}, {"weight_record", "context"}
        };
        for (String[] column : requiredColumns) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = ? AND COLUMN_NAME = ?
                    """, Integer.class, column[0], column[1]);
            if (count == null || count != 1) {
                throw new IllegalStateException("Missing canonical column dbo." + column[0] + "." + column[1] + ".");
            }
        }
        for (String table : new String[]{
                "horse", "horse_ownership", "horse_stable_assignment",
                "veterinary_examination", "weight_record"}) {
            Long rows = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo." + table, Long.class);
            if (rows == null || rows != 0) {
                throw new IllegalStateException("The dedicated CORE-016 SQL test database must have no rows in dbo." + table + ".");
            }
        }
    }

    private static Map<String, String> properties(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : text.split(";")) {
            int separator = part.indexOf('=');
            if (separator > 0) {
                String key = part.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                if (values.put(key, part.substring(separator + 1).trim()) != null) {
                    throw new IllegalStateException("Duplicate JDBC property in CORE016_SQLSERVER_URL: " + key);
                }
            }
        }
        return values;
    }

    private static String environmentOrEmpty(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    record Configuration(String expectedServer, String database, String url, String username, String password) {
    }
}
