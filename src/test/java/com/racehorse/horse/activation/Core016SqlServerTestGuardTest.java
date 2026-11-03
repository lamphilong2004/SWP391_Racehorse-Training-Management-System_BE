package com.racehorse.horse.activation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Core016SqlServerTestGuardTest {

    private static final String DATABASE = "racehorse_core016_test_20261019_1355";
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=" + DATABASE
            + ";encrypt=true;trustServerCertificate=false";

    @Test
    void acceptsOnlyAnApprovedLocalDedicatedStrictTlsTarget() {
        Core016SqlServerTestGuard.Configuration configuration =
                Core016SqlServerTestGuard.validateConfiguration(
                        "true", DATABASE, URL, "DUNG-LAPTOP", "test-user", "test-password");

        assertEquals(DATABASE, configuration.database());
        assertEquals("DUNG-LAPTOP", configuration.expectedServer());
        assertEquals(URL, configuration.url());
    }

    @Test
    void rejectsMissingOperatorApproval() {
        assertThrows(IllegalStateException.class, () -> validate("false", DATABASE, URL));
    }

    @Test
    void rejectsNonDedicatedDatabaseNames() {
        assertThrows(IllegalStateException.class, () -> validate("true", "racehorse_dev", URL));
    }

    @Test
    void rejectsRemoteSqlServerTargets() {
        assertThrows(IllegalStateException.class,
                () -> validate("true", DATABASE, URL.replace("localhost", "sql.example.test")));
    }

    @Test
    void rejectsTlsBypassOrDisabledEncryption() {
        assertThrows(IllegalStateException.class,
                () -> validate("true", DATABASE, URL.replace("trustServerCertificate=false",
                        "trustServerCertificate=true")));
        assertThrows(IllegalStateException.class,
                () -> validate("true", DATABASE, URL.replace("encrypt=true", "encrypt=false")));
    }

    @Test
    void rejectsDatabaseMismatchAndDuplicateProperties() {
        assertThrows(IllegalStateException.class,
                () -> validate("true", DATABASE, URL.replace(DATABASE, "racehorse_demo")));
        assertThrows(IllegalStateException.class,
                () -> validate("true", DATABASE, URL + ";encrypt=true"));
    }

    private static void validate(String approval, String database, String url) {
        Core016SqlServerTestGuard.validateConfiguration(
                approval, database, url, "DUNG-LAPTOP", "", "");
    }
}
