package com.example.contor.service;

import com.example.contor.api.model.NewRuleCommand;
import com.example.contor.api.model.RuleDto;
import com.example.contor.api.model.UpdateRuleCommand;
import com.example.contor.domain.Rule;
import com.example.contor.exception.RuleNotFoundException;
import com.example.contor.mapper.RuleMapper;
import com.example.contor.persistence.RuleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Rules are stored only in phase 1; their condition/action format is defined together with the rule engine. */
@RequiredArgsConstructor
@Service
public class RuleService {

    private static final int DEFAULT_PRIORITY = 100;

    private final RuleRepository ruleRepository;

    @Transactional
    public RuleDto createRule(NewRuleCommand command) {
        requireName(command.getName());

        Rule rule = Rule.builder()
                .name(command.getName().trim())
                .enabled(command.getEnabled() == null || command.getEnabled())
                .priority(command.getPriority() != null ? command.getPriority() : DEFAULT_PRIORITY)
                .conditions(command.getConditions() != null ? new LinkedHashMap<>(command.getConditions()) : new LinkedHashMap<>())
                .actions(command.getActions() != null ? new ArrayList<>(command.getActions()) : new ArrayList<>())
                .build();

        return RuleMapper.toDto(ruleRepository.saveAndFlush(rule));
    }

    @Transactional
    public List<RuleDto> findAll() {
        return ruleRepository.findAllByOrderByPriorityAscNameAsc().stream()
                .map(RuleMapper::toDto)
                .toList();
    }

    @Transactional
    public RuleDto findById(Long id) {
        return ruleRepository.findById(new Rule.RuleId(id))
                .map(RuleMapper::toDto)
                .orElseThrow(() -> new RuleNotFoundException(id));
    }

    @Transactional
    public RuleDto updateRule(Long id, UpdateRuleCommand command) {
        Rule rule = ruleRepository.findById(new Rule.RuleId(id))
                .orElseThrow(() -> new RuleNotFoundException(id));

        if (command.getName() != null) {
            requireName(command.getName());
            rule.setName(command.getName().trim());
        }
        if (command.getEnabled() != null) {
            rule.setEnabled(command.getEnabled());
        }
        if (command.getPriority() != null) {
            rule.setPriority(command.getPriority());
        }
        if (command.getConditions() != null) {
            rule.setConditions(new LinkedHashMap<>(command.getConditions()));
        }
        if (command.getActions() != null) {
            rule.setActions(new ArrayList<>(command.getActions()));
        }

        return RuleMapper.toDto(ruleRepository.saveAndFlush(rule));
    }

    @Transactional
    public void deleteRule(Long id) {
        if (!ruleRepository.existsById(new Rule.RuleId(id))) {
            throw new RuleNotFoundException(id);
        }
        ruleRepository.deleteById(new Rule.RuleId(id));
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
