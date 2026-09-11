package com.stock.stock_analyser.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Deterministic NSE freshness calculation using IST, weekends and the official holiday calendar. */
@Component
public class NseMarketFreshnessService {
    static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");

    private final Clock clock;
    private final LocalTime marketDataClose;
    private final Set<LocalDate> configuredHolidays;
    private final NseHolidayCalendarService holidayCalendarService;

    @Autowired
    public NseMarketFreshnessService(
            @Value("${stock.analyser.freshness.market-close-time:18:30}") String marketCloseTime,
            @Value("${stock.analyser.freshness.holidays:}") String configuredHolidays,
            NseHolidayCalendarService holidayCalendarService) {
        this.clock = Clock.system(NSE_ZONE);
        this.marketDataClose = LocalTime.parse(marketCloseTime);
        this.configuredHolidays = parseHolidays(configuredHolidays);
        this.holidayCalendarService = holidayCalendarService;
    }

    NseMarketFreshnessService(Clock clock, LocalTime marketDataClose, Set<LocalDate> holidays) {
        this(clock, marketDataClose, holidays, null);
    }

    NseMarketFreshnessService(Clock clock, LocalTime marketDataClose, Set<LocalDate> holidays,
                              NseHolidayCalendarService holidayCalendarService) {
        this.clock = clock.withZone(NSE_ZONE);
        this.marketDataClose = marketDataClose;
        this.configuredHolidays = holidays == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(holidays));
        this.holidayCalendarService = holidayCalendarService;
    }

    public LocalDate analysisDate() { return LocalDate.now(clock); }

    public LocalDate expectedLatestTradingDate() {
        LocalDate date = analysisDate();
        if (isTradingDay(date) && !LocalTime.now(clock).isBefore(marketDataClose)) return date;
        return previousTradingDay(date.minusDays(1));
    }

    boolean isTradingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false;
        Set<LocalDate> holidays = new HashSet<>(configuredHolidays);
        if (holidayCalendarService != null) {
            Set<LocalDate> calendar = holidayCalendarService.holidaysForYear(date.getYear());
            if (calendar == null) return false; // fail closed if calendar cannot be established
            holidays.addAll(calendar);
        }
        return !holidays.contains(date);
    }

    private LocalDate previousTradingDay(LocalDate date) {
        LocalDate candidate = date;
        while (!isTradingDay(candidate)) candidate = candidate.minusDays(1);
        return candidate;
    }

    private static Set<LocalDate> parseHolidays(String value) {
        if (value == null || value.isBlank()) return Set.of();
        Set<LocalDate> result = new HashSet<>();
        for (String token : value.split(",")) if (!token.isBlank()) result.add(LocalDate.parse(token.trim()));
        return Collections.unmodifiableSet(result);
    }
}
