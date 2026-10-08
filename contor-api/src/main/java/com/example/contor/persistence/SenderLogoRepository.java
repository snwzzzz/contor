package com.example.contor.persistence;

import com.example.contor.domain.SenderDomain;
import com.example.contor.domain.SenderLogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SenderLogoRepository extends JpaRepository<SenderLogo, SenderLogo.SenderLogoId> {

    Optional<SenderLogo> findByDomain(SenderDomain domain);

    List<SenderLogo> findByDomainIn(Collection<SenderDomain> domains);
}
