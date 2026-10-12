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
 * the body (the model's own list is only a hint to write them). Never logs the text.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReplyComposerService {

    public static final String PROMPT_VERSION = "reply-v1";
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
            - subject: "Re: " and the subject of the answered message.
            - language: the ISO 639-1 code of the reply, e.g. de or en.
            - summary: one short English sentence saying what the reply does.""";

    private final LlmClient llmClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * @param language     ISO 639-1 code, lower case
     * @param placeholders the {@code […]} parts of the body the user still has to fill in, in order of appearance
     * @param summary      one line on what the reply does (shown as the proposal's reasoning)
     */
    public record ComposedReply(String subject, String body, String language, List<String> placeholders, String summary) {
    }

    /**
     * A draft answering the thread's last email, or empty when the model's answer is unusable.
     *
     * @param grounding one line per fact from the user's records (sender's contact, upcoming events and to-dos)
     */
    public Optional<ComposedReply> compose(ReplyThread thread, List<String> grounding) {
        LocalDate today = LocalDate.now(clock.withZone(properties.timezone()));
        String system = SYSTEM.formatted(today, today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                properties.timezone(), Optional.ofNullable(thread.userName()).filter(name -> !name.isBlank()).orElse("the user"),
                Optional.ofNullable(thread.mailbox()).orElse(""));
        Map<String, Object> schema = JsonSchemas.object(JsonSchemas.properties(
                "subject", JsonSchemas.string(),
                "body", JsonSchemas.string(),
                "language", JsonSchemas.string(),
                "placeholders", JsonSchemas.array(JsonSchemas.string()),
                "summary", JsonSchemas.string()));

        String answer = llmClient.completeJson(properties.models().extraction(), system, userPrompt(thread, grounding),
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
        String language = json.path("language").asString("").strip().toLowerCase(Locale.ROOT);
        return Optional.of(new ComposedReply(subject, body, language.matches("[a-z]{2}") ? language : null,
                placeholders(body), json.path("summary").asString("").strip()));
    }

    /** "Re: " + the subject, unless it already is a reply ("Re:", "AW:", …). */
    public static String replySubject(String subject) {
        String original = Optional.ofNullable(subject).orElse("").strip();
        return REPLY_PREFIX.matcher(original).matches() ? original : ("Re: " + original).strip();
    }

    private String userPrompt(ReplyThread thread, List<String> grounding) {
        StringBuilder prompt = new StringBuilder();
        if (!grounding.isEmpty()) {
            prompt.append("From the user's records:\n");
            grounding.forEach(line -> prompt.append("- ").append(line).append('\n'));
            prompt.append('\n');
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
