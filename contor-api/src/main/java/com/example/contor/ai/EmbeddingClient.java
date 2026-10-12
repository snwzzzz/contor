package com.example.contor.ai;

/** Text embeddings (Ollama, {@code nomic-embed-text}, 768 dimensions; see EmailEmbedding.DIMENSIONS). */
public interface EmbeddingClient {

    float[] embed(String text);
}
