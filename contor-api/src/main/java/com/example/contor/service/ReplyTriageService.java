package com.example.contor.service;

import com.example.contor.ai.JsonSchemas;
import com.example.contor.ai.LlmClient;
import com.example.contor.ai.ReplyThread;
import com.example.contor.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * One small, schema-constrained model call: does the email need a personal answer from the user? Runs only for emails
 * that passed {@link com.example.contor.ai.ReplyCandidateFilter}; reads the email and the two messages before it.
 */
@RequiredArgsConstructor
@Service
public class ReplyTriageService {

    private static final List<String> URGENCY = List.of("low", "normal", "high");

    private static final String SYSTEM = """
            You decide whether an email needs a personal written answer from the user. Answer with JSON only.
            needsReply is true only when a person asks the user something, asks for a decision, a confirmation, a \
            document or an appointment, or clearly expects an answer from the user. It is false for newsletters, \
            notifications, receipts, order or booking confirmations without a question, mails that only inform (FYI), \
            and when the sender only thanks the user or closes the conversation.
            reason: one short sentence. urgency: high when the email names a deadline within the next days, low when an \
            answer can wait, normal otherwise.""";

    private final LlmClient llmClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    /** @param urgency low, normal or high */
    public record Triage(boolean needsReply, String reason, String urgency) {
    }

    /** Whether the thread's last email needs a reply; an unreadable answer counts as "no". */
    public Triage triage(ReplyThread thread) {
        Map<String, Object> schema = JsonSchemas.object(JsonSchemas.properties(
                "needsReply", JsonSchemas.bool(),
                "reason", JsonSchemas.string(),
                "urgency", JsonSchemas.oneOf(URGENCY)));
        String answer = llmClient.completeJson(properties.models().routing(), SYSTEM,
                ReplyPrompts.recent(thread, properties.timezone(), 2), objectMapper.valueToTree(schema));
        try {
            JsonNode json = objectMapper.readTree(answer);
            String urgency = json.path("urgency").asString("normal");
            return new Triage(json.path("needsReply").asBoolean(false), json.path("reason").asString(""),
                    URGENCY.contains(urgency) ? urgency : "normal");
        } catch (JacksonException e) {
            return new Triage(false, "The model's answer could not be read", "normal");
        }
    }
}
