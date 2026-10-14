package com.example.contor.service;

import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.DisplayConfigDto;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.api.model.NewCollectionFieldCommand;
import com.example.contor.domain.Slug;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the system collections (People, Organizations, and the assistant's own calendar and to-do list: Events,
 * Tasks) and relation types (works_at, knows, attends) through the services on startup. Idempotent: anything that
 * already exists is left alone.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SystemDataSeeder implements ApplicationRunner {

    private final CollectionRepository collectionRepository;
    private final RelationTypeRepository relationTypeRepository;
    private final CollectionSchemaService collectionSchemaService;
    private final RelationTypeService relationTypeService;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Long> createdIds = new HashMap<>();
        systemCollections().forEach(command -> seedCollection(command, createdIds));
        seedRelationType("works_at", "works at", "employs", "people", "organizations", true, createdIds);
        seedRelationType("knows", "knows", "knows", "people", "people", false, createdIds);
        seedRelationType("attends", "attends", "attended by", "people", "events", true, createdIds);
    }

    /** The system collection definitions (also used by the AI evaluation to build its fixed schema). */
    public static List<NewCollectionCommand> systemCollections() {
        return List.of(people(), organizations(), events(), tasks());
    }

    private void seedCollection(NewCollectionCommand command, Map<String, Long> createdIds) {
        if (collectionRepository.existsBySlug(new Slug(command.getSlug()))) {
            return;
        }
        createdIds.put(command.getSlug(), collectionSchemaService.createSystemCollection(command).getId());
        log.info("Seeded system collection '{}'", command.getSlug());
    }

    private void seedRelationType(String slug, String name, String inverseName, String from, String to, boolean directed,
                                  Map<String, Long> collectionIds) {
        if (relationTypeRepository.existsBySlug(new Slug(slug))) {
            return;
        }
        relationTypeService.createSystemRelationType(slug, name, inverseName,
                collectionId(from, collectionIds), collectionId(to, collectionIds), directed);
        log.info("Seeded relation type '{}'", slug);
    }

    private Long collectionId(String slug, Map<String, Long> collectionIds) {
        return collectionIds.computeIfAbsent(slug,
                s -> collectionRepository.findBySlug(new Slug(s)).orElseThrow().getId().id());
    }

    private static NewCollectionFieldCommand field(String slug, String name, CollectionFieldType type, String description) {
        return new NewCollectionFieldCommand(slug, name, type).description(description);
    }

    private static NewCollectionCommand people() {
        return new NewCollectionCommand("people", "People")
                .description("People you deal with: contacts, colleagues, senders.")
                .icon("user")
                .matchKeys(List.of("email"))
                .displayConfig(new DisplayConfigDto().defaultView("table").titleField("full_name"))
                .fields(List.of(
                        field("full_name", "Full name", CollectionFieldType.TEXT, "First and last name").required(true),
                        field("email", "Email", CollectionFieldType.EMAIL, "Primary email address"),
                        field("phone", "Phone", CollectionFieldType.PHONE, "Phone number"),
                        field("role_title", "Role", CollectionFieldType.TEXT, "Job title or role"),
                        field("notes", "Notes", CollectionFieldType.LONG_TEXT, "Free-form notes")));
    }

    private static NewCollectionCommand organizations() {
        return new NewCollectionCommand("organizations", "Organizations")
                .description("Companies, shops, authorities and other organizations.")
                .icon("building")
                .matchKeys(List.of("domain"))
                .displayConfig(new DisplayConfigDto().defaultView("table").titleField("name"))
                .fields(List.of(
                        field("name", "Name", CollectionFieldType.TEXT, "Organization name").required(true),
                        field("domain", "Domain", CollectionFieldType.TEXT, "Email/web domain, e.g. example.com"),
                        field("website", "Website", CollectionFieldType.URL, "Website URL"),
                        field("notes", "Notes", CollectionFieldType.LONG_TEXT, "Free-form notes")));
    }

    /** The assistant's calendar: appointments, meetings, deadlines and trips found in mail. Title = what happens. */
    private static NewCollectionCommand events() {
        return new NewCollectionCommand("events", "Calendar")
                .description("Appointments, meetings, deadlines, bookings and trips. The title says what happens.")
                .icon("calendar")
                .displayConfig(new DisplayConfigDto().defaultView("calendar").calendarDateField("starts_at"))
                .fields(List.of(
                        field("starts_at", "Starts", CollectionFieldType.DATETIME, "Start date and time (ISO-8601 with offset)").required(true),
                        field("ends_at", "Ends", CollectionFieldType.DATETIME, "End date and time, if known"),
                        field("all_day", "All day", CollectionFieldType.BOOLEAN, "true when the event has no time of day"),
                        field("location", "Location", CollectionFieldType.TEXT, "Address, room or video call link"),
                        field("notes", "Notes", CollectionFieldType.LONG_TEXT, "Details, agenda, booking codes")));
    }

    /** The assistant's to-do list: things the user has to do, found in mail. Title = the action. */
    private static NewCollectionCommand tasks() {
        return new NewCollectionCommand("tasks", "To-dos")
                .description("Things you have to do: reply, pay, sign, send, prepare. The title is the action to take.")
                .icon("check-square")
                .displayConfig(new DisplayConfigDto().defaultView("list").kanbanStatusField("state"))
                .fields(List.of(
                        field("state", "State", CollectionFieldType.STATUS, "open, in_progress or done")
                                .options(Map.of("states", List.of("open", "in_progress", "done"))),
                        field("due_date", "Due", CollectionFieldType.DATE, "Deadline (YYYY-MM-DD), if any"),
                        field("priority", "Priority", CollectionFieldType.ENUM, "low, normal or high")
                                .options(Map.of("values", List.of("low", "normal", "high"))),
                        field("notes", "Notes", CollectionFieldType.LONG_TEXT, "Details and context")));
    }
}
