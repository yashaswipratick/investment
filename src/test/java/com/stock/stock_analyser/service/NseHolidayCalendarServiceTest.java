package com.stock.stock_analyser.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NseHolidayCalendarServiceTest {
    @Test void parsesOfficialStyleNseHolidayRows() {
        Document document = Jsoup.parse("<table><tr><td>1</td><td>14-Sep-2026</td><td>Monday</td><td>Ganesh Chaturthi</td></tr></table>");
        Set<LocalDate> holidays = NseHolidayCalendarService.parseHolidayDates(document, 2026);
        assertTrue(holidays.contains(LocalDate.of(2026, 9, 14)));
    }

    @Test void bundledCalendarCoversCurrentYearWhenNetworkIsUnavailable() {
        Set<LocalDate> holidays = new NseHolidayCalendarService().loadHolidayCalendar(2026);
        assertTrue(holidays.contains(LocalDate.of(2026, 9, 14)));
        assertTrue(holidays.contains(LocalDate.of(2026, 11, 8)));
    }
}
