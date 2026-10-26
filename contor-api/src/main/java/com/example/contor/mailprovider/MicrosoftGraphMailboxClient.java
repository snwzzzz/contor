package com.example.contor.mailprovider;

import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.MailProvider;
import com.example.contor.domain.SyncFolder;
import com.example.contor.exception.SyncCursorExpiredException;
import com.example.contor.service.ImageData;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.Contact;
import com.microsoft.graph.models.ContactCollectionResponse;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.contacts.ContactsRequestBuilder;
import com.microsoft.graph.users.item.mailfolders.item.messages.delta.DeltaGetResponse;
import com.microsoft.graph.users.item.mailfolders.item.messages.delta.DeltaRequestBuilder;
import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.authentication.AuthenticationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Outlook mailboxes through Microsoft Graph: folder delta queries, profile and contact photos. */
@Slf4j
@Component
public class MicrosoftGraphMailboxClient implements MailboxClient {

    private static final String PHOTO_SIZE = "96x96";
    private static final int MAX_CONTACTS = 500;
    private static final Pattern VALID_ADDRESS = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Override
    public MailProvider provider() {
        return MailProvider.MICROSOFT_GRAPH;
    }

    GraphServiceClient createClient(String accessToken) {
        AuthenticationProvider authProvider = (request, context) ->
                request.headers.add("Authorization", "Bearer " + accessToken);
        return new GraphServiceClient(authProvider);
    }

    @Override
    public MailboxProfile fetchProfile(String accessToken) {
        GraphServiceClient client = createClient(accessToken);
        User user = client.me().get();
        if (user == null) {
            throw new IllegalStateException("Failed to retrieve user profile from Microsoft Graph");
        }
        return extractProfile(user);
    }

    /**
     * The signed-in user's profile photo: the 96x96 version if Graph offers sizes, else the original. Empty when the
     * account has none (404) or Graph does not answer; a missing photo must never break a sync.
     */
    @Override
    public Optional<ImageData> fetchProfilePhoto(String accessToken) {
        GraphServiceClient client = createClient(accessToken);
        try {
            return Optional.of(photoOf(client.me().photos().byProfilePhotoId(PHOTO_SIZE).content().get()));
        } catch (ApiException e) {
            if (e.getResponseStatusCode() != 404) {
                log.debug("Sized profile photo unavailable ({}), trying the original", e.getResponseStatusCode());
            }
        } catch (RuntimeException | IOException e) {
            log.debug("Sized profile photo unavailable: {}", e.getMessage());
        }
        try {
            return Optional.of(photoOf(client.me().photo().content().get()));
        } catch (ApiException e) {
            if (e.getResponseStatusCode() != 404) {
                log.warn("Profile photo request failed with status {}", e.getResponseStatusCode());
            }
            return Optional.empty();
        } catch (RuntimeException | IOException e) {
            log.warn("Profile photo request failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Photos of the signed-in user's Outlook contacts (up to {@value #MAX_CONTACTS} contacts), one entry per contact
     * address. Needs the Contacts.Read permission; a missing permission surfaces as an exception for the caller.
     */
    @Override
    public List<ContactPhotoData> fetchContactPhotos(String accessToken) {
        GraphServiceClient client = createClient(accessToken);
        ContactCollectionResponse page = client.me().contacts().get(config -> {
            config.queryParameters.select = new String[] {"id", "emailAddresses"};
            config.queryParameters.top = 100;
        });
        List<ContactPhotoData> photos = new ArrayList<>();
        int seen = 0;
        while (page != null && page.getValue() != null && seen < MAX_CONTACTS) {
            for (Contact contact : page.getValue()) {
                if (++seen > MAX_CONTACTS) break;
                List<String> addresses = contactAddresses(contact);
                if (addresses.isEmpty() || contact.getId() == null) continue;
                contactPhoto(client, contact.getId()).ifPresent(photo ->
                        addresses.forEach(address -> photos.add(new ContactPhotoData(address, photo))));
            }
            String next = page.getOdataNextLink();
            page = next == null || next.isBlank() ? null : new ContactsRequestBuilder(next, client.getRequestAdapter()).get();
        }
        return photos;
    }

    private static Optional<ImageData> contactPhoto(GraphServiceClient client, String contactId) {
        try {
            return Optional.of(photoOf(client.me().contacts().byContactId(contactId).photo().content().get()));
        } catch (ApiException e) {
            return Optional.empty(); // 404 = contact without photo
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Valid, lower-cased, distinct addresses of a contact. */
    static List<String> contactAddresses(Contact contact) {
        if (contact.getEmailAddresses() == null) {
            return List.of();
        }
        return contact.getEmailAddresses().stream()
                .map(EmailAddress::getAddress)
                .filter(address -> address != null && VALID_ADDRESS.matcher(address).matches())
                .map(address -> address.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static ImageData photoOf(InputStream content) throws IOException {
        if (content == null) {
            throw new IOException("Empty photo response");
        }
        try (content) {
            byte[] bytes = content.readAllBytes();
            if (bytes.length == 0) {
                throw new IOException("Empty photo response");
            }
            return new ImageData(bytes, ImageData.contentTypeOf(bytes));
        }
    }

    static MailboxProfile extractProfile(User user) {
        String email = user.getMail() != null && !user.getMail().isBlank()
                ? user.getMail()
                : user.getUserPrincipalName();
        return new MailboxProfile(email, user.getDisplayName());
    }

    /**
     * Delta of the inbox or the Sent Items folder (both covered by Mail.Read). The cursor is a Graph next link or
     * delta link; an expired delta link answers 410 Gone.
     */
    @Override
    public FolderChanges fetchChanges(String accessToken, SyncFolder folder, String cursor) {
        GraphServiceClient client = createClient(accessToken);
        DeltaGetResponse response;
        try {
            if (cursor == null || cursor.isBlank()) {
                String wellKnownFolder = folder == SyncFolder.SENT ? "sentitems" : "inbox";
                response = client.me().mailFolders().byMailFolderId(wellKnownFolder).messages().delta().get();
            } else {
                response = new DeltaRequestBuilder(cursor, client.getRequestAdapter()).get();
            }
        } catch (RuntimeException e) {
            if (cursor != null && isGone(e)) {
                throw new SyncCursorExpiredException("Graph delta link of " + folder + " expired");
            }
            throw e;
        }

        if (response == null) {
            return new FolderChanges(List.of(), null, null);
        }
        List<SyncedMessage> messages = response.getValue() == null ? List.of() : response.getValue().stream()
                .filter(message -> message.getId() != null)
                .map(message -> toSyncedMessage(message, folder))
                .toList();
        return new FolderChanges(messages, response.getOdataNextLink(), response.getOdataDeltaLink());
    }

    private static boolean isGone(RuntimeException e) {
        if (e instanceof ApiException api && api.getResponseStatusCode() == 410) {
            return true;
        }
        return e.getMessage() != null && e.getMessage().contains("410");
    }

    static SyncedMessage toSyncedMessage(Message message, SyncFolder folder) {
        boolean sent = folder == SyncFolder.SENT;
        var builder = SyncedMessage.builder()
                .providerMessageId(message.getId())
                .internetMessageId(message.getInternetMessageId())
                .conversationId(message.getConversationId())
                .to(mapRecipients(message.getToRecipients()))
                .cc(mapRecipients(message.getCcRecipients()))
                .subject(message.getSubject())
                .snippet(message.getBodyPreview())
                .read(message.getIsRead())
                .hasAttachments(message.getHasAttachments());
        if (message.getFrom() != null && message.getFrom().getEmailAddress() != null) {
            builder.from(new EmailRecipient(message.getFrom().getEmailAddress().getName(), message.getFrom().getEmailAddress().getAddress()));
        }
        if (message.getBody() != null) {
            if (message.getBody().getContentType() == BodyType.Html) {
                builder.bodyHtml(message.getBody().getContent());
            } else {
                builder.bodyText(message.getBody().getContent());
            }
        }
        if (sent && message.getSentDateTime() != null) {
            builder.receivedAt(message.getSentDateTime().toInstant());
        } else if (message.getReceivedDateTime() != null) {
            builder.receivedAt(message.getReceivedDateTime().toInstant());
        }
        return builder.build();
    }

    static List<EmailRecipient> mapRecipients(List<Recipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return Collections.emptyList();
        }
        return recipients.stream()
                .filter(r -> r.getEmailAddress() != null && r.getEmailAddress().getAddress() != null)
                .map(r -> new EmailRecipient(r.getEmailAddress().getName(), r.getEmailAddress().getAddress()))
                .toList();
    }
}
