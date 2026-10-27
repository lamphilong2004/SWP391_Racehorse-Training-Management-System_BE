package com.racehorse.training;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TrainingCalendarTest {
    private final TrainingCalendar calendar = new TrainingCalendar(
            Clock.fixed(Instant.parse("2026-10-27T17:00:00Z"), ZoneOffset.UTC));

    @Test void programDatesFollowVietnamMidnightAndDoNotImposeASixMonthLimit() {
        assertEquals(LocalDate.parse("2026-10-28"), calendar.today());
        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> calendar.requireProgramStart(LocalDate.parse("2026-10-27"))).code());
        assertDoesNotThrow(() -> calendar.requireProgramStart(LocalDate.parse("2026-10-28")));
        assertDoesNotThrow(() -> calendar.requireProgramStart(LocalDate.parse("2027-10-28")));
    }

    @Test void sessionStartMustBeStrictlyInTheFutureRegardlessOfCalendarDay() {
        assertThrows(BusinessException.class, () -> calendar.requireFutureSession(calendar.now().minusSeconds(1)));
        assertThrows(BusinessException.class, () -> calendar.requireFutureSession(calendar.now()));
        assertDoesNotThrow(() -> calendar.requireFutureSession(calendar.now().plusSeconds(1)));
        assertDoesNotThrow(() -> calendar.requireFutureSession(calendar.now().plusSeconds(86400)));
    }
}
