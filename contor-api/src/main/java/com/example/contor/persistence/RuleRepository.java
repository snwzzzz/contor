package com.example.contor.persistence;

import com.example.contor.domain.Rule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleRepository extends JpaRepository<Rule, Rule.RuleId> {

    List<Rule> findByEnabledTrueOrderByPriorityAsc();
}
