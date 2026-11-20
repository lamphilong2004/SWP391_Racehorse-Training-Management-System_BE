package com.racehorse.billing;

import com.racehorse.billing.BillingRepository.OwnerRow;
import com.racehorse.billing.BillingRepository.Stay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingCalculationTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 30);

    @Test
    void boardingDaysCountOnlyTheOverlapWithTheBillingPeriodInclusive() {
        assertEquals(30, BillingService.boardingDays(List.of(new Stay(LocalDate.of(2026, 10, 5), null)), START, END));
        assertEquals(1, BillingService.boardingDays(List.of(new Stay(END, null)), START, END));
        // A move on the 10th: first box until the 9th, second box from the 10th. No day is counted twice.
        assertEquals(30, BillingService.boardingDays(List.of(
                new Stay(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 9)),
                new Stay(LocalDate.of(2026, 11, 10), null)), START, END));
        assertEquals(5, BillingService.boardingDays(List.of(
                new Stay(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 7))), START, END));
        assertEquals(0, BillingService.boardingDays(List.of(), START, END));
    }

    @Test
    void theManagingOwnerIsBilledAndAmbiguousOwnershipIsNotGuessed() {
        assertEquals(Optional.of(7L), BillingService.billedOwner(List.of(new OwnerRow(7, true), new OwnerRow(8, false))));
        assertEquals(Optional.of(9L), BillingService.billedOwner(List.of(new OwnerRow(9, false))));
        assertEquals(Optional.empty(), BillingService.billedOwner(List.of(new OwnerRow(7, false), new OwnerRow(8, false))));
        assertEquals(Optional.empty(), BillingService.billedOwner(List.of(new OwnerRow(7, true), new OwnerRow(8, true))));
        assertEquals(Optional.empty(), BillingService.billedOwner(List.of()));
    }
}
