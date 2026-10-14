package com.example.contor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * When the user can meet, for the times a reply draft offers ({@code app.ai.availability.*}). Busy times come from the
 * user's Calendar collection; the Outlook calendar is not read.
 *
 * @param workDays    days a meeting may be on
 * @param workStart   earliest start, local time in {@code app.ai.timezone}
 * @param workEnd     latest end
 * @param slotMinutes length of an offered slot when the email names none
 * @param searchDays  how many days ahead slots are searched when the email names no dates
 * @param maxSlots    how many slots a draft offers at most (one per day)
 */
@ConfigurationProperties("app.ai.availability")
public record AvailabilityProperties(
        @DefaultValue({"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"}) List<DayOfWeek> workDays,
        @DefaultValue("09:00") LocalTime workStart,
        @DefaultValue("18:00") LocalTime workEnd,
        @DefaultValue("60") int slotMinutes,
        @DefaultValue("10") int searchDays,
        @DefaultValue("3") int maxSlots) {
}
