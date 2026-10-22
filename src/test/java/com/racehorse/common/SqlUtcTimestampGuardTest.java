package com.racehorse.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * DATETIME2 instants are stored as UTC (schema defaults use SYSUTCDATETIME()). SQL Server CURRENT_TIMESTAMP is the
 * server's local time, so on a UTC+7 machine it skewed reported_at/actual_start/etc. by seven hours. H2 tests cannot
 * catch that, so this guard rejects CURRENT_TIMESTAMP in production SQL except for date-only CAST(... AS DATE) scopes
 * and the dialect-aware Groom service.
 */
class SqlUtcTimestampGuardTest {

    private static final Pattern INSTANT_USE = Pattern.compile("(?<!CAST\\()CURRENT_TIMESTAMP");
    private static final String DIALECT_AWARE = "GroomTreatmentTaskService.java";

    @Test
    void productionSqlUsesUtcInstants() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            List<String> offenders = files
                    .filter(p -> p.toString().endsWith(".java") && !p.endsWith(DIALECT_AWARE))
                    .filter(p -> INSTANT_USE.matcher(read(p)).find())
                    .map(Path::toString)
                    .toList();
            assertEquals(List.of(), offenders, "Use SYSUTCDATETIME() for instants; CURRENT_TIMESTAMP is server-local time");
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
