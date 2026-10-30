package com.example.contor.mailprovider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** The parts of the Gmail, OpenID userinfo and People API answers contor reads (everything else is ignored). */
final class GmailJson {

    private GmailJson() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Header(String name, String value) {}

    /** {@code data} is base64url; large bodies (attachments) only carry an {@code attachmentId}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(Integer size, String data, String attachmentId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String partId, String mimeType, String filename, List<Header> headers, Body body, List<Part> parts) {}

    /** {@code internalDate}: epoch milliseconds (as a string) when Gmail received or sent the message. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String id, String threadId, List<String> labelIds, String snippet, String historyId,
                   String internalDate, Part payload) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MessageRef(String id, String threadId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MessageList(List<MessageRef> messages, String nextPageToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Profile(String emailAddress, String historyId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MessageChange(MessageRef message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record History(String id, List<MessageChange> messagesAdded, List<MessageChange> labelsAdded,
                   List<MessageChange> labelsRemoved) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HistoryList(List<History> history, String nextPageToken, String historyId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserInfo(String email, String name, String picture) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PersonEmail(String value) {}

    /** {@code isDefault}: Google's generated initial-letter avatar, not a real photo. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PersonPhoto(String url, @JsonProperty("default") Boolean isDefault) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Person(List<PersonEmail> emailAddresses, List<PersonPhoto> photos) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Connections(List<Person> connections, String nextPageToken) {}
}
