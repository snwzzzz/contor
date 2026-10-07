package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.Rule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class RuleRepositoryTest {

    @Autowired
    private RuleRepository ruleRepository;

    @Test
    void finds_enabled_rules_by_priority() {
        ruleRepository.save(Rule.builder().name("second").enabled(true).priority(20).conditions(Map.of()).actions(List.of()).build());
        ruleRepository.save(Rule.builder().name("disabled").enabled(false).priority(1).conditions(Map.of()).actions(List.of()).build());
        ruleRepository.save(Rule.builder().name("first").enabled(true).priority(10)
                .conditions(Map.of("from", "shop@example.com"))
                .actions(List.of(Map.of("type", "SET_LABEL", "label", "orders")))
                .build());

        var rules = ruleRepository.findByEnabledTrueOrderByPriorityAsc();

        assertThat(rules).extracting(Rule::getName).containsExactly("first", "second");
        assertThat(rules.get(0).getConditions()).containsEntry("from", "shop@example.com");
        assertThat(rules.get(0).getActions()).hasSize(1);
    }

    @Test
    void lists_all_rules_by_priority_then_name() {
        ruleRepository.save(Rule.builder().name("b").priority(10).build());
        ruleRepository.save(Rule.builder().name("a").priority(10).enabled(false).build());
        ruleRepository.save(Rule.builder().name("c").priority(1).build());

        assertThat(ruleRepository.findAllByOrderByPriorityAscNameAsc()).extracting(Rule::getName).containsExactly("c", "a", "b");
    }
}
