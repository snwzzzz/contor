package com.example.contor.domain;

/**
 * How a collection is shown by default. Field references are field slugs.
 *
 * @param defaultView       e.g. {@code table}, {@code kanban}, {@code calendar}
 * @param kanbanStatusField STATUS field used as kanban columns
 * @param calendarDateField DATE/DATETIME field used by the calendar view
 * @param titleField        field shown as record title ({@code null} = system column {@code title})
 */
public record DisplayConfig(String defaultView, String kanbanStatusField, String calendarDateField, String titleField) {
}
