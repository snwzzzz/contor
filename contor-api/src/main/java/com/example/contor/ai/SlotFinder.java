package com.example.contor.ai;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Free time for a meeting, from the user's appointments: the earliest free slot of each working day in a date range,
 * starting on the full or half hour, inside working hours (local time, so it stays 09:00 across daylight saving), never
 * in the past. One slot per day spreads the offers ("Tue 9:00, Wed 9:00, Thu 11:30") instead of three on one morning.
 */
public final class SlotFinder {

    private static final Duration STEP = Duration.ofMinutes(30);

    private SlotFinder() {
    }

    public record WorkingHours(Set<DayOfWeek> days, LocalTime start, LocalTime end) {
    }

    /** When in the day the sender wants to meet; EVENING lies after work (a dinner), the others narrow working hours. */
    public enum TimeOfDay {
        ANY, MORNING, AFTERNOON, EVENING;

        private static final LocalTime NOON = LocalTime.of(12, 0);
        private static final LocalTime EVENING_START = LocalTime.of(18, 0);
        private static final LocalTime EVENING_END = LocalTime.of(22, 0);

        /** The hours to search on the same days. */
        public WorkingHours narrow(WorkingHours hours) {
            return switch (this) {
                case ANY -> hours;
                case MORNING -> new WorkingHours(hours.days(), hours.start(), hours.end().isBefore(NOON) ? hours.end() : NOON);
                case AFTERNOON -> new WorkingHours(hours.days(), hours.start().isAfter(NOON) ? hours.start() : NOON, hours.end());
                case EVENING -> new WorkingHours(hours.days(), EVENING_START, EVENING_END);
            };
        }
    }

    /** A time the user is not free; {@code end} after {@code start}. */
    public record Busy(Instant start, Instant end) {

        /** A whole local day (an all-day appointment). */
        public static Busy allDay(LocalDate date, ZoneId zone) {
            return new Busy(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
        }
    }

    public record Slot(ZonedDateTime start, ZonedDateTime end) {

        private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

        /** "Tuesday, 13 October 2026, 11:00–12:00": the model writes it in the reply's language and format. */
        public String describe() {
            return DAY.format(start) + ", " + TIME.format(start) + "–" + TIME.format(end);
        }
    }

    /** Up to {@code max} slots of {@code length}, one per working day from {@code from} to {@code to} (inclusive). */
    public static List<Slot> freeSlots(List<Busy> busy, WorkingHours hours, LocalDate from, LocalDate to, Duration length,
                                       Instant now, ZoneId zone, int max) {
        List<Slot> slots = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to) && slots.size() < max; date = date.plusDays(1)) {
            if (!hours.days().contains(date.getDayOfWeek())) {
                continue;
            }
            ZonedDateTime close = date.atTime(hours.end()).atZone(zone);
            ZonedDateTime cursor = roundUp(later(date.atTime(hours.start()).atZone(zone), now.atZone(zone)));
            while (!cursor.plus(length).isAfter(close)) {
                Instant start = cursor.toInstant();
                Instant end = start.plus(length);
                Instant blockedUntil = busy.stream()
                        .filter(appointment -> appointment.start().isBefore(end) && appointment.end().isAfter(start))
                        .map(Busy::end)
                        .max(Instant::compareTo)
                        .orElse(null);
                if (blockedUntil == null) {
                    slots.add(new Slot(cursor, cursor.plus(length)));
                    break;
                }
                cursor = roundUp(blockedUntil.atZone(zone));
            }
        }
        return slots;
    }

    private static ZonedDateTime later(ZonedDateTime a, ZonedDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    /** The next full or half hour at or after {@code time}. */
    private static ZonedDateTime roundUp(ZonedDateTime time) {
        ZonedDateTime hour = time.truncatedTo(ChronoUnit.HOURS);
        while (hour.isBefore(time)) {
            hour = hour.plus(STEP);
        }
        return hour;
    }
}
