package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.Relation;
import com.example.contor.domain.RelationCreatedBy;
import com.example.contor.domain.RelationType;
import com.example.contor.domain.Slug;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class RelationRepositoryTest {

    @Autowired
    private RelationRepository relationRepository;

    @Autowired
    private RelationTypeRepository relationTypeRepository;

    @Autowired
    private CollectionRepository collectionRepository;

    @Test
    void can_find_relation_type_by_slug() {
        var people = collectionRepository.save(Fixtures.collection("people"));
        relationTypeRepository.save(RelationType.builder().slug(new Slug("knows")).name("knows").inverseName("knows")
                .fromCollection(people).toCollection(people).directed(false).createdBy(CreatedBy.USER).build());

        var found = relationTypeRepository.findBySlug(new Slug("knows"));

        assertThat(found).isPresent();
        assertThat(found.get().isDirected()).isFalse();
        assertThat(found.get().getFromCollection().getId()).isEqualTo(people.getId());
    }

    @Test
    void finds_relations_from_and_to_a_record_pair() {
        var people = collectionRepository.save(Fixtures.collection("people"));
        var orgs = collectionRepository.save(Fixtures.collection("organizations"));
        var worksAt = relationTypeRepository.save(RelationType.builder().slug(new Slug("works_at")).name("works at").inverseName("employs")
                .fromCollection(people).toCollection(orgs).directed(true).createdBy(CreatedBy.USER).build());
        relationRepository.save(Relation.builder().relationType(worksAt)
                .fromCollection(people).fromRecordId(1L).toCollection(orgs).toRecordId(5L)
                .attributes(Map.of("since", "2024")).createdBy(RelationCreatedBy.USER).build());

        assertThat(relationRepository.findByFromCollection_IdAndFromRecordId(people.getId(), 1L))
                .singleElement().satisfies(r -> assertThat(r.getAttributes()).containsEntry("since", "2024"));
        assertThat(relationRepository.findByToCollection_IdAndToRecordId(orgs.getId(), 5L)).hasSize(1);
        assertThat(relationRepository.findByToCollection_IdAndToRecordId(people.getId(), 5L)).isEmpty();
    }

    @Test
    void counts_and_finds_relations_by_type_and_endpoints() {
        var people = collectionRepository.save(Fixtures.collection("people"));
        var knows = relationTypeRepository.save(RelationType.builder().slug(new Slug("knows")).name("knows").directed(false)
                .createdBy(CreatedBy.USER).build());
        relationRepository.save(Relation.builder().relationType(knows).fromCollection(people).fromRecordId(1L)
                .toCollection(people).toRecordId(2L).createdBy(RelationCreatedBy.USER).build());

        assertThat(relationRepository.countByRelationType_Id(knows.getId())).isEqualTo(1);
        assertThat(relationRepository.existsByRelationType_IdAndFromCollection_IdAndFromRecordIdAndToCollection_IdAndToRecordId(
                knows.getId(), people.getId(), 1L, people.getId(), 2L)).isTrue();
        assertThat(relationRepository.existsByRelationType_IdAndFromCollection_IdAndFromRecordIdAndToCollection_IdAndToRecordId(
                knows.getId(), people.getId(), 2L, people.getId(), 1L)).isFalse();
        assertThat(relationTypeRepository.findAllByOrderByNameAsc()).extracting(RelationType::getName).containsExactly("knows");
    }
}
