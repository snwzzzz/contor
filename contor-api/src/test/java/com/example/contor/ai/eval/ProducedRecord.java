package com.example.contor.ai.eval;

import java.util.Map;

/** A record the extraction proposed for an eval case. */
public record ProducedRecord(String collection, String title, Map<String, Object> values) {

    public ProducedRecord {
        values = values != null ? values : Map.of();
    }
}
