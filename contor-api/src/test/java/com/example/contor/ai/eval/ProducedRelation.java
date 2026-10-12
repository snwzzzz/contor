package com.example.contor.ai.eval;

/** A relation the extraction proposed for an eval case; the ends are named by collection slug and record title. */
public record ProducedRelation(String type, String fromCollection, String fromTitle, String toCollection, String toTitle) {
}
