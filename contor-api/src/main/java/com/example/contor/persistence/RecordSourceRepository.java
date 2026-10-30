package com.example.contor.persistence;

import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.RecordSource;
import com.example.contor.domain.RecordSourceRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecordSourceRepository extends JpaRepository<RecordSource, Long> {

    List<RecordSource> findByCollection_IdAndRecordId(Collection.CollectionId collectionId, Long recordId);

    List<RecordSource> findByEmail_Id(EmailMessage.EmailMessageId emailId);

    List<RecordSource> findByEmail_IdIn(List<EmailMessage.EmailMessageId> emailIds);

    List<RecordSource> findByApiItem_Id(Long apiItemId);

    boolean existsByCollection_IdAndRecordIdAndApiItem_IdAndRole(Collection.CollectionId collectionId, Long recordId, Long apiItemId,
                                                                RecordSourceRole role);
}
