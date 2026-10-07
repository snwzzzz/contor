package com.example.contor.persistence;

import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionSchemaChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollectionSchemaChangeRepository extends JpaRepository<CollectionSchemaChange, Long> {

    List<CollectionSchemaChange> findByCollection_IdOrderByToVersionAsc(Collection.CollectionId collectionId);
}
