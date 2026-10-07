package com.example.contor.persistence;

import com.example.contor.domain.RelationType;
import com.example.contor.domain.Slug;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RelationTypeRepository extends JpaRepository<RelationType, RelationType.RelationTypeId> {

    Optional<RelationType> findBySlug(Slug slug);
}
