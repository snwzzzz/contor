package com.example.contor.domain;

import java.util.Map;

/** A cited source of a report sentence or a chat answer: what the source list and the source states need. */
public interface Citation {

    int getMarker();

    CitationType getType();

    Collection getCollection();

    Long getRecordId();

    String getFieldSlug();

    EmailMessage getEmail();

    Integer getParagraphIndex();

    String getQuote();

    Long getRelationId();

    Map<String, Object> getValueSnapshot();
}
