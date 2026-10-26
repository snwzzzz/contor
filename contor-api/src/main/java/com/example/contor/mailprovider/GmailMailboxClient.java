package com.example.contor.mailprovider;

import com.example.contor.config.GmailProperties;
import com.example.contor.domain.MailProvider;
import com.example.contor.domain.SyncFolder;
import com.example.contor.exception.SyncCursorExpiredException;
import com.example.contor.service.ImageData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Gmail mailboxes through the Gmail REST API, plus the OpenID userinfo (name, picture) and the People API (contact
 * photos).
 * <p>
 * Sync cursors: the stored cursor is a Gmail history id. The first sync lists the folder's label (only mail of the
 * last {@link GmailProperties#initialSyncDays()} days) and ends with the history id read before listing, so nothing
 * that arrives meanwhile is missed; later syncs read the history from there. Page cursors carry their kind, the
 * history id and Gmail's page token ({@code list:<historyId>:<token>}, {@code history:<historyId>:<token>}).
 * <p>
 * Quota: 250 units per user and second; {@code messages.get} costs 5, so at most {@link #DEFAULT_CONCURRENCY}
 * messages are fetched at once, and rate-limited (429) or failed (5xx) requests are retried with backoff.
 */
@Slf4j
@Component
public class GmailMailboxClient implements MailboxClient {

    static final String GMAIL = "https://gmail.googleapis.com/gmail/v1/users/me";
    static final String USERINFO = "https://openidconnect.googleapis.com/v1/userinfo";
    static final String PEOPLE = "https://people.googleapis.com/v1/people/me/connections";

    private static final int PAGE_SIZE = 100;
    private static final int MAX_CONTACTS = 500;
    private static final int MAX_ATTEMPTS = 3;
    private static final int DEFAULT_CONCURRENCY = 4;
    private static final Pattern HISTORY_ID = Pattern.compile("\\d{1,20}");
    private static final Pattern VALID_ADDRESS = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    /** Size suffix of Google profile picture URLs ({@code =s96-c}). */
    private static final Pattern PICTURE_SIZE = Pattern.compile("=s\\d+(-c)?$");

    private final GmailProperties properties;
    private final RestClient restClient;
    private final int concurrency;
    private final Duration backoff;

    @Autowired
    public GmailMailboxClient(GmailProperties properties) {
        this(properties, RestClient.builder(), DEFAULT_CONCURRENCY, Duration.ofSeconds(1));
    }

    GmailMailboxClient(GmailProperties properties, RestClient.Builder restClientBuilder, int concurrency, Duration backoff) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.concurrency = concurrency;
        this.backoff = backoff;
    }

    @Override
    public MailProvider provider() {
        return MailProvider.GMAIL;
    }

    /** The address from Gmail itself (needs only gmail.readonly); the name from the userinfo when the scope allows. */
    @Override
    public MailboxProfile fetchProfile(String accessToken) {
        GmailJson.Profile profile = gmailProfile(accessToken);
        if (profile == null || profile.emailAddress() == null) {
            throw new IllegalStateException("Failed to retrieve the Gmail profile");
        }
        String name = userInfo(accessToken).map(GmailJson.UserInfo::name).orElse(null);
        return new MailboxProfile(profile.emailAddress(), name);
    }

    @Override
    public Optional<ImageData> fetchProfilePhoto(String accessToken) {
        try {
            return userInfo(accessToken)
                    .map(GmailJson.UserInfo::picture)
                    .flatMap(picture -> image(PICTURE_SIZE.matcher(picture).find()
                            ? PICTURE_SIZE.matcher(picture).replaceFirst("=s96-c") : picture));
        } catch (RuntimeException e) {
            log.warn("Profile photo request failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Photos of the user's Google contacts (up to {@value #MAX_CONTACTS}), one entry per contact address; Google's
     * generated letter avatars are skipped. Needs the contacts.readonly scope; without it Google answers 403.
     */
    @Override
    public List<ContactPhotoData> fetchContactPhotos(String accessToken) {
        List<ContactPhotoData> photos = new ArrayList<>();
        String pageToken = null;
        int seen = 0;
        do {
            String token = pageToken;
            GmailJson.Connections page = get(accessToken, uri(PEOPLE, builder -> builder
                    .queryParam("personFields", "emailAddresses,photos")
                    .queryParam("pageSize", PAGE_SIZE)
                    .queryParamIfPresent("pageToken", Optional.ofNullable(token))), GmailJson.Connections.class);
            if (page == null || page.connections() == null) {
                break;
            }
            for (GmailJson.Person person : page.connections()) {
                if (++seen > MAX_CONTACTS) {
                    return photos;
                }
                List<String> addresses = addresses(person);
                Optional<String> url = photoUrl(person);
                if (addresses.isEmpty() || url.isEmpty()) {
                    continue;
                }
                image(url.get()).ifPresent(photo -> addresses.forEach(address -> photos.add(new ContactPhotoData(address, photo))));
            }
            pageToken = page.nextPageToken();
        } while (pageToken != null && !pageToken.isBlank());
        return photos;
    }

    private static List<String> addresses(GmailJson.Person person) {
        if (person.emailAddresses() == null) {
            return List.of();
        }
        return person.emailAddresses().stream()
                .map(GmailJson.PersonEmail::value)
                .filter(address -> address != null && VALID_ADDRESS.matcher(address).matches())
                .map(address -> address.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static Optional<String> photoUrl(GmailJson.Person person) {
        if (person.photos() == null) {
            return Optional.empty();
        }
        return person.photos().stream()
                .filter(photo -> photo.url() != null && !Boolean.TRUE.equals(photo.isDefault()))
                .map(GmailJson.PersonPhoto::url)
                .findFirst();
    }

    @Override
    public FolderChanges fetchChanges(String accessToken, SyncFolder folder, String cursor) {
        String label = folder == SyncFolder.SENT ? "SENT" : "INBOX";
        if (cursor == null || cursor.isBlank()) {
            GmailJson.Profile profile = gmailProfile(accessToken);
            if (profile == null || profile.historyId() == null) {
                throw new IllegalStateException("Gmail returned no history id");
            }
            return listPage(accessToken, label, profile.historyId(), null);
        }
        if (HISTORY_ID.matcher(cursor).matches()) {
            return historyPage(accessToken, label, cursor, null);
        }
        String[] parts = cursor.split(":", 3);
        if (parts.length == 3 && HISTORY_ID.matcher(parts[1]).matches() && !parts[2].isBlank()) {
            if (parts[0].equals("list")) {
                return listPage(accessToken, label, parts[1], parts[2]);
            }
            if (parts[0].equals("history")) {
                return historyPage(accessToken, label, parts[1], parts[2]);
            }
        }
        throw new SyncCursorExpiredException("Not a Gmail sync cursor");
    }

    /** One page of the first sync: the label's messages within the initial window. */
    private FolderChanges listPage(String accessToken, String label, String historyId, String pageToken) {
        GmailJson.MessageList page = get(accessToken, uri(GMAIL + "/messages", builder -> builder
                .queryParam("labelIds", label)
                .queryParam("q", "newer_than:" + properties.initialSyncDays() + "d")
                .queryParam("maxResults", PAGE_SIZE)
                .queryParamIfPresent("pageToken", Optional.ofNullable(pageToken))), GmailJson.MessageList.class);
        List<String> ids = page == null || page.messages() == null ? List.of()
                : page.messages().stream().map(GmailJson.MessageRef::id).toList();
        List<SyncedMessage> messages = fetchMessages(accessToken, ids, label);
        String next = page != null ? page.nextPageToken() : null;
        return next != null && !next.isBlank()
                ? new FolderChanges(messages, "list:" + historyId + ":" + next, null)
                : new FolderChanges(messages, null, historyId);
    }

    /** One page of the history since {@code historyId}: new messages and label changes (read/unread) of the label. */
    private FolderChanges historyPage(String accessToken, String label, String historyId, String pageToken) {
        GmailJson.HistoryList page;
        try {
            page = get(accessToken, uri(GMAIL + "/history", builder -> builder
                    .queryParam("startHistoryId", historyId)
                    .queryParam("labelId", label)
                    .queryParam("historyTypes", "messageAdded", "labelAdded", "labelRemoved")
                    .queryParam("maxResults", PAGE_SIZE)
                    .queryParamIfPresent("pageToken", Optional.ofNullable(pageToken))), GmailJson.HistoryList.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new SyncCursorExpiredException("Gmail history id " + historyId + " is too old");
        }
        Set<String> ids = new LinkedHashSet<>();
        if (page != null && page.history() != null) {
            for (GmailJson.History history : page.history()) {
                addIds(ids, history.messagesAdded());
                addIds(ids, history.labelsAdded());
                addIds(ids, history.labelsRemoved());
            }
        }
        List<SyncedMessage> messages = fetchMessages(accessToken, List.copyOf(ids), label);
        String next = page != null ? page.nextPageToken() : null;
        if (next != null && !next.isBlank()) {
            return new FolderChanges(messages, "history:" + historyId + ":" + next, null);
        }
        String newHistoryId = page != null && page.historyId() != null ? page.historyId() : historyId;
        return new FolderChanges(messages, null, newHistoryId);
    }

    private static void addIds(Set<String> ids, List<GmailJson.MessageChange> changes) {
        if (changes == null) {
            return;
        }
        changes.stream()
                .map(GmailJson.MessageChange::message)
                .filter(message -> message != null && message.id() != null)
                .forEach(message -> ids.add(message.id()));
    }

    /**
     * The full messages in the given order; deleted ones (404) and ones no longer carrying the label (archived since)
     * are left out.
     */
    private List<SyncedMessage> fetchMessages(String accessToken, List<String> ids, String label) {
        SyncFolder folder = label.equals("SENT") ? SyncFolder.SENT : SyncFolder.INBOX;
        List<Optional<GmailJson.Message>> fetched;
        if (concurrency <= 1 || ids.size() <= 1) {
            fetched = ids.stream().map(id -> fetchMessage(accessToken, id)).toList();
        } else {
            fetched = fetchConcurrently(ids, id -> fetchMessage(accessToken, id));
        }
        return fetched.stream()
                .flatMap(Optional::stream)
                .filter(message -> message.labelIds() != null && message.labelIds().contains(label))
                .map(message -> GmailMessageParser.parse(message, folder))
                .toList();
    }

    private <T> List<T> fetchConcurrently(List<String> ids, Function<String, T> fetch) {
        Semaphore permits = new Semaphore(concurrency);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = ids.stream().map(id -> executor.submit(() -> {
                permits.acquire();
                try {
                    return fetch.apply(id);
                } finally {
                    permits.release();
                }
            })).toList();
            List<T> results = new ArrayList<>(futures.size());
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching Gmail messages", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    private Optional<GmailJson.Message> fetchMessage(String accessToken, String id) {
        try {
            return Optional.ofNullable(get(accessToken, uri(GMAIL + "/messages/{id}", builder -> builder.queryParam("format", "full"), id),
                    GmailJson.Message.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty(); // deleted since the history entry
        }
    }

    private GmailJson.Profile gmailProfile(String accessToken) {
        return get(accessToken, URI.create(GMAIL + "/profile"), GmailJson.Profile.class);
    }

    /** The OpenID userinfo; empty without the profile scope or when Google does not answer. */
    private Optional<GmailJson.UserInfo> userInfo(String accessToken) {
        try {
            return Optional.ofNullable(get(accessToken, URI.create(USERINFO), GmailJson.UserInfo.class));
        } catch (RestClientResponseException e) {
            log.debug("Google userinfo unavailable: {}", e.getStatusCode());
            return Optional.empty();
        }
    }

    /**
     * A Google-hosted image (profile or contact photo), loaded without the access token. Other hosts are refused: the
     * URL comes from an API answer, and contor only ever talks to Google here.
     */
    private Optional<ImageData> image(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().endsWith(".googleusercontent.com")) {
            log.debug("Ignoring photo URL on an unexpected host");
            return Optional.empty();
        }
        try {
            byte[] bytes = withRetry(() -> restClient.get().uri(uri).retrieve().body(byte[].class));
            return bytes == null || bytes.length == 0 ? Optional.empty() : Optional.of(new ImageData(bytes, ImageData.contentTypeOf(bytes)));
        } catch (RuntimeException e) {
            log.debug("Photo unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private <T> T get(String accessToken, URI uri, Class<T> type) {
        return withRetry(() -> restClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(type));
    }

    /** Retries 429 and 5xx answers up to {@value #MAX_ATTEMPTS} attempts with growing backoff. */
    private <T> T withRetry(Supplier<T> request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return request.get();
            } catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                sleep(backoff.multipliedBy(attempt));
            }
        }
    }

    private static void sleep(Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry a Gmail request", e);
        }
    }

    private static URI uri(String base, Function<UriBuilder, UriBuilder> query, Object... pathVariables) {
        return query.apply(UriComponentsBuilder.fromUriString(base)).build(pathVariables);
    }
}
