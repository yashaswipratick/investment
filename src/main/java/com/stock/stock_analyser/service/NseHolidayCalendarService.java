package com.stock.stock_analyser.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Resolves NSE equity holidays from the official page with a deterministic fallback. */
@Component
public class NseHolidayCalendarService {
    private static final String NSE_HOLIDAY_URL = "https://www.nseindia.com/resources/exchange-communication-holidays";
    private final Map<Integer, Set<LocalDate>> cache = new HashMap<>();

    public synchronized Set<LocalDate> holidaysForYear(int year) {
        return cache.computeIfAbsent(year, this::loadHolidayCalendar);
    }

    Set<LocalDate> loadHolidayCalendar(int year) {
        try {
            Document document = Jsoup.connect(NSE_HOLIDAY_URL)
                    .userAgent("Mozilla/5.0 Marcus/1.0")
                    .timeout(5000)
                    .get();
            Set<LocalDate> parsed = parseHolidayDates(document, year);
            if (!parsed.isEmpty()) return Set.copyOf(parsed);
        } catch (Exception ignored) {
            // Keep freshness deterministic when NSE is temporarily unavailable.
        }
        return Set.copyOf(BUNDLED_HOLIDAYS.getOrDefault(year, Set.of()));
    }

    static Set<LocalDate> parseHolidayDates(Document document, int year) {
        Set<LocalDate> result = new HashSet<>();
        if (document == null) return result;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
        Elements rows = document.select("tr");
        for (Element row : rows) {
            for (String token : row.text().split("\\s+")) {
                if (!token.matches("\\d{2}-[A-Za-z]{3}-" + year)) continue;
                try { result.add(LocalDate.parse(token, formatter)); }
                catch (RuntimeException ignored) { }
            }
        }
        return result;
    }

    private static final Map<Integer, Set<LocalDate>> BUNDLED_HOLIDAYS = Map.of(
            2026, Set.of(
                    LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 26),
                    LocalDate.of(2026, 2, 19), LocalDate.of(2026, 3, 3),
                    LocalDate.of(2026, 3, 19), LocalDate.of(2026, 3, 26),
                    LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),
                    LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 14),
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 28),
                    LocalDate.of(2026, 6, 26), LocalDate.of(2026, 8, 26),
                    LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 2),
                    LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 8),
                    LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 24),
                    LocalDate.of(2026, 12, 25)
            )
    );
}
