package com.example.contor.persistence;

import com.example.contor.domain.ApiStatus;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.Slug;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExternalApiRepository extends JpaRepository<ExternalApi, ExternalApi.ExternalApiId> {

    /** The user's apps by name (the Apps menu). */
    List<ExternalApi> findByOwner_IdOrderByNameAsc(AppUser.AppUserId ownerId);

    /** An app only when it belongs to {@code ownerId} (another user's app is not found). */
    Optional<ExternalApi> findByIdAndOwner_Id(ExternalApi.ExternalApiId id, AppUser.AppUserId ownerId);

    boolean existsByOwner_IdAndSlug(AppUser.AppUserId ownerId, Slug slug);

    /** Enabled apps in one of {@code statuses} whose next call is due at {@code now}. */
    List<ExternalApi> findByEnabledTrueAndStatusInAndNextCallAtLessThanEqual(Collection<ApiStatus> statuses, Instant now);
}
