package com.example.contor.ai;

import java.util.List;

/** Text embeddings (Ollama, {@code nomic-embed-text}, 768 dimensions; see EmailEmbedding.DIMENSIONS). */
public interface EmbeddingClient {

    float[] embed(String text);

    /** One vector per text, in order; implementations embed them with one model call where they can. */
    default List<float[]> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
