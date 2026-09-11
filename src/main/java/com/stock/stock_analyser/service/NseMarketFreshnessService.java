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

/** Deterministic NSE freshness calculation using IST, weekends and configured holidays. */
@Component
public class NseMarketFreshnessService {
    static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");

    private final Clock clock;
    private final LocalTime marketDataClose;
    private final Set<LocalDate> holidays;

    @Autowired
    public NseMarketFreshnessService(
            @Value("${stock.analyser.freshness.market-close-time:18:30}") String marketCloseTime,
            @Value("${stock.analyser.freshness.holidays:}") String configuredHolidays) {
        this(Clock.system(NSE_ZONE), LocalTime.parse(marketCloseTime), parseHolidays(configuredHolidays));
    }

    NseMarketFreshnessService(Clock clock, LocalTime marketDataClose, Set<LocalDate> holidays) {
        this.clock = clock.withZone(NSE_ZONE);
        this.marketDataClose = marketDataClose;
        this.holidays = holidays == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(holidays));
    }

    public LocalDate analysisDate() { return LocalDate.now(clock); }

    public LocalDate expectedLatestTradingDate() {
        LocalDate date = analysisDate();
        if (isTradingDay(date) && !LocalTime.now(clock).isBefore(marketDataClose)) return date;
        return previousTradingDay(date.minusDays(1));
    }

    boolean isTradingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays.contains(date);
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
        return result;
    }
}
