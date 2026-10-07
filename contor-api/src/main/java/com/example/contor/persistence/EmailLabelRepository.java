package com.example.contor.persistence;

import com.example.contor.domain.EmailLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface EmailLabelRepository extends JpaRepository<EmailLabel, EmailLabel.EmailLabelId> {

    List<EmailLabel> findById_EmailId(Long emailId);

    List<EmailLabel> findById_EmailIdIn(Collection<Long> emailIds);
}
