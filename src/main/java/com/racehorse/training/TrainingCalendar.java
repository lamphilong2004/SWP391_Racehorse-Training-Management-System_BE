package com.racehorse.training;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Scheduling uses the club's calendar, independently of the server's timezone. */
@Component
public class TrainingCalendar {
    public static final ZoneId CLUB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Clock clock;

    public TrainingCalendar() { this(Clock.systemUTC()); }

    TrainingCalendar(Clock clock) { this.clock = clock; }

    public Instant now() { return clock.instant(); }

    public LocalDate today() { return now().atZone(CLUB_ZONE).toLocalDate(); }

    public void requireProgramStart(LocalDate start) {
        if (start.isBefore(today())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "periodStart must be today or later in Asia/Ho_Chi_Minh.");
        }
    }

    public void requireFutureSession(Instant start) {
        if (!start.isAfter(now())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "scheduledStart must be in the future. Choose a later time or reschedule the session.");
        }
    }
}
