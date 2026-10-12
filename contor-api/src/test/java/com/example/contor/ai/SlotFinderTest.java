package com.example.contor.ai;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlotFinderTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final SlotFinder.WorkingHours OFFICE = new SlotFinder.WorkingHours(
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(9, 0), LocalTime.of(18, 0));
    /** Monday 12 October 2026, 07:00 in Berlin. */
    private static final Instant MONDAY_MORNING = Instant.parse("2026-10-12T05:00:00Z");

    private static List<String> starts(List<SlotFinder.Slot> slots) {
        return slots.stream().map(slot -> slot.start().toLocalDateTime().toString()).toList();
    }

    private static SlotFinder.Busy busy(String start, String end) {
        return new SlotFinder.Busy(Instant.parse(start), Instant.parse(end));
    }

    private static List<SlotFinder.Slot> find(List<SlotFinder.Busy> busy, LocalDate from, LocalDate to, Instant now, int max) {
        return SlotFinder.freeSlots(busy, OFFICE, from, to, Duration.ofHours(1), now, ZONE, max);
    }

    @Test
    void offers_the_earliest_free_hour_of_each_working_day_skipping_weekends() {
        List<SlotFinder.Slot> slots = find(List.of(), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 20), MONDAY_MORNING, 5);

        assertThat(starts(slots)).containsExactly("2026-10-15T09:00", "2026-10-16T09:00", "2026-10-19T09:00", "2026-10-20T09:00");
        assertThat(slots.getFirst().end().toLocalTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void appointments_push_the_slot_to_the_next_free_half_hour_after_them() {
        // 09:00–11:15 Berlin is busy on Thursday
        List<SlotFinder.Slot> slots = find(List.of(busy("2026-10-15T07:00:00Z", "2026-10-15T09:15:00Z")),
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15), MONDAY_MORNING, 3);

        assertThat(starts(slots)).containsExactly("2026-10-15T11:30");
    }

    @Test
    void a_day_without_an_hour_free_until_closing_offers_nothing() {
        List<SlotFinder.Slot> slots = find(List.of(busy("2026-10-15T07:00:00Z", "2026-10-15T15:30:00Z")),
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15), MONDAY_MORNING, 3);

        assertThat(slots).isEmpty();
    }

    @Test
    void today_starts_after_now_and_at_most_max_slots_are_offered() {
        // Monday 13:10 Berlin
        Instant now = Instant.parse("2026-10-12T11:10:00Z");

        List<SlotFinder.Slot> slots = find(List.of(), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 30), now, 2);

        assertThat(starts(slots)).containsExactly("2026-10-12T13:30", "2026-10-13T09:00");
    }

    @Test
    void an_all_day_event_blocks_its_whole_day() {
        SlotFinder.Busy holiday = SlotFinder.Busy.allDay(LocalDate.of(2026, 10, 15), ZONE);

        List<SlotFinder.Slot> slots = find(List.of(holiday), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16), MONDAY_MORNING, 3);

        assertThat(starts(slots)).containsExactly("2026-10-16T09:00");
    }

    @Test
    void the_time_of_day_narrows_the_hours_and_evening_lies_after_work() {
        LocalDate thursday = LocalDate.of(2026, 10, 15);

        assertThat(starts(SlotFinder.freeSlots(List.of(), SlotFinder.TimeOfDay.AFTERNOON.narrow(OFFICE), thursday, thursday,
                Duration.ofHours(1), MONDAY_MORNING, ZONE, 1))).containsExactly("2026-10-15T12:00");
        assertThat(starts(SlotFinder.freeSlots(List.of(), SlotFinder.TimeOfDay.EVENING.narrow(OFFICE), thursday, thursday,
                Duration.ofHours(1), MONDAY_MORNING, ZONE, 1))).containsExactly("2026-10-15T18:00");
        assertThat(SlotFinder.TimeOfDay.MORNING.narrow(OFFICE).end()).isEqualTo(LocalTime.of(12, 0));
        assertThat(SlotFinder.TimeOfDay.ANY.narrow(OFFICE)).isEqualTo(OFFICE);
    }

    @Test
    void a_slot_describes_itself_for_the_prompt() {
        List<SlotFinder.Slot> slots = find(List.of(), LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13), MONDAY_MORNING, 1);

        assertThat(slots.getFirst().describe()).isEqualTo("Tuesday, 13 October 2026, 09:00–10:00");
    }

    @Test
    void working_hours_stay_local_across_the_change_from_summer_time() {
        // 25 October 2026 is a Sunday; Monday 26 is winter time (UTC+1)
        List<SlotFinder.Slot> slots = find(List.of(), LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 26), MONDAY_MORNING, 3);

        assertThat(starts(slots)).containsExactly("2026-10-23T09:00", "2026-10-26T09:00");
        assertThat(slots.getLast().start().toInstant()).isEqualTo(Instant.parse("2026-10-26T08:00:00Z"));
    }
}
