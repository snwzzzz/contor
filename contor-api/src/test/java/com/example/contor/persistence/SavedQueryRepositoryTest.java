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
}
