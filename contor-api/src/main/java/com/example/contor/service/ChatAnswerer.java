package com.example.contor.service;

import com.example.contor.ai.ChatPlan;
import com.example.contor.ai.EmailParagraphs;
import com.example.contor.ai.EvidencePack;
import com.example.contor.ai.EvidencePack.Source;
import com.example.contor.ai.OllamaException;
import com.example.contor.ai.References;
import com.example.contor.ai.ReportDraft;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.ChatBlock;
import com.example.contor.domain.ChatCitation;
import com.example.contor.domain.ChatConversation;
import com.example.contor.domain.ChatMention;
import com.example.contor.domain.ChatMessage;
import com.example.contor.domain.ChatMessageState;
import com.example.contor.domain.ChatRole;
import com.example.contor.domain.CitationType;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.ReportBlockKind;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.persistence.ChatMessageRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Writes a chat answer on the AI thread (H5): the model plans what to look up ({@link ChatModelService#plan}), the app
 * finds the records and mail ({@link ChatRetrievalService}), the model answers over them, and the answer is stored as
 * blocks with citations (resolved by {@link References}, numbered per answer) and mentions: the records and emails it
 * cites, or the records found when it cites none. The records it is about become the conversation's focus for the next
 * question. Each state change is its own transaction, so polling sees it. Never logs questions or mail text.
 */
@Slf4j
@Service
public class ChatAnswerer {

    /** Earlier messages the model sees (three turns). */
    static final int HISTORY = 6;
    static final int MAX_MENTIONS = 8;
    /** Records found but not cited that are still linked under an answer. */
    static final int FOUND_MENTIONS = 3;
    static final int MAX_FOCUS = 5;
    private static final int PARAGRAPH_QUOTE_CHARS = 300;
    /** An earlier answer as context for the model, cut to this many characters. */
    private static final int HISTORY_CHARS = 600;
    private static final Pattern QUOTE_MARKS = Pattern.compile("^[\\s\"'„“”‚‘’«»]+|[\\s\"'„“”‚‘’«»]+$");

    private final ChatMessageRepository chatMessageRepository;
    private final CollectionRepository collectionRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final ChatRetrievalService chatRetrievalService;
    private final ChatModelService chatModelService;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ChatAnswerer(ChatMessageRepository chatMessageRepository, CollectionRepository collectionRepository,
                        EmailMessageRepository emailMessageRepository, ChatRetrievalService chatRetrievalService,
                        ChatModelService chatModelService, PlatformTransactionManager transactionManager, Clock clock) {
        this.chatMessageRepository = chatMessageRepository;
        this.collectionRepository = collectionRepository;
        this.emailMessageRepository = emailMessageRepository;
        this.chatRetrievalService = chatRetrievalService;
        this.chatModelService = chatModelService;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** What a run needs from the stored conversation, read in one transaction. */
    private record Job(String question, List<String> history, List<RecordRef> focus, AppUser.AppUserId owner) {}

    /** An answer ready to store: its blocks, the cited source per marker, its mentions and its text. */
    private record Answer(List<ChatBlock> blocks, Map<Integer, Cited> cited, List<ChatMention> mentions, String text) {}

    private record Cited(Source source, String quote) {}

    /** Answers the PENDING message {@code messageId}; a deleted or already finished message is left alone. */
    public void answer(Long messageId) {
        Job job = transactions.execute(status -> start(messageId));
        if (job == null) {
            return;
        }
        try {
            long started = clock.millis();
            ChatPlan plan = chatModelService.plan(job.question(), job.history(), chatRetrievalService.catalog(),
                    chatRetrievalService.collectionSlugs());
            List<RecordRef> records = chatRetrievalService.findRecords(plan, job.focus());
            EvidencePack pack = chatRetrievalService.collect(records, plan.searches(), job.question(), job.owner());
            int planMs = (int) (clock.millis() - started);
            update(messageId, message -> message.setState(ChatMessageState.WRITING));
            long writing = clock.millis();
            ReportDraft draft = chatModelService.answer(pack, job.question(), job.history(), chatRetrievalService.mailAccess(job.owner()));
            int answerMs = (int) (clock.millis() - writing);
            transactions.executeWithoutResult(status -> store(messageId, plan, records, pack, draft, planMs, answerMs));
        } catch (RuntimeException | LinkageError e) {
            log.warn("Chat answer {} failed: {}", messageId, e.getClass().getSimpleName());
            String reason = failure(e);
            try {
                update(messageId, message -> {
                    message.setState(ChatMessageState.FAILED);
                    message.setError(reason);
                });
            } catch (RuntimeException ignored) {
                log.warn("Chat answer {} could not be marked as failed", messageId);
            }
        }
    }

    private Job start(Long messageId) {
        ChatMessage answer = chatMessageRepository.findById(new ChatMessage.ChatMessageId(messageId))
                .filter(message -> message.getState().isActive())
                .orElse(null);
        if (answer == null) {
            return null;
        }
        answer.setState(ChatMessageState.RETRIEVING);
        chatMessageRepository.saveAndFlush(answer);
        ChatConversation conversation = answer.getConversation();
        List<ChatMessage> before = conversation.getMessages().stream()
                .filter(message -> message.getPosition() < answer.getPosition())
                .toList();
        String question = before.isEmpty() ? "" : Optional.ofNullable(before.getLast().getContent()).orElse("");
        List<String> history = before.stream()
                .limit(Math.max(0, before.size() - 1))
                .filter(message -> message.getState() == ChatMessageState.DONE && message.getContent() != null)
                .map(message -> (message.getRole() == ChatRole.USER ? "You: " : "Assistant: ") + cut(message.getContent()))
                .toList();
        history = history.subList(Math.max(0, history.size() - HISTORY), history.size());
        AppUser.AppUserId owner = conversation.getOwner() != null ? conversation.getOwner().getId() : null;
        return new Job(question, history, List.copyOf(conversation.getFocus()), owner);
    }

    private void store(Long messageId, ChatPlan plan, List<RecordRef> records, EvidencePack pack, ReportDraft draft, int planMs, int answerMs) {
        ChatMessage message = chatMessageRepository.findById(new ChatMessage.ChatMessageId(messageId))
                .orElseThrow(() -> new IllegalStateException("Chat message " + messageId + " is gone"));
        Answer answer = build(draft, pack, records);
        message.getCitations().clear();
        answer.cited().forEach((marker, cited) -> message.getCitations().add(citation(message, marker, cited)));
        message.setBlocks(answer.blocks());
        message.setMentions(answer.mentions());
        message.setContent(answer.text());
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("searches", plan.searches());
        stored.put("collections", plan.collections());
        stored.put("records", records.size());
        stored.put("emails", pack.emailIds().size());
        message.setPlan(stored);
        message.setModel(chatModelService.model());
        message.setPromptVersion(ChatModelService.ANSWER_PROMPT_VERSION);
        message.setPlanMs(planMs);
        message.setAnswerMs(answerMs);
        message.setError(null);
        message.setState(ChatMessageState.DONE);
        ChatConversation conversation = message.getConversation();
        List<RecordRef> focus = answer.mentions().stream()
                .filter(mention -> mention.recordId() != null)
                .map(mention -> new RecordRef(mention.collectionId(), mention.recordId()))
                .limit(MAX_FOCUS)
                .toList();
        if (!focus.isEmpty()) {
            conversation.setFocus(new ArrayList<>(focus));
        }
        conversation.setLastModified(clock.instant());
        chatMessageRepository.saveAndFlush(message);
    }

    /** The draft as blocks (a section heading becomes a bold line), with markers numbered per answer in reading order. */
    private Answer build(ReportDraft draft, EvidencePack pack, List<RecordRef> records) {
        Map<String, Integer> markers = new HashMap<>();
        Map<Integer, Cited> cited = new LinkedHashMap<>();
        List<ChatBlock> blocks = new ArrayList<>();
        List<String> text = new ArrayList<>();
        for (ReportDraft.Section section : draft.sections()) {
            if (!section.title().isBlank()) {
                blocks.add(new ChatBlock(ReportBlockKind.PARAGRAPH, null, List.of(new ChatBlock.Item("**" + section.title() + "**", List.of())), null));
                text.add(section.title());
            }
            for (ReportDraft.Block block : section.blocks()) {
                if (block.kind() == ReportBlockKind.TABLE) {
                    List<List<ChatBlock.Item>> rows = new ArrayList<>();
                    for (List<ReportDraft.Claim> row : block.rows()) {
                        List<ChatBlock.Item> cells = new ArrayList<>();
                        for (ReportDraft.Claim cell : row) {
                            cells.add(item(References.resolve(cell.text(), cell.refs(), List.of(), pack), pack, markers, cited));
                        }
                        rows.add(cells);
                        text.add(String.join(" | ", cells.stream().map(ChatBlock.Item::text).toList()));
                    }
                    if (!rows.isEmpty()) {
                        blocks.add(new ChatBlock(ReportBlockKind.TABLE, block.columns(), null, rows));
                    }
                    continue;
                }
                List<ChatBlock.Item> items = new ArrayList<>();
                for (ReportDraft.Claim claim : block.items()) {
                    References.Resolved resolved = References.resolve(claim.text(), claim.refs(), quotes(block.kind(), claim), pack);
                    if (!resolved.text().isBlank()) {
                        items.add(item(resolved, pack, markers, cited));
                        text.add(resolved.text());
                    }
                }
                if (!items.isEmpty()) {
                    blocks.add(new ChatBlock(block.kind(), null, items, null));
                }
            }
        }
        return new Answer(blocks, cited, mentions(cited, pack, records), String.join("\n", text));
    }

    private static ChatBlock.Item item(References.Resolved resolved, EvidencePack pack, Map<String, Integer> markers, Map<Integer, Cited> cited) {
        Map<String, String> quoted = new HashMap<>();
        resolved.quotes().forEach(quote -> quoted.putIfAbsent(quote.token(), quote.text()));
        List<Integer> itemMarkers = new ArrayList<>();
        for (String token : resolved.refs()) {
            pack.source(token).ifPresent(source -> {
                int marker = markers.computeIfAbsent(source.token(), key -> markers.size() + 1);
                cited.putIfAbsent(marker, new Cited(source, quoted.get(token)));
                if (!itemMarkers.contains(marker)) {
                    itemMarkers.add(marker);
                }
            });
        }
        return new ChatBlock.Item(resolved.text(), itemMarkers);
    }

    /** The cited records and emails in marker order; when nothing is cited, the first records found that are in the sources. */
    private List<ChatMention> mentions(Map<Integer, Cited> cited, EvidencePack pack, List<RecordRef> records) {
        Map<String, ChatMention> mentions = new LinkedHashMap<>();
        Map<Long, String> collectionNames = new HashMap<>();
        for (Cited entry : cited.values()) {
            Source source = entry.source();
            if ((source.type() == CitationType.RECORD || source.type() == CitationType.FIELD) && source.recordId() != null) {
                mentions.putIfAbsent("r" + source.collectionId() + "-" + source.recordId(), recordMention(source, collectionNames));
            } else if (source.type() == CitationType.EMAIL_PARAGRAPH && source.emailId() != null) {
                mentions.putIfAbsent("e" + source.emailId(), ChatMention.email(source.emailId(), blankTo(source.label(), "Email")));
            }
        }
        if (mentions.isEmpty()) {
            for (RecordRef ref : records.stream().limit(FOUND_MENTIONS).toList()) {
                pack.sources().stream()
                        .filter(source -> source.type() == CitationType.RECORD && ref.collectionId().equals(source.collectionId())
                                && ref.recordId().equals(source.recordId()))
                        .findFirst()
                        .ifPresent(source -> mentions.putIfAbsent("r" + ref.collectionId() + "-" + ref.recordId(),
                                recordMention(source, collectionNames)));
            }
        }
        return mentions.values().stream().limit(MAX_MENTIONS).toList();
    }

    private ChatMention recordMention(Source source, Map<Long, String> collectionNames) {
        String collection = collectionNames.computeIfAbsent(source.collectionId(), id ->
                collectionRepository.findById(new Collection.CollectionId(id)).map(Collection::getName).orElse(""));
        String label = blankTo(source.recordTitle(), blankTo(collection, "Record") + " #" + source.recordId());
        return ChatMention.record(source.collectionId(), source.recordId(), label, collection.isBlank() ? null : collection);
    }

    private ChatCitation citation(ChatMessage message, int marker, Cited cited) {
        Source source = cited.source();
        ChatCitation.ChatCitationBuilder citation = ChatCitation.builder().message(message).marker(marker).type(source.type());
        if (source.collectionId() != null) {
            citation.collection(collectionRepository.getReferenceById(new Collection.CollectionId(source.collectionId())));
            citation.recordId(source.recordId());
        }
        switch (source.type()) {
            case FIELD -> {
                Map<String, Object> snapshot = new HashMap<>();
                snapshot.put("value", source.value());
                citation.fieldSlug(source.fieldSlug()).valueSnapshot(snapshot);
            }
            case EMAIL_PARAGRAPH -> citation.email(emailMessageRepository.getReferenceById(new EmailMessage.EmailMessageId(source.emailId())))
                    .paragraphIndex(source.paragraphIndex()).paragraphHash(source.paragraphHash())
                    .segmenterVersion(EmailParagraphs.SEGMENTER_VERSION)
                    .quote(cited.quote() != null ? cited.quote() : prefix(source.text(), PARAGRAPH_QUOTE_CHARS));
            case RELATION -> citation.relationId(source.relationId());
            case RECORD -> {
            }
        }
        return citation.build();
    }

    private static List<References.Quote> quotes(ReportBlockKind kind, ReportDraft.Claim item) {
        if (kind != ReportBlockKind.QUOTE) {
            return List.of();
        }
        String text = QUOTE_MARKS.matcher(item.text()).replaceAll("").strip();
        return item.refs().stream().map(ref -> new References.Quote(ref, text)).toList();
    }

    private static String cut(String text) {
        return text.length() <= HISTORY_CHARS ? text : prefix(text, HISTORY_CHARS) + " …";
    }

    /** A verbatim beginning of {@code text}, cut at a word boundary. */
    private static String prefix(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int cut = text.lastIndexOf(' ', max);
        return text.substring(0, cut > 0 ? cut : max);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String failure(Throwable e) {
        if (e instanceof OllamaException) {
            return "The AI model did not answer. Is Ollama running?";
        }
        if (e instanceof IllegalStateException && e.getMessage() != null && e.getMessage().startsWith("The model")) {
            return e.getMessage();
        }
        return "Writing the answer failed. Try again.";
    }

    private void update(Long messageId, Consumer<ChatMessage> change) {
        transactions.executeWithoutResult(status -> chatMessageRepository.findById(new ChatMessage.ChatMessageId(messageId))
                .ifPresent(message -> {
                    change.accept(message);
                    chatMessageRepository.saveAndFlush(message);
                }));
    }
}
