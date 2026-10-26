package com.example.contor.mailprovider;

import com.example.contor.config.GmailProperties;
import com.example.contor.domain.MailProvider;
import com.example.contor.domain.SyncFolder;
import com.example.contor.exception.SyncCursorExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GmailMailboxClientTest {

    private static final String GMAIL = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final String USERINFO = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String PEOPLE = "https://people.googleapis.com/v1/people/me/connections";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A};

    private MockRestServiceServer server;
    private GmailMailboxClient client;

    @BeforeEach
    void setUp() {
        var properties = new GmailProperties("cid", "secret", "http://localhost:8080/api/mail/accounts/callback", null, 365);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GmailMailboxClient(properties, builder, 1, Duration.ZERO);
    }

    private static String message(String id, String labels) {
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(("Body of " + id).getBytes());
        return """
                {"id": "%s", "threadId": "t-%s", "labelIds": [%s], "snippet": "Snippet", "internalDate": "1791363600000",
                 "payload": {"mimeType": "text/plain", "filename": "",
                   "headers": [{"name": "From", "value": "Anna <anna@acme.com>"}, {"name": "Subject", "value": "Hello %s"}],
                   "body": {"size": 10, "data": "%s"}}}
                """.formatted(id, id, labels, id, body);
    }

    private void expectMessage(String id, String labels) {
        server.expect(requestTo(GMAIL + "/messages/" + id + "?format=full"))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess(message(id, labels), MediaType.APPLICATION_JSON));
    }

    @Test
    void is_the_gmail_provider() {
        assertThat(client.provider()).isEqualTo(MailProvider.GMAIL);
    }

    @Test
    void profile_takes_the_address_from_gmail_and_the_name_from_userinfo() {
        server.expect(requestTo(GMAIL + "/profile")).andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("""
                        {"emailAddress": "me@gmail.com", "messagesTotal": 10, "historyId": "500"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USERINFO)).andRespond(withSuccess("""
                {"sub": "1", "email": "me@gmail.com", "name": "Me Myself", "picture": "https://lh3.googleusercontent.com/a/x=s96-c"}
                """, MediaType.APPLICATION_JSON));

        MailboxProfile profile = client.fetchProfile("token");

        assertThat(profile).isEqualTo(new MailboxProfile("me@gmail.com", "Me Myself"));
    }

    @Test
    void profile_without_the_profile_scope_still_has_the_address() {
        server.expect(requestTo(GMAIL + "/profile")).andRespond(withSuccess("""
                {"emailAddress": "me@gmail.com", "historyId": "500"}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USERINFO)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(client.fetchProfile("token")).isEqualTo(new MailboxProfile("me@gmail.com", null));
    }

    @Test
    void initial_sync_lists_the_folder_within_the_window_and_ends_with_the_history_id_from_before() {
        server.expect(requestTo(GMAIL + "/profile")).andRespond(withSuccess("""
                {"emailAddress": "me@gmail.com", "historyId": "500"}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(GMAIL + "/messages?")))
                .andExpect(queryParam("labelIds", "INBOX"))
                .andExpect(queryParam("q", "newer_than:365d"))
                .andExpect(queryParam("maxResults", "100"))
                .andRespond(withSuccess("""
                        {"messages": [{"id": "m1", "threadId": "t-m1"}], "nextPageToken": "p2", "resultSizeEstimate": 2}
                        """, MediaType.APPLICATION_JSON));
        expectMessage("m1", "\"INBOX\", \"UNREAD\"");

        FolderChanges first = client.fetchChanges("token", SyncFolder.INBOX, null);

        assertThat(first.messages()).extracting(SyncedMessage::providerMessageId).containsExactly("m1");
        assertThat(first.messages().get(0).subject()).isEqualTo("Hello m1");
        assertThat(first.messages().get(0).read()).isFalse();
        assertThat(first.hasNextPage()).isTrue();
        assertThat(first.newSyncCursor()).isNull();
        server.verify();

        server.reset();
        server.expect(requestTo(startsWith(GMAIL + "/messages?")))
                .andExpect(queryParam("pageToken", "p2"))
                .andRespond(withSuccess("""
                        {"messages": [{"id": "m2", "threadId": "t-m2"}]}
                        """, MediaType.APPLICATION_JSON));
        expectMessage("m2", "\"INBOX\"");

        FolderChanges last = client.fetchChanges("token", SyncFolder.INBOX, first.nextPageCursor());

        assertThat(last.messages()).extracting(SyncedMessage::providerMessageId).containsExactly("m2");
        assertThat(last.hasNextPage()).isFalse();
        assertThat(last.newSyncCursor()).isEqualTo("500");
        server.verify();
    }

    @Test
    void initial_sync_of_the_sent_folder_lists_the_sent_label() {
        server.expect(requestTo(GMAIL + "/profile")).andRespond(withSuccess("""
                {"emailAddress": "me@gmail.com", "historyId": "500"}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(GMAIL + "/messages?"))).andExpect(queryParam("labelIds", "SENT"))
                .andRespond(withSuccess("{\"resultSizeEstimate\": 0}", MediaType.APPLICATION_JSON));

        FolderChanges changes = client.fetchChanges("token", SyncFolder.SENT, null);

        assertThat(changes.messages()).isEmpty();
        assertThat(changes.newSyncCursor()).isEqualTo("500");
    }

    @Test
    void incremental_sync_reads_the_history_and_refetches_changed_messages() {
        server.expect(requestTo(startsWith(GMAIL + "/history?")))
                .andExpect(queryParam("startHistoryId", "500"))
                .andExpect(queryParam("labelId", "INBOX"))
                .andExpect(queryParam("historyTypes", "messageAdded", "labelAdded", "labelRemoved"))
                .andRespond(withSuccess("""
                        {"history": [
                           {"id": "510", "messagesAdded": [{"message": {"id": "m2", "labelIds": ["INBOX", "UNREAD"]}}]},
                           {"id": "511", "labelsRemoved": [{"message": {"id": "m3"}, "labelIds": ["UNREAD"]}]},
                           {"id": "512", "messagesAdded": [{"message": {"id": "m2"}}, {"message": {"id": "gone"}}]},
                           {"id": "513", "labelsRemoved": [{"message": {"id": "archived"}, "labelIds": ["INBOX"]}]}
                         ],
                         "historyId": "520"}
                        """, MediaType.APPLICATION_JSON));
        expectMessage("m2", "\"INBOX\", \"UNREAD\"");
        expectMessage("m3", "\"INBOX\"");
        server.expect(requestTo(GMAIL + "/messages/gone?format=full")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        expectMessage("archived", "\"CATEGORY_UPDATES\"");

        FolderChanges changes = client.fetchChanges("token", SyncFolder.INBOX, "500");

        assertThat(changes.messages()).extracting(SyncedMessage::providerMessageId).containsExactly("m2", "m3");
        assertThat(changes.messages().get(1).read()).isTrue();
        assertThat(changes.hasNextPage()).isFalse();
        assertThat(changes.newSyncCursor()).isEqualTo("520");
        server.verify();
    }

    @Test
    void incremental_sync_pages_through_the_history() {
        server.expect(requestTo(startsWith(GMAIL + "/history?"))).andExpect(queryParam("startHistoryId", "500"))
                .andRespond(withSuccess("""
                        {"history": [], "nextPageToken": "h2", "historyId": "530"}
                        """, MediaType.APPLICATION_JSON));

        FolderChanges first = client.fetchChanges("token", SyncFolder.INBOX, "500");
        assertThat(first.hasNextPage()).isTrue();
        assertThat(first.newSyncCursor()).isNull();

        server.reset();
        server.expect(requestTo(startsWith(GMAIL + "/history?")))
                .andExpect(queryParam("startHistoryId", "500"))
                .andExpect(queryParam("pageToken", "h2"))
                .andRespond(withSuccess("""
                        {"historyId": "531"}
                        """, MediaType.APPLICATION_JSON));

        FolderChanges last = client.fetchChanges("token", SyncFolder.INBOX, first.nextPageCursor());
        assertThat(last.newSyncCursor()).isEqualTo("531");
    }

    @Test
    void history_id_too_old_means_the_cursor_expired() {
        server.expect(requestTo(startsWith(GMAIL + "/history?"))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchChanges("token", SyncFolder.INBOX, "500"))
                .isInstanceOf(SyncCursorExpiredException.class);
    }

    @Test
    void a_cursor_that_is_no_gmail_cursor_counts_as_expired() {
        assertThatThrownBy(() -> client.fetchChanges("token", SyncFolder.INBOX, "https://graph.microsoft.com/delta?x"))
                .isInstanceOf(SyncCursorExpiredException.class);
    }

    @Test
    void rate_limited_requests_are_retried() {
        server.expect(once(), requestTo(startsWith(GMAIL + "/history?"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(once(), requestTo(startsWith(GMAIL + "/history?"))).andRespond(withSuccess("""
                {"historyId": "501"}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchChanges("token", SyncFolder.INBOX, "500").newSyncCursor()).isEqualTo("501");
    }

    @Test
    void rate_limits_that_persist_fail_the_request() {
        server.expect(twice(), requestTo(startsWith(GMAIL + "/history?"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(once(), requestTo(startsWith(GMAIL + "/history?"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetchChanges("token", SyncFolder.INBOX, "500"))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void profile_photo_is_loaded_from_the_userinfo_picture_in_96_pixels() {
        server.expect(requestTo(USERINFO)).andRespond(withSuccess("""
                {"email": "me@gmail.com", "picture": "https://lh3.googleusercontent.com/a/ACg8x=s128-c"}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://lh3.googleusercontent.com/a/ACg8x=s96-c"))
                .andRespond(withSuccess(PNG, MediaType.IMAGE_PNG));

        var photo = client.fetchProfilePhoto("token");

        assertThat(photo).hasValueSatisfying(image -> {
            assertThat(image.bytes()).isEqualTo(PNG);
            assertThat(image.contentType()).isEqualTo("image/png");
        });
    }

    @Test
    void profile_photo_from_a_foreign_host_is_not_loaded() {
        server.expect(requestTo(USERINFO)).andRespond(withSuccess("""
                {"email": "me@gmail.com", "picture": "https://evil.example.com/a.png"}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchProfilePhoto("token")).isEmpty();
        server.verify();
    }

    @Test
    void profile_photo_failures_never_throw() {
        server.expect(requestTo(USERINFO)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(client.fetchProfilePhoto("token")).isEmpty();
    }

    @Test
    void contact_photos_skip_default_avatars_and_map_every_address() {
        server.expect(requestTo(startsWith(PEOPLE + "?")))
                .andExpect(queryParam("personFields", "emailAddresses,photos"))
                .andRespond(withSuccess("""
                        {"connections": [
                           {"emailAddresses": [{"value": "Bob@Example.com"}, {"value": "bob@work.example"}],
                            "photos": [{"url": "https://lh3.googleusercontent.com/cm/bob=s100"}]},
                           {"emailAddresses": [{"value": "eve@example.com"}],
                            "photos": [{"url": "https://lh3.googleusercontent.com/cm/eve", "default": true}]},
                           {"photos": [{"url": "https://lh3.googleusercontent.com/cm/noaddress"}]}
                         ]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://lh3.googleusercontent.com/cm/bob=s100")).andRespond(withSuccess(PNG, MediaType.IMAGE_PNG));

        var photos = client.fetchContactPhotos("token");

        assertThat(photos).extracting(ContactPhotoData::emailAddress).containsExactly("bob@example.com", "bob@work.example");
        assertThat(photos.get(0).photo().contentType()).isEqualTo("image/png");
        server.verify();
    }

    @Test
    void contact_photos_without_permission_throw_for_the_caller() {
        server.expect(requestTo(startsWith(PEOPLE + "?"))).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.fetchContactPhotos("token")).isInstanceOf(HttpClientErrorException.class);
    }
}
