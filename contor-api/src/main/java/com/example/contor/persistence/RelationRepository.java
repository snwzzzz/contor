package com.example.contor.persistence;

import com.example.contor.domain.Collection;
import com.example.contor.domain.Relation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelationRepository extends JpaRepository<Relation, Relation.RelationId> {

    List<Relation> findByFromCollection_IdAndFromRecordId(Collection.CollectionId collectionId, Long recordId);

    List<Relation> findByToCollection_IdAndToRecordId(Collection.CollectionId collectionId, Long recordId);
}
