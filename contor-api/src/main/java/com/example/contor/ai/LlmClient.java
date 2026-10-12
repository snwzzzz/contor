package com.example.contor.ai;

import tools.jackson.databind.JsonNode;

/** Local LLM (Ollama) constrained to answer with JSON matching {@code jsonSchema}. */
public interface LlmClient {

    /** Returns the raw JSON answer of {@code model}; throws {@link OllamaException} when the call fails. */
    String completeJson(String model, String systemPrompt, String userPrompt, JsonNode jsonSchema);
}
