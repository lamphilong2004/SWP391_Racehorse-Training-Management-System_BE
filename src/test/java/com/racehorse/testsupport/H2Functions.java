package com.racehorse.testsupport;

import java.sql.Timestamp;

/** SQL Server functions that H2 (MSSQLServer mode) lacks; registered by test schema.sql. */
public final class H2Functions {

    private H2Functions() {
    }

    /** H2 stand-in for SQL Server SYSUTCDATETIME(); same clock semantics as H2 CURRENT_TIMESTAMP. */
    public static Timestamp sysUtcDateTime() {
        return new Timestamp(System.currentTimeMillis());
    }
}
