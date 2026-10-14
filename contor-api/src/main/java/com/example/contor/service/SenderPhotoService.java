package com.example.contor.service;

import com.example.contor.api.model.ContactPhotoDto;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.ContactPhoto;
import com.example.contor.domain.SenderDomain;
import com.example.contor.domain.SenderLogo;
import com.example.contor.exception.ContactPhotoNotFoundException;
import com.example.contor.exception.SenderLogoNotFoundException;
import com.example.contor.mapper.ContactPhotoMapper;
import com.example.contor.persistence.ContactPhotoRepository;
import com.example.contor.persistence.SenderLogoRepository;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pictures for mail senders: logos of sender domains (fetched from the domain, cached) and the owner's Outlook
 * contact photos (stored by the mail sync).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SenderPhotoService {

    /** Domains resolved per request (one inbox page). */
    private static final int MAX_DOMAINS = 100;
    private static final Duration REFRESH_FOUND = Duration.ofDays(30);
    private static final Duration RETRY_MISSING = Duration.ofDays(7);
    private static final Duration FETCH_BUDGET = Duration.ofSeconds(10);

    private final SenderLogoRepository senderLogoRepository;
    private final ContactPhotoRepository contactPhotoRepository;
    private final LogoFetcher logoFetcher;

    /** Which of the given domains have a logo; unknown or stale domains are fetched (in parallel) and cached first. */
    @Transactional
    public List<String> listLogos(List<String> requested) {
        Set<SenderDomain> domains = requested.stream()
                .limit(MAX_DOMAINS)
                .map(SenderDomain::parse)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (domains.isEmpty()) {
            return List.of();
        }
        Map<SenderDomain, SenderLogo> logos = senderLogoRepository.findByDomainIn(domains).stream()
                .collect(Collectors.toMap(SenderLogo::getDomain, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        Instant now = Instant.now();
        List<SenderDomain> due = domains.stream().filter(domain -> isDue(logos.get(domain), now)).toList();
        if (!due.isEmpty()) {
            Map<SenderDomain, Optional<ImageData>> fetched = fetchAll(due);
            // Fetching took a while: a concurrent request may have cached some of these domains meanwhile. Take the
            // write lock and re-read, so such rows are updated instead of inserted twice (uq_sender_logo_domain).
            senderLogoRepository.lockWrites();
            senderLogoRepository.findByDomainIn(fetched.keySet())
                    .forEach(existing -> logos.put(existing.getDomain(), existing));
            List<SenderLogo> changed = new ArrayList<>();
            fetched.forEach((domain, image) -> {
                SenderLogo logo = logos.computeIfAbsent(domain, d -> SenderLogo.builder().domain(d).build());
                logo.setImage(image.map(ImageData::bytes).orElse(null));
                logo.setContentType(image.map(ImageData::contentType).orElse(null));
                logo.setFetchedAt(now);
                changed.add(logo);
            });
            senderLogoRepository.saveAllAndFlush(changed);
        }
        return domains.stream()
                .filter(domain -> logos.containsKey(domain) && logos.get(domain).getImage() != null)
                .map(SenderDomain::value)
                .toList();
    }

    @Transactional
    public ImageData getLogo(String domain) {
        return SenderDomain.parse(domain)
                .flatMap(senderLogoRepository::findByDomain)
                .filter(logo -> logo.getImage() != null)
                .map(logo -> new ImageData(logo.getImage(), logo.getContentType()))
                .orElseThrow(() -> new SenderLogoNotFoundException(domain));
    }

    @Transactional
    public List<ContactPhotoDto> listContactPhotos() {
        return contactPhotoRepository.findByAccount_Owner_Id(currentUser()).stream()
                .map(ContactPhotoMapper::toDto)
                .toList();
    }

    @Transactional
    public ImageData getContactPhoto(Long id) {
        ContactPhoto photo = contactPhotoRepository.findById(new ContactPhoto.ContactPhotoId(id))
                .filter(candidate -> candidate.getAccount().getOwner().getId().equals(currentUser()))
                .orElseThrow(() -> new ContactPhotoNotFoundException(id));
        return new ImageData(photo.getImage(), photo.getContentType());
    }

    private static boolean isDue(SenderLogo logo, Instant now) {
        if (logo == null) return true;
        Duration maxAge = logo.getImage() != null ? REFRESH_FOUND : RETRY_MISSING;
        return logo.getFetchedAt().isBefore(now.minus(maxAge));
    }

    /** One virtual thread per domain; whatever is not done within the budget counts as "no logo" for now. */
    private Map<SenderDomain, Optional<ImageData>> fetchAll(List<SenderDomain> domains) {
        Map<SenderDomain, Optional<ImageData>> results = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<SenderDomain, Future<Optional<ImageData>>> futures = new LinkedHashMap<>();
            domains.forEach(domain -> futures.put(domain, executor.submit(() -> logoFetcher.fetch(domain))));
            Instant deadline = Instant.now().plus(FETCH_BUDGET);
            for (var entry : futures.entrySet()) {
                try {
                    Duration left = Duration.between(Instant.now(), deadline);
                    results.put(entry.getKey(), entry.getValue().get(Math.max(left.toMillis(), 1), TimeUnit.MILLISECONDS));
                } catch (ExecutionException | TimeoutException e) {
                    entry.getValue().cancel(true);
                    log.debug("Logo fetch for {} did not finish: {}", entry.getKey().value(), e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return results;
    }

    private static AppUser.AppUserId currentUser() {
        return new AppUser.AppUserId(CurrentActor.userId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user")));
    }
}
