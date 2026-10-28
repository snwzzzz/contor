package com.example.contor.persistence;

import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ExternalApiCallRepository extends JpaRepository<ExternalApiCall, Long> {

    /** The app's latest calls across its endpoints, newest first. */
    List<ExternalApiCall> findTop50ByEndpoint_Api_IdOrderByStartedAtDesc(ExternalApi.ExternalApiId apiId);

    /** Calls of the app since {@code since} (the daily request limit). */
    long countByEndpoint_Api_IdAndStartedAtGreaterThanEqual(ExternalApi.ExternalApiId apiId, Instant since);

    /** Prunes the history; returns how many calls were removed. */
    @Modifying
    @Query("delete from ExternalApiCall c where c.startedAt < :before")
    int deleteStartedBefore(@Param("before") Instant before);
}
