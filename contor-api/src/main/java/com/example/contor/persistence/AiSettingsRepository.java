package com.example.contor.persistence;

import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiSettingsRepository extends JpaRepository<AiSettings, AiSettings.AiSettingsId> {

    Optional<AiSettings> findByUser_Id(AppUser.AppUserId userId);

    /** Users whose new mail is processed in the background. */
    List<AiSettings> findByMailAccessTrueAndOnboardedAtIsNotNull();
}
