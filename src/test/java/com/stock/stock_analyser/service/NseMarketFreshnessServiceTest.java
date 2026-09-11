package com.stock.stock_analyser.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NseMarketFreshnessServiceTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private NseMarketFreshnessService service(String instant, Set<LocalDate> holidays) {
        return new NseMarketFreshnessService(Clock.fixed(Instant.parse(instant), IST), LocalTime.of(18, 30), holidays);
    }

    @Test void weekendUsesFriday() {
        assertEquals(LocalDate.of(2026, 9, 11), service("2026-09-12T12:00:00Z", Set.of()).expectedLatestTradingDate());
    }

    @Test void mondayUsesPreviousFriday() {
        assertEquals(LocalDate.of(2026, 9, 11), service("2026-09-14T05:00:00Z", Set.of()).expectedLatestTradingDate());
    }

    @Test void weekdayBeforeCloseUsesPreviousTradingDay() {
        assertEquals(LocalDate.of(2026, 9, 10), service("2026-09-11T11:00:00Z", Set.of()).expectedLatestTradingDate());
    }

    @Test void weekdayAfterCloseUsesToday() {
        assertEquals(LocalDate.of(2026, 9, 11), service("2026-09-11T14:00:00Z", Set.of()).expectedLatestTradingDate());
    }

    @Test void configuredHolidayIsSkipped() {
        assertEquals(LocalDate.of(2026, 9, 10), service("2026-09-11T14:00:00Z", Set.of(LocalDate.of(2026, 9, 11))).expectedLatestTradingDate());
    }
}
