package com.example.contor.persistence;

import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.Slug;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollectionFieldRepository extends JpaRepository<CollectionField, CollectionField.CollectionFieldId> {

    List<CollectionField> findByCollection_IdOrderByPositionAsc(Collection.CollectionId collectionId);

    boolean existsByCollection_IdAndSlug(Collection.CollectionId collectionId, Slug slug);
}
