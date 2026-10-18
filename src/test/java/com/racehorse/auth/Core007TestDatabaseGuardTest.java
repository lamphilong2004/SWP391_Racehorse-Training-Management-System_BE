package com.racehorse.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Offline: no SQL Server connection. Proves the CORE-007 SQL suite cannot target non-test databases. */
class Core007TestDatabaseGuardTest {

    private static final String DB = "racehorse_local_core007_test_20261019_093000";
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=" + DB
            + ";encrypt=true;trustServerCertificate=false;hostNameInCertificate=TESTHOST;integratedSecurity=true";

    @Test
    void acceptsTheDedicatedStrictTlsTestDatabase() {
        assertDoesNotThrow(() -> Core007TestDatabaseGuard.checkConfiguration(DB, URL));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "racehorse_local_long_rc1_20261018_205630", // Leader's local development database
            "racehorse_local_long_20261018_202524",
            "racehorse_local_dung_20261019_101500", // another member's database
            "racehorse_demo", "racehorse_dev", "racehorse_staging",
            "racehorse_core009_test_20261017_0046",
            "racehorse_core007_test_20261018_2322", // legacy name, created by TLS-bypassing scripts
            "racehorse_local_core007_test_2026101_093000", "racehorse_local_core007_test_20261019_093000;x"})
    void rejectsEveryDatabaseThatIsNotADedicatedCore007TestDatabase(String database) {
        assertThrows(IllegalStateException.class,
                () -> Core007TestDatabaseGuard.checkConfiguration(database, URL.replace(DB, database)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:sqlserver://localhost:1433;databaseName=" + DB + ";encrypt=true;trustServerCertificate=true",
            "jdbc:sqlserver://localhost:1433;databaseName=" + DB + ";encrypt=true",
            "jdbc:sqlserver://localhost:1433;databaseName=" + DB + ";encrypt=false;trustServerCertificate=false",
            "jdbc:sqlserver://localhost:1433;databaseName=" + DB + ";trustServerCertificate=false",
            "jdbc:sqlserver://localhost:1433;databaseName=" + DB + ";encrypt=true;trustServerCertificate=false;trustServerCertificate=true",
            "jdbc:sqlserver://10.0.0.5:1433;databaseName=" + DB + ";encrypt=true;trustServerCertificate=false",
            "jdbc:sqlserver://localhost:1433;databaseName=racehorse_demo;encrypt=true;trustServerCertificate=false",
            "jdbc:sqlserver://localhost:1433;databaseName=" + DB + ";encrypt=true;trustServerCertificate=false;failoverPartner=other",
            "jdbc:sqlserver://localhost:1433;serverName=other;databaseName=" + DB + ";encrypt=true;trustServerCertificate=false"})
    void rejectsTlsBypassRemoteHostsAndMismatchedUrls(String url) {
        assertThrows(IllegalStateException.class, () -> Core007TestDatabaseGuard.checkConfiguration(DB, url));
    }

    @Test
    void requiresAnUnseededReceiptFromTheStrictTlsInstaller(@TempDir Path appData) throws Exception {
        assertThrows(IllegalStateException.class,
                () -> Core007TestDatabaseGuard.checkDisposableInstall(appData, DB, "TESTHOST"));
        Path receipt = Files.createDirectories(appData.resolve("Racehorse/LocalDb/" + DB)).resolve("receipt.json");

        Files.writeString(receipt, "{\"status\":\"INSTALLED_AND_VERIFIED\",\"database\":\"" + DB + "\",\"machine\":\"TESTHOST\",\"seeded\":false}");
        assertDoesNotThrow(() -> Core007TestDatabaseGuard.checkDisposableInstall(appData, DB, "TESTHOST"));
        assertThrows(IllegalStateException.class,
                () -> Core007TestDatabaseGuard.checkDisposableInstall(appData, DB, "OTHERHOST"));

        Files.writeString(receipt, "{\"status\":\"INSTALLED_AND_VERIFIED\",\"database\":\"" + DB + "\",\"machine\":\"TESTHOST\",\"seeded\":true}");
        assertThrows(IllegalStateException.class,
                () -> Core007TestDatabaseGuard.checkDisposableInstall(appData, DB, "TESTHOST"));

        Files.writeString(receipt, "{\"status\":\"INSTALLED_AND_VERIFIED\",\"database\":\"racehorse_local_long_rc1_20261018_205630\",\"machine\":\"TESTHOST\",\"seeded\":false}");
        assertThrows(IllegalStateException.class,
                () -> Core007TestDatabaseGuard.checkDisposableInstall(appData, DB, "TESTHOST"));
    }
}
