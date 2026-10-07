package com.example.contor.ai;

import tools.jackson.databind.JsonNode;

/** Local LLM (Ollama, next phase) constrained to answer with JSON matching {@code jsonSchema}. */
public interface LlmClient {

    String completeJson(String systemPrompt, String userPrompt, JsonNode jsonSchema);
}
