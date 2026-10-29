package com.example.contor.service;

import com.example.contor.ai.ChatPlan;
import com.example.contor.ai.EvidencePack;
import com.example.contor.api.model.RecordDto;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Collection;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.domain.ReportScope;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.ExternalApiItemRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a chat answer is written from (H4): the records the plan's searches find in every active collection (the global
 * search of {@code GET /records}, exact titles first), the newest records of the collections it names, and the focus of
 * the conversation; then their facts, connections and mail ({@link ReportScopeService#collectQuestion}). Mail only with
 * the user's mail access, hidden mailboxes only when the AI may read them. Items of the user's apps that are no records
 * and match the searches come along as quoted data ({@code D<n>}), also without mail access.
 */
@RequiredArgsConstructor
@Service
public class ChatRetrievalService {

    /** At most this many records go into an answer's sources (a report's limit). */
    public static final int MAX_RECORDS = ReportScope.MAX_RECORDS;
    /** Hits per search and collection. */
    static final int PER_SEARCH = 3;
    /** Newest records of a collection the plan names. */
    static final int PER_COLLECTION = 15;
    /** App items per search, and in all. */
    static final int APP_ITEMS_PER_SEARCH = 3;
    static final int MAX_APP_ITEMS = 10;

    private final CollectionRepository collectionRepository;
    private final RecordService recordService;
    private final ReportScopeService reportScopeService;
    private final AiSettingsService aiSettingsService;
    private final ExternalApiItemRepository externalApiItemRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<String> collectionSlugs() {
        return active().stream().map(collection -> collection.getSlug().value()).toList();
    }

    /** One line per active collection, for the plan: {@code - slug: Name}. */
    @Transactional
    public String catalog() {
        return active().stream()
                .map(collection -> "- " + collection.getSlug().value() + ": " + collection.getName())
                .collect(Collectors.joining("\n"));
    }

    /** The focus first, then the search hits, then the planned collections' newest records; no duplicates, at most {@link #MAX_RECORDS}. */
    @Transactional
    public List<RecordRef> findRecords(ChatPlan plan, List<RecordRef> focus) {
        Set<RecordRef> found = new LinkedHashSet<>(focus);
        List<Collection> collections = active();
        for (String search : plan.searches()) {
            List<Hit> hits = new ArrayList<>();
            for (Collection collection : collections) {
                for (RecordDto record : recordService.listRecords(collection.getId().id(), null, search, null, 0, PER_SEARCH).getItems()) {
                    hits.add(new Hit(new RecordRef(collection.getId().id(), record.getId()), rank(record.getTitle(), search)));
                }
            }
            hits.stream().sorted(Comparator.comparingInt(Hit::rank)).forEach(hit -> found.add(hit.ref()));
        }
        for (String slug : plan.collections()) {
            collections.stream().filter(collection -> collection.getSlug().value().equals(slug)).findFirst().ifPresent(collection ->
                    recordService.listRecords(collection.getId().id(), null, null, null, 0, PER_COLLECTION).getItems()
                            .forEach(record -> found.add(new RecordRef(collection.getId().id(), record.getId()))));
        }
        return found.stream().limit(MAX_RECORDS).toList();
    }

    /** Whether the AI may read {@code owner}'s mail (Settings → AI). */
    @Transactional
    public boolean mailAccess(AppUser.AppUserId owner) {
        AiSettings settings = owner != null ? aiSettingsService.settingsFor(owner) : null;
        return settings != null && settings.isMailAccess();
    }

    /** The evidence over {@code records} and the owner's mail for {@code question} and the plan's {@code searches}. */
    @Transactional
    public EvidencePack collect(List<RecordRef> records, List<String> searches, String question, AppUser.AppUserId owner) {
        AiSettings settings = owner != null ? aiSettingsService.settingsFor(owner) : null;
        boolean mailAccess = settings != null && settings.isMailAccess();
        boolean readHidden = settings != null && settings.isReadHiddenMailboxes();
        return reportScopeService.collectQuestion(records, question, searches, owner, mailAccess, readHidden, appItems(searches, owner));
    }

    /** The owner's app items (no records) that match a search, last seen first, each once. */
    private List<EvidencePack.AppItemInput> appItems(List<String> searches, AppUser.AppUserId owner) {
        if (owner == null) return List.of();
        Map<Long, EvidencePack.AppItemInput> found = new LinkedHashMap<>();
        for (String search : searches) {
            if (search == null || search.isBlank()) continue;
            for (ExternalApiItem item : externalApiItemRepository.searchForChat(owner.id(), likePattern(search), APP_ITEMS_PER_SEARCH)) {
                if (found.size() == MAX_APP_ITEMS) break;
                found.computeIfAbsent(item.getId(), id -> new EvidencePack.AppItemInput(item.getId(), item.getEndpoint().getApi().getId().id(),
                        item.getEndpoint().getApi().getName(), item.getEndpoint().getName(), item.getItemKey(),
                        objectMapper.writeValueAsString(item.getData()), LocalDate.ofInstant(item.getLastSeenAt(), ZoneOffset.UTC)));
            }
        }
        return List.copyOf(found.values());
    }

    /** {@code %search%} for ILIKE, with {@code %}, {@code _} and the escape character taken literally. */
    private static String likePattern(String search) {
        String escaped = search.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private record Hit(RecordRef ref, int rank) {}

    /** 0 = the title is the search, 1 = it contains it, 2 = only another field does. */
    private static int rank(String title, String search) {
        String text = title == null ? "" : title.strip().toLowerCase(Locale.ROOT);
        String needle = search.toLowerCase(Locale.ROOT);
        return text.equals(needle) ? 0 : text.contains(needle) ? 1 : 2;
    }

    private List<Collection> active() {
        return collectionRepository.findAll().stream().filter(collection -> collection.getArchivedAt() == null).toList();
    }
}
