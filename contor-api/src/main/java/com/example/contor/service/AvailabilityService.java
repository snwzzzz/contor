package com.example.contor.service;

import com.example.contor.ai.SlotFinder;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.RecordDto;
import com.example.contor.config.AiProperties;
import com.example.contor.config.AvailabilityProperties;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Free times the user could offer in a reply: working hours ({@link AvailabilityProperties}) minus the appointments of
 * the Calendar collection ({@code events}: {@code starts_at}, {@code ends_at} — one hour when missing — and
 * {@code all_day}). Appointments that cannot be read are ignored.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AvailabilityService {

    private static final String EVENTS = "events";
    private static final int MAX_EVENTS = 500;
    private static final Duration DEFAULT_EVENT = Duration.ofHours(1);

    private final CollectionSchemaService collectionSchemaService;
    private final RecordService recordService;
    private final AvailabilityProperties properties;
    private final AiProperties aiProperties;
    private final Clock clock;

    /**
     * Up to {@code maxSlots} free slots, one per working day.
     *
     * @param from first day, {@code null} = today
     * @param to   last day, {@code null} = {@code searchDays} after {@code from}
     */
    @Transactional
    public List<SlotFinder.Slot> freeSlots(LocalDate from, LocalDate to) {
        return freeSlots(from, to, SlotFinder.TimeOfDay.ANY);
    }

    /** As {@link #freeSlots(LocalDate, LocalDate)} at a time of day (EVENING: after work, e.g. for a dinner). */
    @Transactional
    public List<SlotFinder.Slot> freeSlots(LocalDate from, LocalDate to, SlotFinder.TimeOfDay timeOfDay) {
        ZoneId zone = aiProperties.timezone();
        LocalDate first = from != null ? from : LocalDate.now(clock.withZone(zone));
        LocalDate last = to != null ? to : first.plusDays(properties.searchDays());
        SlotFinder.WorkingHours hours = timeOfDay.narrow(new SlotFinder.WorkingHours(EnumSet.copyOf(properties.workDays()),
                properties.workStart(), properties.workEnd()));
        return SlotFinder.freeSlots(busy(first, last, zone), hours, first, last, Duration.ofMinutes(properties.slotMinutes()),
                clock.instant(), zone, properties.maxSlots());
    }

    /** The appointments from the day before {@code first} (they may last into it) to the end of {@code last}. */
    private List<SlotFinder.Busy> busy(LocalDate first, LocalDate last, ZoneId zone) {
        Optional<CollectionDto> events = collectionSchemaService.findAll().stream()
                .filter(collection -> EVENTS.equals(collection.getSlug())).findFirst();
        if (events.isEmpty()) {
            return List.of();
        }
        Instant start = first.minusDays(1).atStartOfDay(zone).toInstant();
        Instant end = last.plusDays(1).atStartOfDay(zone).toInstant();
        List<RecordDto> records;
        try {
            records = recordService.listRecords(events.get().getId(), List.of("starts_at:gte:" + start, "starts_at:lt:" + end), null,
                    "starts_at", 0, MAX_EVENTS).getItems();
        } catch (RuntimeException e) {
            log.debug("No appointments for free slots: {}", e.getMessage());
            return List.of();
        }
        List<SlotFinder.Busy> busy = new ArrayList<>();
        for (RecordDto record : records) {
            toBusy(record.getValues(), zone).ifPresent(busy::add);
        }
        return busy;
    }

    private static Optional<SlotFinder.Busy> toBusy(Map<String, Object> values, ZoneId zone) {
        if (values == null || !(values.get("starts_at") instanceof String startsAt)) {
            return Optional.empty();
        }
        try {
            OffsetDateTime starts = OffsetDateTime.parse(startsAt);
            if (Boolean.TRUE.equals(values.get("all_day"))) {
                return Optional.of(SlotFinder.Busy.allDay(starts.atZoneSameInstant(zone).toLocalDate(), zone));
            }
            Instant end = values.get("ends_at") instanceof String endsAt
                    ? OffsetDateTime.parse(endsAt).toInstant()
                    : starts.toInstant().plus(DEFAULT_EVENT);
            return end.isAfter(starts.toInstant()) ? Optional.of(new SlotFinder.Busy(starts.toInstant(), end)) : Optional.empty();
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
