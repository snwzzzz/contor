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

    /** APP_ITEM: the cited item of the user's apps while it exists (chat answers only). */
    default ExternalApiItem getApiItem() {
        return null;
    }
}
