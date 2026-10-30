package com.example.contor.mailprovider;

import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.SyncFolder;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GmailMessageParserTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static GmailJson.Message message(String json) {
        return JSON.readValue(json, GmailJson.Message.class);
    }

    private static String b64(String text) {
        return b64(text, StandardCharsets.UTF_8);
    }

    private static String b64(String text, Charset charset) {
        // Gmail sends base64url without padding
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(charset));
    }

    @Test
    void multipart_alternative_message_keeps_the_html_body_and_all_headers() {
        var parsed = GmailMessageParser.parse(message("""
                {
                  "id": "18c1", "threadId": "18c0", "labelIds": ["INBOX", "UNREAD", "CATEGORY_PERSONAL"],
                  "snippet": "Hi, it&#39;s about Friday &amp; the offer", "internalDate": "1791363600000",
                  "payload": {
                    "mimeType": "multipart/alternative", "filename": "",
                    "headers": [
                      {"name": "From", "value": "\\"Doe, Anna\\" <anna@acme.com>"},
                      {"name": "To", "value": "me@gmail.com, Bob <bob@example.com>"},
                      {"name": "Cc", "value": "carol@example.com"},
                      {"name": "Subject", "value": "Offer for Friday"},
                      {"name": "Message-ID", "value": "<CAF=abc@mail.gmail.com>"}
                    ],
                    "body": {"size": 0},
                    "parts": [
                      {"partId": "0", "mimeType": "text/plain", "filename": "",
                       "headers": [{"name": "Content-Type", "value": "text/plain; charset=\\"UTF-8\\""}],
                       "body": {"size": 5, "data": "%s"}},
                      {"partId": "1", "mimeType": "text/html", "filename": "",
                       "headers": [{"name": "Content-Type", "value": "text/html; charset=\\"UTF-8\\""}],
                       "body": {"size": 12, "data": "%s"}}
                    ]
                  }
                }
                """.formatted(b64("Hi Anna, Grüße"), b64("<p>Hi Anna, Grüße</p>"))), SyncFolder.INBOX);

        assertThat(parsed.providerMessageId()).isEqualTo("18c1");
        assertThat(parsed.conversationId()).isEqualTo("18c0");
        assertThat(parsed.internetMessageId()).isEqualTo("<CAF=abc@mail.gmail.com>");
        assertThat(parsed.from()).isEqualTo(new EmailRecipient("Doe, Anna", "anna@acme.com"));
        assertThat(parsed.to()).containsExactly(new EmailRecipient(null, "me@gmail.com"), new EmailRecipient("Bob", "bob@example.com"));
        assertThat(parsed.cc()).containsExactly(new EmailRecipient(null, "carol@example.com"));
        assertThat(parsed.subject()).isEqualTo("Offer for Friday");
        assertThat(parsed.snippet()).isEqualTo("Hi, it's about Friday & the offer");
        assertThat(parsed.bodyHtml()).isEqualTo("<p>Hi Anna, Grüße</p>");
        assertThat(parsed.bodyText()).isNull();
        assertThat(parsed.receivedAt()).isEqualTo(Instant.ofEpochMilli(1791363600000L));
        assertThat(parsed.read()).isFalse();
        assertThat(parsed.hasAttachments()).isFalse();
    }

    @Test
    void plain_text_message_in_latin_1_is_decoded_with_its_charset() {
        var parsed = GmailMessageParser.parse(message("""
                {"id": "m2", "threadId": "t2", "labelIds": ["INBOX"], "internalDate": "1000",
                 "payload": {"mimeType": "text/plain", "filename": "",
                   "headers": [{"name": "Content-Type", "value": "text/plain; charset=ISO-8859-1"},
                               {"name": "From", "value": "jo@example.at"}],
                   "body": {"size": 4, "data": "%s"}}}
                """.formatted(b64("Grüß Gott", StandardCharsets.ISO_8859_1))), SyncFolder.INBOX);

        assertThat(parsed.bodyText()).isEqualTo("Grüß Gott");
        assertThat(parsed.bodyHtml()).isNull();
        assertThat(parsed.read()).isTrue();
        assertThat(parsed.from()).isEqualTo(new EmailRecipient(null, "jo@example.at"));
        assertThat(parsed.to()).isEmpty();
    }

    @Test
    void nested_multipart_mixed_finds_the_body_and_the_attachment() {
        var parsed = GmailMessageParser.parse(message("""
                {"id": "m3", "threadId": "t3", "labelIds": ["SENT"], "internalDate": "2000",
                 "payload": {"mimeType": "multipart/mixed", "filename": "", "headers": [], "body": {"size": 0},
                   "parts": [
                     {"mimeType": "multipart/related", "filename": "", "body": {"size": 0}, "parts": [
                       {"mimeType": "multipart/alternative", "filename": "", "body": {"size": 0}, "parts": [
                         {"mimeType": "text/plain", "filename": "", "body": {"size": 3, "data": "%s"}},
                         {"mimeType": "text/html", "filename": "", "body": {"size": 3, "data": "%s"}}
                       ]},
                       {"mimeType": "image/png", "filename": "logo.png",
                        "headers": [{"name": "Content-Disposition", "value": "inline; filename=logo.png"},
                                    {"name": "Content-ID", "value": "<logo>"}],
                        "body": {"size": 900, "attachmentId": "ANGj1"}}
                     ]},
                     {"mimeType": "application/pdf", "filename": "Angebot.pdf",
                      "headers": [{"name": "Content-Disposition", "value": "attachment; filename=Angebot.pdf"}],
                      "body": {"size": 12000, "attachmentId": "ANGj2"}}
                   ]}}
                """.formatted(b64("Hi"), b64("<b>Hi</b>"))), SyncFolder.SENT);

        assertThat(parsed.bodyHtml()).isEqualTo("<b>Hi</b>");
        assertThat(parsed.hasAttachments()).isTrue();
    }

    @Test
    void inline_images_alone_are_no_attachments() {
        var parsed = GmailMessageParser.parse(message("""
                {"id": "m4", "threadId": "t4", "labelIds": [], "internalDate": "3000",
                 "payload": {"mimeType": "multipart/related", "filename": "", "body": {"size": 0}, "parts": [
                   {"mimeType": "text/html", "filename": "", "body": {"size": 3, "data": "%s"}},
                   {"mimeType": "image/png", "filename": "logo.png",
                    "headers": [{"name": "Content-Disposition", "value": "inline; filename=logo.png"}],
                    "body": {"size": 900, "attachmentId": "ANGj1"}}
                 ]}}
                """.formatted(b64("<i>x</i>"))), SyncFolder.INBOX);

        assertThat(parsed.hasAttachments()).isFalse();
    }

    @Test
    void message_without_payload_still_maps_its_ids() {
        var parsed = GmailMessageParser.parse(message("""
                {"id": "m5", "threadId": "t5", "internalDate": "4000"}
                """), SyncFolder.INBOX);

        assertThat(parsed.providerMessageId()).isEqualTo("m5");
        assertThat(parsed.subject()).isNull();
        assertThat(parsed.bodyText()).isNull();
        assertThat(parsed.bodyHtml()).isNull();
        assertThat(parsed.to()).isEmpty();
        assertThat(parsed.read()).isTrue();
    }

    @Test
    void encoded_words_in_headers_are_decoded() {
        assertThat(GmailMessageParser.decodeHeader("=?UTF-8?B?R3LDvMOfZSBhdXMgV2llbg==?=")).isEqualTo("Grüße aus Wien");
        assertThat(GmailMessageParser.decodeHeader("=?ISO-8859-1?Q?Gr=FC=DFe_aus?= =?ISO-8859-1?Q?_Wien?=")).isEqualTo("Grüße aus Wien");
        assertThat(GmailMessageParser.decodeHeader("Re: =?utf-8?q?Angebot?= für dich")).isEqualTo("Re: Angebot für dich");
        assertThat(GmailMessageParser.decodeHeader("plain")).isEqualTo("plain");
        assertThat(GmailMessageParser.decodeHeader(null)).isNull();
    }

    @Test
    void address_lists_respect_quotes_brackets_and_encoded_names() {
        assertThat(GmailMessageParser.parseAddresses(
                "\"Doe, Anna\" <Anna@Acme.com>, =?UTF-8?B?SsO8cmdlbg==?= <j@example.at>, plain@example.com, undisclosed-recipients:;"))
                .containsExactly(
                        new EmailRecipient("Doe, Anna", "Anna@Acme.com"),
                        new EmailRecipient("Jürgen", "j@example.at"),
                        new EmailRecipient(null, "plain@example.com"));
        assertThat(GmailMessageParser.parseAddresses(null)).isEmpty();
        assertThat(GmailMessageParser.parseAddresses("<only@brackets.com>")).containsExactly(new EmailRecipient(null, "only@brackets.com"));
    }
}
