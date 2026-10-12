package com.example.contor.service;

import com.example.contor.ai.JsonSchemas;
import com.example.contor.ai.LlmClient;
import com.example.contor.ai.ReplyThread;
import com.example.contor.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a reply draft for the last email of a thread with the extraction model (prompt {@link #PROMPT_VERSION}, kept
 * apart from the extraction prompt so its evaluation stays stable). The model must not invent facts: where the user has
 * to decide, it writes a {@code [placeholder]}. The answer is validated: a body is required and limited to
 * {@link #MAX_BODY_CHARS}, a missing subject becomes "Re: …", and the placeholders are exactly the {@code […]} parts of
 * the body (the model's own list is only a hint to write them). With a {@link Brief} (reply-v2) it answers each listed
 * question — from the thread, the user's records and other mail, or the offered free times — and reports per question
 * whether it did; a question reported as open needs a placeholder in the body, otherwise the draft is dropped. Never
 * logs the text.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReplyComposerService {

    public static final String PROMPT_VERSION = "reply-v2";
    public static final int MAX_BODY_CHARS = 4000;

    /** Tokens kept free for the instructions and the answer; the rest of the context window holds the thread. */
    private static final int RESERVED_TOKENS = 2000;
    private static final int MIN_THREAD_CHARS = 4000;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[[^\\[\\]\\n]{1,80}]");
    private static final Pattern REPLY_PREFIX = Pattern.compile("(?i)^(re|aw|antw|sv|wg|fw|fwd)\\s*:.*");

    private static final String SYSTEM = """
            You write a reply draft for the user of contor, a personal management app. The user reads, edits and sends it \
            personally; you never send anything. Answer with JSON only.
            Today is %s (%s). The user's time zone is %s. The user is %s <%s>.
            Rules:
            - Answer the message marked ANSWER THIS, in the language it is written in, and match its formality (for \
            example Sie or du, first names or not).
            - Messages from "You" were written by the user: they show the user's tone and what the user already said. Do \
            not repeat them.
            - Never invent facts, dates, times, prices, numbers or commitments. Where the user has to decide or fill \
            something in, write a short placeholder in square brackets in the language of the reply, e.g. \
            [available times] or [price], and list every placeholder in "placeholders".
            - Use only what the thread and the listed records say about people, appointments and to-dos.
            - Keep it short and friendly. No quoted history, no subject line in the body. End with the user's first name \
            only, no signature block.
            - Answer every question listed under "Questions", in that order, in natural sentences. Where the thread, the \
            user's records or other mail answer it, answer it; otherwise write a placeholder for it. In "answers", give one \
            entry per listed question, in the same order: answered true, or false when the body only has a placeholder.
            - When free times are listed, offer only the free times listed (all of them, in the language and date format of \
            the reply) and never other times; ask the sender to pick one. If they do not fit what the sender asks for \
            (another day or time of day), offer none and write a placeholder instead.
            - subject: "Re: " and the subject of the answered message.
            - language: the ISO 639-1 code of the reply, e.g. de or en.
            - summary: one short English sentence saying what the reply does.""";

    private final LlmClient llmClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * What the reply has to do, from the triage and the user's data.
     *
     * @param intent    the triage's intent (SCHEDULE, ANSWER_QUESTIONS, …), {@code null} when unknown
     * @param questions what the reply must answer, in order
     * @param freeTimes offered meeting times, already formatted ("Tuesday, 13 October 2026, 11:00–12:00")
     * @param context   the user's other mail about the email ({@code MailContextPrompt}), "" when none
     * @param courtesyKind for intent COURTESY: what the polite reply does (REJECTION_THANKS, …), else {@code null}
     */
    public record Brief(String intent, List<String> questions, List<String> freeTimes, String context, String courtesyKind) {

        public static final Brief NONE = new Brief(null, List.of(), List.of(), "", null);

        public Brief {
            questions = questions != null ? List.copyOf(questions) : List.of();
            freeTimes = freeTimes != null ? List.copyOf(freeTimes) : List.of();
            context = context != null ? context : "";
        }

        public Brief(String intent, List<String> questions, List<String> freeTimes, String context) {
            this(intent, questions, freeTimes, context, null);
        }
    }

    /** How an optional polite reply is written, per courtesy kind; all are short, warm and promise nothing new. */
    private static final Map<String, String> COURTESY_GUIDANCE = Map.of(
            "REJECTION_THANKS", "thank them for the opportunity and their time, say you remain interested and ask them to keep "
                    + "you in mind for future openings; you may add a placeholder like [request for feedback]",
            "DECLINE_OFFER", "thank them for the offer and decline it kindly, with a short reason only as a placeholder like [reason]",
            "THANK_YOU", "thank them warmly for what they did, naming it",
            "CONGRATULATE", "congratulate them warmly on their news, naming it",
            "KEEP_IN_TOUCH", "thank them for the collaboration, wish them well and say you would like to stay in touch");

    /** @param answered true/false as the model reported it; {@code null} when it did not report on the question */
    public record Answer(String question, Boolean answered) {
    }

    /**
     * @param language     ISO 639-1 code, lower case
     * @param placeholders the {@code […]} parts of the body the user still has to fill in, in order of appearance
     * @param summary      one line on what the reply does (shown as the proposal's reasoning)
     * @param answers      one per question of the {@link Brief}
     */
    public record ComposedReply(String subject, String body, String language, List<String> placeholders, String summary,
                                List<Answer> answers) {
    }

    /**
     * A draft answering the thread's last email, or empty when the model's answer is unusable.
     *
     * @param grounding one line per fact from the user's records (sender's contact, upcoming events and to-dos)
     */
    public Optional<ComposedReply> compose(ReplyThread thread, List<String> grounding) {
        return compose(thread, grounding, Brief.NONE);
    }

    /** A draft answering the thread's last email along the {@code brief}, or empty when the answer is unusable. */
    public Optional<ComposedReply> compose(ReplyThread thread, List<String> grounding, Brief brief) {
        LocalDate today = LocalDate.now(clock.withZone(properties.timezone()));
        String system = SYSTEM.formatted(today, today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                properties.timezone(), Optional.ofNullable(thread.userName()).filter(name -> !name.isBlank()).orElse("the user"),
                Optional.ofNullable(thread.mailbox()).orElse(""));
        Map<String, Object> schema = JsonSchemas.object(JsonSchemas.properties(
                "subject", JsonSchemas.string(),
                "body", JsonSchemas.string(),
                "language", JsonSchemas.string(),
                "placeholders", JsonSchemas.array(JsonSchemas.string()),
                "summary", JsonSchemas.string(),
                "answers", JsonSchemas.array(JsonSchemas.object(JsonSchemas.properties(
                        "question", JsonSchemas.string(),
                        "answered", JsonSchemas.bool())))));

        String answer = llmClient.completeJson(properties.models().extraction(), system, userPrompt(thread, grounding, brief),
                objectMapper.valueToTree(schema));
        JsonNode json;
        try {
            json = objectMapper.readTree(answer);
        } catch (JacksonException e) {
            log.warn("The reply model answered with unreadable JSON");
            return Optional.empty();
        }
        String body = json.path("body").asString("").strip();
        if (body.isEmpty() || body.length() > MAX_BODY_CHARS) {
            log.info("The reply model's draft was discarded: body of {} chars", body.length());
            return Optional.empty();
        }
        String subject = json.path("subject").asString("").strip();
        if (subject.isEmpty()) {
            subject = replySubject(thread.answered().subject());
        }
        List<String> placeholders = placeholders(body);
        List<Answer> answers = answers(brief.questions(), json.path("answers"));
        if (!placeholders.isEmpty() || answers.stream().noneMatch(found -> Boolean.FALSE.equals(found.answered()))) {
            String language = json.path("language").asString("").strip().toLowerCase(Locale.ROOT);
            return Optional.of(new ComposedReply(subject, body, language.matches("[a-z]{2}") ? language : null,
                    placeholders, json.path("summary").asString("").strip(), answers));
        }
        log.info("The reply model's draft was discarded: an open question without a placeholder");
        return Optional.empty();
    }

    /** The model's report per question, matched by position; questions it skipped stay unchecked. */
    private static List<Answer> answers(List<String> questions, JsonNode reported) {
        List<Answer> answers = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            JsonNode answered = reported.path(i).path("answered");
            answers.add(new Answer(questions.get(i), answered.isBoolean() ? answered.asBoolean() : null));
        }
        return answers;
    }

    /** "Re: " + the subject, unless it already is a reply ("Re:", "AW:", …). */
    public static String replySubject(String subject) {
        String original = Optional.ofNullable(subject).orElse("").strip();
        return REPLY_PREFIX.matcher(original).matches() ? original : ("Re: " + original).strip();
    }

    private String userPrompt(ReplyThread thread, List<String> grounding, Brief brief) {
        StringBuilder prompt = new StringBuilder();
        if (!grounding.isEmpty()) {
            prompt.append("From the user's records:\n");
            grounding.forEach(line -> prompt.append("- ").append(line).append('\n'));
            prompt.append('\n');
        }
        if (brief.intent() != null) {
            prompt.append("What the reply has to do: ").append(brief.intent()).append("\n\n");
        }
        if (brief.courtesyKind() != null && COURTESY_GUIDANCE.containsKey(brief.courtesyKind())) {
            prompt.append("An optional polite reply, 2 to 4 sentences, no new commitments: ")
                    .append(COURTESY_GUIDANCE.get(brief.courtesyKind())).append(".\n\n");
        }
        if (!brief.questions().isEmpty()) {
            prompt.append("Questions:\n");
            for (int i = 0; i < brief.questions().size(); i++) {
                prompt.append(i + 1).append(". ").append(brief.questions().get(i)).append('\n');
            }
            prompt.append('\n');
        }
        if (!brief.freeTimes().isEmpty()) {
            prompt.append("Free times of the user (from the calendar):\n");
            brief.freeTimes().forEach(time -> prompt.append("- ").append(time).append('\n'));
            prompt.append('\n');
        }
        if (!brief.context().isBlank()) {
            prompt.append(brief.context().strip()).append("\n\n");
        }
        int budget = Math.max(MIN_THREAD_CHARS, (properties.ollama().numCtx() - RESERVED_TOKENS) * 3);
        return prompt.append("Conversation, oldest first:\n\n")
                .append(ReplyPrompts.thread(thread, properties.timezone(), budget))
                .toString();
    }

    /**
     * The {@code […]} parts of the body in order of appearance. The model's own list is not trusted: listed ones missing
     * from the body are dropped, forgotten ones are added.
     */
    private static List<String> placeholders(String body) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(body);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return List.copyOf(found);
    }
}
