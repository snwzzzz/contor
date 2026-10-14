package com.example.contor.persistence;

import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.RecordSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecordSourceRepository extends JpaRepository<RecordSource, Long> {

    List<RecordSource> findByCollection_IdAndRecordId(Collection.CollectionId collectionId, Long recordId);

    List<RecordSource> findByEmail_Id(EmailMessage.EmailMessageId emailId);

    List<RecordSource> findByEmail_IdIn(List<EmailMessage.EmailMessageId> emailIds);
}
