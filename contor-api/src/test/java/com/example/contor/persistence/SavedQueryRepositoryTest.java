package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.SavedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class SavedQueryRepositoryTest {

    @Autowired
    private SavedQueryRepository savedQueryRepository;

    @Test
    void finds_pinned_queries() {
        savedQueryRepository.save(SavedQuery.builder().name("Open deliveries").pinned(true)
                .dsl(Map.of("from", "deliveries", "where", Map.of("status", "OPEN"))).build());
        savedQueryRepository.save(SavedQuery.builder().name("Scratch").dsl(Map.of()).build());

        var pinned = savedQueryRepository.findByPinnedTrueOrderByNameAsc();

        assertThat(pinned).extracting(SavedQuery::getName).containsExactly("Open deliveries");
        assertThat(pinned.get(0).getDsl()).containsEntry("from", "deliveries");
    }

    @Test
    void lists_pinned_queries_first_then_by_name() {
        savedQueryRepository.save(SavedQuery.builder().name("b").dsl(Map.of()).build());
        savedQueryRepository.save(SavedQuery.builder().name("z").pinned(true).dsl(Map.of()).build());
        savedQueryRepository.save(SavedQuery.builder().name("a").dsl(Map.of()).build());

        assertThat(savedQueryRepository.findAllByOrderByPinnedDescNameAsc()).extracting(SavedQuery::getName).containsExactly("z", "a", "b");
    }
}
