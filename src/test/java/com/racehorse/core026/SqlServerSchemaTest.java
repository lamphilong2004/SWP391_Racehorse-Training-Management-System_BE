package com.racehorse.core026;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in real DB-01 constraint/locking tests using Windows-authenticated
 * sqlcmd. This verifies real SQL Server semantics, not the application's JDBC
 * connectivity. Creates/drops ONLY a uniquely named core026_test_* database.
 * Never uses an existing database.
 */
@EnabledIfEnvironmentVariable(named = "CORE026_SQLCMD_SERVER", matches = ".+")
@Timeout(60)
class SqlServerSchemaTest {
    static String database;
    static String server;
    static String executable;

    @BeforeAll
    static void prepare() throws Exception {
        server = System.getenv("CORE026_SQLCMD_SERVER");
        executable = Optional.ofNullable(System.getenv("CORE026_SQLCMD_PATH")).orElse("sqlcmd");
        database = "core026_test_" + UUID.randomUUID().toString().replace("-", "");
        run("master", "CREATE DATABASE [" + database + "];");
        try {
            run(database, Files.readString(Path.of("database/schema.sql"), StandardCharsets.UTF_8));
            run(database,
                    Files.readString(Path.of("src/test/resources/core026-sqlserver-seed.sql"), StandardCharsets.UTF_8));
        } catch (Exception | AssertionError failure) {
            cleanup();
            throw failure;
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (database == null)
            return;
        if (!database.matches("core026_test_[a-f0-9]{32}"))
            throw new IllegalStateException("Unsafe test database name");
        run("master", "IF DB_ID(N'" + database + "') IS NOT NULL BEGIN ALTER DATABASE [" + database
                + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [" + database + "]; END;");
    }

    @Test
    void canonicalConstraintsFilteredUniquenessAndUtcMilliseconds() throws Exception {
        run(database, Files.readString(Path.of("src/test/resources/core026-sqlserver-assertions.sql"),
                StandardCharsets.UTF_8));
    }

    @Test
    void sessionLockSerializesCheckWithLifecycleMutation() throws Exception {
        Path output = Files.createTempFile("core026-lock-", ".log");
        Path script = Files.createTempFile("core026-lock-", ".sql");
        String tag = "0x" + UUID.randomUUID().toString().replace("-", "");
        Files.writeString(script, "SET NOCOUNT ON; SET CONTEXT_INFO " + tag
                + ";\nGO\nBEGIN TRAN; SELECT s.id,p.horse_id,p.id,s.work_rider_user_id,s.status FROM dbo.training_session s WITH (UPDLOCK,HOLDLOCK) JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id WHERE s.id=1; WAITFOR DELAY '00:00:30'; ROLLBACK;",
                StandardCharsets.UTF_8);
        Process holder = new ProcessBuilder(command(database, script)).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
        try {
            // Observe the server lock itself; sqlcmd can buffer redirected output until it
            // exits.
            run(database,
                    "SET NOCOUNT ON; DECLARE @deadline DATETIME2=DATEADD(SECOND,10,SYSUTCDATETIME()); WHILE NOT EXISTS(SELECT 1 FROM sys.dm_tran_locks l JOIN sys.dm_exec_sessions s ON s.session_id=l.request_session_id WHERE SUBSTRING(s.context_info,1,16)="
                            + tag
                            + " AND l.request_status='GRANT' AND l.request_mode IN('U','X','RangeS-U')) BEGIN IF SYSUTCDATETIME()>@deadline THROW 51000,'Lock holder did not acquire the row lock',1; WAITFOR DELAY '00:00:00.050'; END;");
            assertTrue(holder.isAlive(), Files.readString(output));
            run(database,
                    "SET LOCK_TIMEOUT 500; BEGIN TRY UPDATE dbo.training_session SET status='IN_PROGRESS' WHERE id=1; THROW 51000,'Concurrent lifecycle mutation bypassed session lock',1; END TRY BEGIN CATCH IF ERROR_NUMBER()<>1222 THROW; END CATCH;");
            assertTrue(holder.waitFor(35, TimeUnit.SECONDS));
            assertEquals(0, holder.exitValue(), Files.readString(output));
        } finally {
            if (holder.isAlive()) {
                holder.destroyForcibly();
                holder.waitFor(10, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(script);
            Files.deleteIfExists(output);
        }
    }

    static List<String> command(String db, Path script) {
        return List.of(executable, "-S", server, "-E", "-C", "-I", "-b", "-r", "1", "-l", "5", "-f", "65001", "-d", db,
                "-i", script.toAbsolutePath().toString());
    }

    static void run(String db, String sql) throws Exception {
        Path script = Files.createTempFile("core026-sql-", ".sql");
        Path output = Files.createTempFile("core026-sql-", ".log");
        Process process = null;
        try {
            Files.writeString(script, sql, StandardCharsets.UTF_8);
            process = new ProcessBuilder(command(db, script)).redirectErrorStream(true).redirectOutput(output.toFile())
                    .start();
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "sqlcmd timed out");
            assertEquals(0, process.exitValue(), Files.readString(output));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(script);
            Files.deleteIfExists(output);
        }
    }
}
