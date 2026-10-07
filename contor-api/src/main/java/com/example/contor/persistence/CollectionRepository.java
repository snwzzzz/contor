package com.example.contor.persistence;

import com.example.contor.domain.Collection;
import com.example.contor.domain.Slug;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, Collection.CollectionId> {

    Optional<Collection> findBySlug(Slug slug);

    boolean existsBySlug(Slug slug);

    boolean existsByTableName(String tableName);

    List<Collection> findByArchivedAtIsNullOrderByNameAsc();
}
