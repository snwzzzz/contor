package com.example.contor.service;

import com.example.contor.ai.SlotFinder;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.config.AiProperties;
import com.example.contor.config.AvailabilityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    /** Monday 12 October 2026, 07:00 in Berlin. */
    private static final Instant NOW = Instant.parse("2026-10-12T05:00:00Z");

    @Mock
    private CollectionSchemaService collectionSchemaService;

    @Mock
    private RecordService recordService;

    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        AiProperties ai = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 8192, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Berlin"));
        AvailabilityProperties availability = new AvailabilityProperties(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), LocalTime.of(9, 0), LocalTime.of(18, 0), 60, 10, 3);
        service = new AvailabilityService(collectionSchemaService, recordService, availability, ai, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RecordDto event(long id, Map<String, Object> values) {
        return new RecordDto(id, 3L, new LinkedHashMap<>(values), new LinkedHashMap<>(), OffsetDateTime.parse("2026-01-01T00:00:00Z"), "USER");
    }

    private void events(RecordDto... events) {
        when(collectionSchemaService.findAll()).thenReturn(List.of(new CollectionDto().id(3L).slug("events").name("Calendar")));
        when(recordService.listRecords(eq(3L), anyList(), any(), eq("starts_at"), eq(0), anyInt()))
                .thenReturn(new RecordPageDto(new ArrayList<>(List.of(events)), 0, events.length, (long) events.length, 1));
    }

    private static List<String> starts(List<SlotFinder.Slot> slots) {
        return slots.stream().map(slot -> slot.start().toLocalDateTime().toString()).toList();
    }

    @Test
    void freeSlots_AvoidTheUsersAppointmentsAndAllDayEvents() {
        events(event(1, Map.of("starts_at", "2026-10-13T09:00:00+02:00", "ends_at", "2026-10-13T11:00:00+02:00")),
                event(2, Map.of("starts_at", "2026-10-14T00:00:00+02:00", "all_day", true)),
                event(3, Map.of("starts_at", "2026-10-15T09:00:00+02:00")));

        List<SlotFinder.Slot> slots = service.freeSlots(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 16));

        // Tuesday after the 2-hour meeting, Wednesday is blocked, Thursday after the 1-hour default length
        assertThat(starts(slots)).containsExactly("2026-10-13T11:00", "2026-10-15T10:00", "2026-10-16T09:00");
    }

    @Test
    void freeSlots_ForTheEveningLookAfterWork() {
        events();

        assertThat(starts(service.freeSlots(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13), SlotFinder.TimeOfDay.EVENING)))
                .containsExactly("2026-10-13T18:00");
    }

    @Test
    void freeSlots_WithoutADateRangeLookAtTheNextWorkingDays() {
        events();

        List<SlotFinder.Slot> slots = service.freeSlots(null, null);

        assertThat(starts(slots)).containsExactly("2026-10-12T09:00", "2026-10-13T09:00", "2026-10-14T09:00");
    }

    @Test
    void freeSlots_WithoutACalendarEveryWorkingHourIsFree() {
        when(collectionSchemaService.findAll()).thenReturn(List.of());

        assertThat(service.freeSlots(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13))).hasSize(1);
    }

    @Test
    void freeSlots_EventsWithUnreadableDatesAreIgnored() {
        events(event(1, Map.of("starts_at", "next Tuesday")));

        assertThat(starts(service.freeSlots(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13)))).containsExactly("2026-10-13T09:00");
    }
}
