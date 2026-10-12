package com.example.contor.service;

import com.example.contor.domain.EmailRecipient;
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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MsGraphMailService {

    public record UserProfile(String emailAddress, String displayName) {}

    public record DeltaResult(List<Message> messages, String nextLink, String newDeltaLink) {}

    public record ContactPhotoData(String emailAddress, ImageData photo) {}

    private static final String PHOTO_SIZE = "96x96";
    private static final int MAX_CONTACTS = 500;
    private static final Pattern VALID_ADDRESS = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public GraphServiceClient createClient(String accessToken) {
        AuthenticationProvider authProvider = (request, context) ->
                request.headers.add("Authorization", "Bearer " + accessToken);
        return new GraphServiceClient(authProvider);
    }

    public UserProfile fetchUserProfile(String accessToken) {
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
            return new ImageData(bytes, contentTypeOf(bytes));
        }
    }

    /** Image type from the file signature (Graph returns JPEG for nearly all photos). */
    public static String contentTypeOf(byte[] bytes) {
        if (bytes.length >= 4 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 3 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        return "image/jpeg";
    }

    public static UserProfile extractProfile(User user) {
        String email = user.getMail() != null && !user.getMail().isBlank()
                ? user.getMail()
                : user.getUserPrincipalName();
        return new UserProfile(email, user.getDisplayName());
    }

    public DeltaResult fetchMessagesDelta(String accessToken, String deltaLinkOrNextLink) {
        return fetchFolderDelta(accessToken, "inbox", deltaLinkOrNextLink);
    }

    /** Delta of the Sent Items folder (covered by the Mail.Read scope). */
    public DeltaResult fetchSentMessagesDelta(String accessToken, String deltaLinkOrNextLink) {
        return fetchFolderDelta(accessToken, "sentitems", deltaLinkOrNextLink);
    }

    private DeltaResult fetchFolderDelta(String accessToken, String wellKnownFolder, String deltaLinkOrNextLink) {
        GraphServiceClient client = createClient(accessToken);
        DeltaGetResponse response;
        if (deltaLinkOrNextLink == null || deltaLinkOrNextLink.isBlank()) {
            response = client.me().mailFolders().byMailFolderId(wellKnownFolder).messages().delta().get();
        } else {
            response = new DeltaRequestBuilder(deltaLinkOrNextLink, client.getRequestAdapter()).get();
        }

        if (response == null) {
            return new DeltaResult(Collections.emptyList(), null, null);
        }
        return new DeltaResult(
                response.getValue() != null ? response.getValue() : Collections.emptyList(),
                response.getOdataNextLink(),
                response.getOdataDeltaLink()
        );
    }

    public static List<EmailRecipient> mapRecipients(List<Recipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return Collections.emptyList();
        }
        return recipients.stream()
                .filter(r -> r.getEmailAddress() != null && r.getEmailAddress().getAddress() != null)
                .map(r -> new EmailRecipient(r.getEmailAddress().getName(), r.getEmailAddress().getAddress()))
                .toList();
    }
}
