package com.stock.stock_analyser.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NseHolidayCalendarServiceTest {
    @Test void parserRejectsPartialCalendar() {
        Document document = Jsoup.parse("<table><tr><td>14-Sep-2026</td></tr><tr><td>2-Oct-2026</td></tr></table>");
        assertTrue(NseHolidayCalendarService.parseAndValidateHolidayDates(document, 2026).isEmpty());
    }

    @Test void parserAcceptsCompleteCalendarForRequestedYear() {
        StringBuilder html = new StringBuilder("<table>");
        for (int day = 1; day <= 15; day++) html.append("<tr><td>").append(String.format("%02d-Jan-2026", day)).append("</td></tr>");
        html.append("</table>");
        Set<LocalDate> holidays = NseHolidayCalendarService.parseAndValidateHolidayDates(Jsoup.parse(html.toString()), 2026);
        assertFalse(holidays.isEmpty());
        assertTrue(holidays.stream().allMatch(d -> d.getYear() == 2026));
    }

    @Test void unsupportedYearFailsClosed() {
        assertNull(new NseHolidayCalendarService().loadHolidayCalendar(2027));
    }
}
