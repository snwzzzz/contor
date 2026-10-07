package com.example.contor.ai;

/** Text embeddings (next phase: Ollama, {@code nomic-embed-text}, 768 dimensions; see EmailEmbedding.DIMENSIONS). */
public interface EmbeddingClient {

    float[] embed(String text);
}
