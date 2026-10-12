package com.example.contor.service;

import com.example.contor.ai.JsonSchemas;
import com.example.contor.ai.LlmClient;
import com.example.contor.ai.ReplyThread;
import com.example.contor.ai.SlotFinder;
import com.example.contor.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * One small, schema-constrained model call: does the email need a personal answer from the user, what kind of answer,
 * and which questions does it ask? Runs only for emails that passed {@link com.example.contor.ai.ReplyCandidateFilter};
 * reads the email and the two messages before it.
 */
@RequiredArgsConstructor
@Service
public class ReplyTriageService {

    private static final List<String> URGENCY = List.of("low", "normal", "high");
    /** What the reply has to do; NONE when no reply is needed. */
    static final List<String> INTENTS = List.of("ANSWER_QUESTIONS", "SCHEDULE", "CONFIRM", "DECLINE", "PROVIDE_INFO", "NONE");
    static final int MAX_QUESTIONS = 5;

    private static final String SYSTEM = """
            You decide whether an email needs a personal written answer from the user. Answer with JSON only.
            needsReply is true only when a person asks the user something, asks for a decision, a confirmation, a \
            document or an appointment, or clearly expects an answer from the user. It is false for newsletters, \
            notifications, receipts, order or booking confirmations without a question, mails that only inform (FYI), \
            and when the sender only thanks the user or closes the conversation. It is also false for invoices, payment \
            reminders, appointment reminders and confirmations of an appointment that is already agreed: the user pays or \
            attends, but does not write back.
            The test: does the sender wait for a written answer from the user? A request to do something — pay, attend, \
            join a call, take a test, renew, cancel or upgrade a plan, click a link — is not a request for an answer, \
            unless the sender explicitly asks the user to reply, confirm or choose.
            reason: one short sentence. urgency: high when the email names a deadline within the next days, low when an \
            answer can wait, normal otherwise.
            intent: what the reply has to do — SCHEDULE (agree on a date or time), ANSWER_QUESTIONS, CONFIRM (say yes to \
            something proposed), DECLINE, PROVIDE_INFO (send a document or information), NONE when no reply is needed.
            questions: each question or request the reply must answer, in the words of the email, at most 5.
            scheduleFrom, scheduleTo: for SCHEDULE, the first and last day (YYYY-MM-DD) the email asks about, resolved from \
            the email's date ("next week" = its Monday to Friday); leave them out when it names no days. Give them for any \
            email that asks when the user is free, whatever the intent.
            timeOfDay: when in the day the sender wants to meet — MORNING, AFTERNOON, EVENING (e.g. a dinner) or ANY.""";

    private final LlmClient llmClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * @param urgency      low, normal or high
     * @param intent       one of {@link #INTENTS}
     * @param questions    what the reply must answer, at most {@link #MAX_QUESTIONS}
     * @param scheduleFrom first day a meeting is asked for, {@code null} when the email names none
     * @param scheduleTo   last day, {@code null} when none (or before {@code scheduleFrom})
     */
    public record Triage(boolean needsReply, String reason, String urgency, String intent, List<String> questions,
                         LocalDate scheduleFrom, LocalDate scheduleTo, SlotFinder.TimeOfDay timeOfDay) {

        public Triage {
            questions = questions != null ? List.copyOf(questions) : List.of();
            timeOfDay = timeOfDay != null ? timeOfDay : SlotFinder.TimeOfDay.ANY;
        }

        /** Whether the reply should offer free times: a meeting is asked for, or the email asks about days. */
        public boolean asksForTimes() {
            return "SCHEDULE".equals(intent) || scheduleFrom != null;
        }

        /** A triage without the reply's details (no answer from the model). */
        public static Triage none(String reason) {
            return new Triage(false, reason, "normal", "NONE", List.of(), null, null, SlotFinder.TimeOfDay.ANY);
        }
    }

    /** Whether the thread's last email needs a reply, and what the reply has to do; an unreadable answer counts as "no". */
    public Triage triage(ReplyThread thread) {
        Map<String, Object> schema = JsonSchemas.object(JsonSchemas.properties(
                "needsReply", JsonSchemas.bool(),
                "reason", JsonSchemas.string(),
                "urgency", JsonSchemas.oneOf(URGENCY),
                "intent", JsonSchemas.oneOf(INTENTS),
                "questions", JsonSchemas.array(JsonSchemas.string()),
                "scheduleFrom", JsonSchemas.string(),
                "scheduleTo", JsonSchemas.string(),
                "timeOfDay", JsonSchemas.oneOf(List.of("ANY", "MORNING", "AFTERNOON", "EVENING"))));
        String answer = llmClient.completeJson(properties.models().routing(), SYSTEM,
                ReplyPrompts.recent(thread, properties.timezone(), 2), objectMapper.valueToTree(schema));
        JsonNode json;
        try {
            json = objectMapper.readTree(answer);
        } catch (JacksonException e) {
            return Triage.none("The model's answer could not be read");
        }
        String urgency = json.path("urgency").asString("normal");
        List<String> questions = new ArrayList<>();
        for (JsonNode question : json.path("questions")) {
            String text = question.asString("").strip();
            if (!text.isEmpty() && questions.size() < MAX_QUESTIONS) {
                questions.add(text);
            }
        }
        String intent = json.path("intent").asString("");
        if (!INTENTS.contains(intent)) {
            intent = questions.isEmpty() ? "NONE" : "ANSWER_QUESTIONS";
        }
        LocalDate from = date(json.path("scheduleFrom"));
        LocalDate to = date(json.path("scheduleTo"));
        if (from != null && to != null && to.isBefore(from)) {
            to = null;
        }
        if (from == null) {
            to = null;
        }
        SlotFinder.TimeOfDay timeOfDay = Arrays.stream(SlotFinder.TimeOfDay.values())
                .filter(value -> value.name().equals(json.path("timeOfDay").asString(""))).findFirst().orElse(SlotFinder.TimeOfDay.ANY);
        return new Triage(json.path("needsReply").asBoolean(false), json.path("reason").asString(""),
                URGENCY.contains(urgency) ? urgency : "normal", intent, questions, from, to, timeOfDay);
    }

    private static LocalDate date(JsonNode node) {
        try {
            return node.isString() ? LocalDate.parse(node.asString().strip()) : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
