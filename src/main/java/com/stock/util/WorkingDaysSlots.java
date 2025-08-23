package com.stock.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class WorkingDaysSlots {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // Get last N working days as LocalDate for correct chronological sorting
    public static TreeSet<LocalDate> getLastNWorkingDays(String inputDate, int n) {
        TreeSet<LocalDate> workingDays = new TreeSet<>(); // Natural order = chronological
        LocalDate startDate = LocalDate.parse(inputDate, FORMATTER);

        LocalDate date = startDate.minusDays(1); // start from 1 day before input
        while (workingDays.size() < n) {
            if (!(date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY)) {
                workingDays.add(date);
            }
            date = date.minusDays(1);
        }
        return workingDays;
    }

    // Split into slots of given size
    /*public static List<TreeSet<LocalDate>> splitIntoSlots(TreeSet<LocalDate> dates, int slotSize) {
        List<TreeSet<LocalDate>> slots = new ArrayList<>();
        List<LocalDate> dateList = new ArrayList<>(dates);
        for (int i = 0; i < dateList.size(); i += slotSize) {
            int end = Math.min(i + slotSize, dateList.size());
            slots.add(new TreeSet<>(dateList.subList(i, end))); // Keep sorted chronologically
        }
        return slots;
    }*/

    // Split into slots of given size
    public static List<TreeSet<String>> splitIntoSlots(TreeSet<LocalDate> dates, int slotSize) {
        List<TreeSet<String>> slots = new ArrayList<>();
        List<LocalDate> dateList = new ArrayList<>(dates); // already sorted because TreeSet keeps natural order
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        for (int i = 0; i < dateList.size(); i += slotSize) {
            int end = Math.min(i + slotSize, dateList.size());

            // Create a TreeSet<String> with formatted dates (dd-MM-yyyy)
            TreeSet<String> slot = new TreeSet<>(
                    Comparator.comparing(d -> LocalDate.parse(d, formatter)) // ensure chronological order
            );

            dateList.subList(i, end).forEach(d -> slot.add(d.format(formatter)));

            slots.add(slot);
        }

        return slots;
    }

    public static List<TreeSet<String>> getDateSlots(String inputDate, int numOfDays) {
        // Example: last 200 working days from 22-08-2025
        TreeSet<LocalDate> last200WorkingDays = getLastNWorkingDays(inputDate, numOfDays);

        // Split into slots of 50
        List<TreeSet<String>> slots = splitIntoSlots(last200WorkingDays, 50);

        /*for (int i = 0; i < slots.size(); i++) {
            System.out.println("Slot " + (i + 1) + " (" + slots.get(i).size() + " dates):");
            slots.get(i).forEach(System.out::println);
            System.out.println("-----------------------------");
        }*/
        return slots;
    }

    public static void main(String[] args) {
        getDateSlots("23-08-2025", 200);
    }
}
