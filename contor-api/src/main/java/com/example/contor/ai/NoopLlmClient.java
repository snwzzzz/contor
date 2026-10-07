package com.example.contor.ai;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Placeholder until the Ollama client exists: never calls a model, always answers {@code {}}. */
@Component
public class NoopLlmClient implements LlmClient {

    @Override
    public String completeJson(String systemPrompt, String userPrompt, JsonNode jsonSchema) {
        return "{}";
    }
}
