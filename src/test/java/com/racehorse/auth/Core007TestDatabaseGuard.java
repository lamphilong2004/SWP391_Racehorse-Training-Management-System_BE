package com.racehorse.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fail-closed guard for the CORE-007 SQL Server integration suite.
 *
 * <p>The suite writes rows, so it may only run against a dedicated, disposable CORE-007 test database:
 * {@code racehorse_local_core007_test_YYYYMMDD_HHMMSS}, freshly created by the strict-TLS installer
 * ({@code scripts/db/Install-LocalDatabase.ps1}) and never seeded or used for development. Local development,
 * staging, demo and other members' databases are rejected by name before any query runs. The database is
 * disposable by design: rows use unique {@code @example.test} identities and nothing is deleted, truncated or
 * reset by the tests; the whole database is retired after the gate instead of being cleaned in place.
 */
final class Core007TestDatabaseGuard {

    static final Pattern DATABASE_NAME = Pattern.compile("racehorse_local_core007_test_[0-9]{8}_[0-9]{6}");
    private static final Pattern LOCAL_URL =
            Pattern.compile("(?i)^jdbc:sqlserver://(localhost|127\\.0\\.0\\.1)(:[0-9]{1,5})?;(.*)$");

    private Core007TestDatabaseGuard() {
    }

    /** Validates the declared database name and JDBC URL before any connection is used. */
    static void checkConfiguration(String expectedDatabase, String url) {
        if (expectedDatabase == null || !DATABASE_NAME.matcher(expectedDatabase).matches()) {
            throw new IllegalStateException(
                    "CORE007_SQLSERVER_DATABASE must be a dedicated racehorse_local_core007_test_YYYYMMDD_HHMMSS database.");
        }
        var matcher = url == null ? null : LOCAL_URL.matcher(url);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalStateException("CORE007_SQLSERVER_URL must target the local SQL Server instance.");
        }
        Map<String, String> properties = properties(matcher.group(3));
        if (properties.containsKey("failoverpartner") || properties.containsKey("servername")) {
            throw new IllegalStateException("Failover or alternate server names are not allowed for the CORE-007 test database.");
        }
        if (!expectedDatabase.equals(properties.get("databasename"))) {
            throw new IllegalStateException("CORE007_SQLSERVER_URL databaseName must equal CORE007_SQLSERVER_DATABASE.");
        }
        if (!"true".equalsIgnoreCase(properties.get("encrypt"))) {
            throw new IllegalStateException("CORE007_SQLSERVER_URL must set encrypt=true.");
        }
        if (!"false".equalsIgnoreCase(properties.get("trustservercertificate"))) {
            throw new IllegalStateException("CORE007_SQLSERVER_URL must set trustServerCertificate=false (no TLS bypass).");
        }
    }

    /** Confirms the live connection: exact database, local machine and an encrypted TCP session. */
    static void checkConnection(JdbcTemplate jdbcTemplate, String expectedDatabase, String localMachine) {
        String actualDatabase = jdbcTemplate.queryForObject("SELECT DB_NAME()", String.class);
        if (!expectedDatabase.equals(actualDatabase)) {
            throw new IllegalStateException("Connected database does not match CORE007_SQLSERVER_DATABASE.");
        }
        String connectedMachine = jdbcTemplate.queryForObject(
                "SELECT CAST(SERVERPROPERTY('ComputerNamePhysicalNetBIOS') AS nvarchar(128))", String.class);
        if (localMachine == null || !localMachine.equalsIgnoreCase(connectedMachine)) {
            throw new IllegalStateException("Connected SQL Server instance is not on the local test machine.");
        }
        Map<String, Object> session = jdbcTemplate.queryForMap(
                "SELECT encrypt_option, net_transport FROM sys.dm_exec_connections WHERE session_id = @@SPID");
        if (!"TRUE".equalsIgnoreCase(String.valueOf(session.get("encrypt_option")))
                || !"TCP".equalsIgnoreCase(String.valueOf(session.get("net_transport")))) {
            throw new IllegalStateException("The CORE-007 test connection must be an encrypted TCP session.");
        }
    }

    /**
     * Requires the strict-TLS installer receipt for this exact database and machine, unseeded: proof that the
     * database was created fresh for the gate and is not a development database.
     */
    static void checkDisposableInstall(Path localAppData, String expectedDatabase, String localMachine) {
        Path receipt = localAppData.resolve("Racehorse").resolve("LocalDb").resolve(expectedDatabase).resolve("receipt.json");
        if (!Files.isRegularFile(receipt)) {
            throw new IllegalStateException("No strict-TLS installer receipt for the CORE-007 test database.");
        }
        JsonNode root;
        try {
            // The installer runs on Windows PowerShell 5.1, whose UTF-8 output starts with a byte-order mark.
            String text = Files.readString(receipt);
            root = JsonMapper.builder().build().readTree(text.startsWith("\uFEFF") ? text.substring(1) : text);
        } catch (Exception exception) {
            throw new IllegalStateException("The CORE-007 test database installer receipt is unreadable.");
        }
        if (!"INSTALLED_AND_VERIFIED".equals(root.path("status").asString())
                || !expectedDatabase.equals(root.path("database").asString())
                || localMachine == null || !localMachine.equalsIgnoreCase(root.path("machine").asString())) {
            throw new IllegalStateException("The installer receipt does not match this CORE-007 test database and machine.");
        }
        if (root.path("seeded").asBoolean(true)) {
            throw new IllegalStateException("A seeded database is not a disposable CORE-007 test database.");
        }
    }

    private static Map<String, String> properties(String text) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (String part : text.split(";")) {
            int separator = part.indexOf('=');
            if (separator > 0) {
                String key = part.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                if (properties.put(key, part.substring(separator + 1).trim()) != null) {
                    throw new IllegalStateException("Duplicate JDBC property in CORE007_SQLSERVER_URL: " + key);
                }
            }
        }
        return properties;
    }
}
