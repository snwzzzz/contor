package com.example.contor.persistence;

import com.example.contor.domain.Label;
import com.example.contor.domain.Slug;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabelRepository extends JpaRepository<Label, Label.LabelId> {

    Optional<Label> findBySlug(Slug slug);

    boolean existsBySlug(Slug slug);

    List<Label> findAllByOrderByNameAsc();
}
