package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.EmailTextCleaner;
import com.example.contor.ai.EmbeddingClient;
import com.example.contor.ai.OllamaException;
import com.example.contor.api.model.EmbeddingProgressDto;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailEmbedding;
import com.example.contor.domain.EmailMessage;
import com.example.contor.persistence.EmailEmbeddingRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.security.CurrentActor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vectors of the user's mail for semantic search (see ADR 0008). One vector per inbox email and embedding model, made from
 * subject, sender and the cleaned body with the {@code search_document:} prefix nomic-embed-text expects. New mail is embedded
 * by the analysis worker; a backfill on the AI executor catches up on existing mail (and after a model change) and then
 * drops the vectors of other models. Model calls run outside transactions.
 */
@Slf4j
@Service
public class EmailEmbeddingService {

    static final String DOCUMENT_PREFIX = "search_document: ";
    private static final int BATCH_SIZE = 50;
    /** Texts per model call in the backfill: batching halves the time per email, larger batches gain nothing more. */
    private static final int EMBED_CHUNK = 16;
    /** nomic-embed-text reads 2048 tokens by default in Ollama; this stays safely inside (about 1000 tokens). */
    private static final int MAX_BODY_CHARS = 4000;

    private final EmailMessageRepository emailMessageRepository;
    private final EmailEmbeddingRepository emailEmbeddingRepository;
    private final AiSettingsService aiSettingsService;
    private final EmbeddingClient embeddingClient;
    private final AiProperties properties;
    private final AiExecutors aiExecutors;
    private final TransactionTemplate transactions;
    private final AtomicBoolean backfilling = new AtomicBoolean(false);

    public EmailEmbeddingService(EmailMessageRepository emailMessageRepository, EmailEmbeddingRepository emailEmbeddingRepository,
                                 AiSettingsService aiSettingsService, EmbeddingClient embeddingClient, AiProperties properties,
                                 AiExecutors aiExecutors, PlatformTransactionManager transactionManager) {
        this.emailMessageRepository = emailMessageRepository;
        this.emailEmbeddingRepository = emailEmbeddingRepository;
        this.aiSettingsService = aiSettingsService;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
        this.aiExecutors = aiExecutors;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** The text that represents an email: subject, sender, cleaned body. */
    static String documentText(EmailMessage email) {
        String sender = email.getFromName() != null && !email.getFromName().isBlank()
                ? email.getFromName()
                : Optional.ofNullable(email.getFromAddress()).orElse("");
        String body = EmailTextCleaner.clean(AiAnalysisWorker.plainText(email), MAX_BODY_CHARS);
        return DOCUMENT_PREFIX + Optional.ofNullable(email.getSubject()).orElse("") + "\n" + sender + "\n" + body;
    }

    /**
     * Embeds one email with the configured model unless it already has that vector.
     *
     * @return whether a vector was stored
     * @throws OllamaException          the embedding model failed
     * @throws IllegalArgumentException the model returned a vector of the wrong size
     */
    public boolean embedEmail(Long emailId) {
        String model = properties.models().embedding();
        String text = transactions.execute(status -> emailMessageRepository.findById(new EmailMessage.EmailMessageId(emailId))
                .filter(email -> !emailEmbeddingRepository.existsById(new EmailEmbedding.EmailEmbeddingId(emailId, model)))
                .map(EmailEmbeddingService::documentText)
                .orElse(null));
        if (text == null) {
            return false;
        }
        float[] vector = embeddingClient.embed(text);
        if (vector.length != EmailEmbedding.DIMENSIONS) {
            throw new IllegalArgumentException("Model " + model + " returned " + vector.length + " dimensions, expected "
                    + EmailEmbedding.DIMENSIONS);
        }
        transactions.executeWithoutResult(status -> emailEmbeddingRepository.saveAndFlush(
                EmailEmbedding.builder().id(new EmailEmbedding.EmailEmbeddingId(emailId, model)).embedding(vector).build()));
        return true;
    }

    /** Starts a backfill on the AI executor; false when there is nothing to do or one is already running. */
    public boolean startBackfill() {
        if (aiSettingsService.onboardedUsers().isEmpty() || !backfilling.compareAndSet(false, true)) {
            return false;
        }
        try {
            aiExecutors.analysis().execute(this::backfill);
        } catch (RuntimeException e) {
            backfilling.set(false);
            throw e;
        }
        return true;
    }

    /** Catches up regularly: new accounts, a changed embedding model, mail that failed before. */
    @Scheduled(fixedDelayString = "${app.ai.processing.interval:2m}", initialDelayString = "${app.ai.processing.interval:2m}")
    public void backfillMissing() {
        if (properties.processing().enabled()) {
            startBackfill();
        }
    }

    private void backfill() {
        try {
            String model = properties.models().embedding();
            boolean complete = true;
            for (AppUser.AppUserId user : aiSettingsService.onboardedUsers()) {
                complete = backfill(user, model);
                if (!complete) {
                    break;
                }
            }
            if (complete) {
                transactions.executeWithoutResult(status -> emailEmbeddingRepository.deleteById_ModelNot(model));
            }
        } catch (RuntimeException e) {
            log.warn("Embedding backfill failed: {}", e.getMessage());
        } finally {
            backfilling.set(false);
        }
    }

    /** Embeds a user's missing mail batch by batch; false when it had to stop (model unavailable, nothing could be stored). */
    private boolean backfill(AppUser.AppUserId user, String model) {
        while (true) {
            List<Long> ids = transactions.execute(status ->
                    emailMessageRepository.findIdsWithoutEmbedding(user, model, PageRequest.of(0, BATCH_SIZE)));
            if (ids == null || ids.isEmpty()) {
                return true;
            }
            int stored = 0;
            for (int from = 0; from < ids.size(); from += EMBED_CHUNK) {
                try {
                    stored += embedEmails(ids.subList(from, Math.min(from + EMBED_CHUNK, ids.size())), model);
                } catch (OllamaException e) {
                    log.warn("Embedding backfill stopped, the embedding model is not available: {}", e.getMessage());
                    return false;
                }
            }
            if (stored == 0) {
                return false;
            }
        }
    }

    /**
     * Embeds the emails among {@code emailIds} that lack a vector of {@code model} with one model call; a vector of the
     * wrong size skips only its email.
     *
     * @return how many vectors were stored
     * @throws OllamaException the embedding model failed
     */
    private int embedEmails(List<Long> emailIds, String model) {
        Map<Long, String> texts = transactions.execute(status -> {
            Map<Long, String> byId = new LinkedHashMap<>();
            for (Long id : emailIds) {
                emailMessageRepository.findById(new EmailMessage.EmailMessageId(id))
                        .filter(email -> !emailEmbeddingRepository.existsById(new EmailEmbedding.EmailEmbeddingId(id, model)))
                        .ifPresent(email -> byId.put(id, documentText(email)));
            }
            return byId;
        });
        if (texts == null || texts.isEmpty()) {
            return 0;
        }
        List<float[]> vectors = embeddingClient.embedAll(List.copyOf(texts.values()));
        List<EmailEmbedding> embeddings = new ArrayList<>();
        int index = 0;
        for (Long id : texts.keySet()) {
            float[] vector = vectors.get(index++);
            if (vector.length != EmailEmbedding.DIMENSIONS) {
                log.warn("Email {} could not be embedded: model {} returned {} dimensions", id, model, vector.length);
                continue;
            }
            embeddings.add(EmailEmbedding.builder().id(new EmailEmbedding.EmailEmbeddingId(id, model)).embedding(vector).build());
        }
        if (!embeddings.isEmpty()) {
            transactions.executeWithoutResult(status -> emailEmbeddingRepository.saveAllAndFlush(embeddings));
        }
        return embeddings.size();
    }

    /** Indexing progress of the signed-in user; empty without one (background callers). */
    public Optional<EmbeddingProgressDto> progress() {
        return CurrentActor.userId().map(userId -> {
            AppUser.AppUserId user = new AppUser.AppUserId(userId);
            String model = properties.models().embedding();
            return transactions.execute(status -> new EmbeddingProgressDto(
                    Math.toIntExact(emailEmbeddingRepository.countIndexed(user, model)),
                    Math.toIntExact(emailMessageRepository.countIndexable(user)),
                    backfilling.get()));
        });
    }
}
