package com.example.contor.service;

import com.example.contor.api.model.NewRuleCommand;
import com.example.contor.api.model.RuleDto;
import com.example.contor.api.model.UpdateRuleCommand;
import com.example.contor.domain.Rule;
import com.example.contor.exception.RuleNotFoundException;
import com.example.contor.persistence.RuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    @Mock
    private RuleRepository ruleRepository;

    @InjectMocks
    private RuleService ruleService;

    @Test
    void createRule_WithDefaults() {
        when(ruleRepository.saveAndFlush(any(Rule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RuleDto result = ruleService.createRule(new NewRuleCommand("Shop mails"));

        assertEquals("Shop mails", result.getName());
        assertTrue(result.getEnabled());
        assertEquals(100, result.getPriority());
        assertTrue(result.getConditions().isEmpty());
        assertTrue(result.getActions().isEmpty());
    }

    @Test
    void createRule_StoresConditionsAndActions() {
        when(ruleRepository.saveAndFlush(any(Rule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RuleDto result = ruleService.createRule(new NewRuleCommand("Shop mails").enabled(false).priority(5)
                .conditions(Map.of("from", "shop@example.com")).actions(List.of(Map.of("type", "SET_LABEL"))));

        assertFalse(result.getEnabled());
        assertEquals(5, result.getPriority());
        assertEquals("shop@example.com", result.getConditions().get("from"));
        assertEquals(1, result.getActions().size());
    }

    @Test
    void createRule_BlankNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> ruleService.createRule(new NewRuleCommand(" ")));
        verify(ruleRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRule_OnlyChangesGivenFields() {
        var rule = Rule.builder().id(new Rule.RuleId(1L)).name("Shop mails").priority(10).conditions(Map.of("a", 1)).build();
        when(ruleRepository.findById(new Rule.RuleId(1L))).thenReturn(Optional.of(rule));
        when(ruleRepository.saveAndFlush(any(Rule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RuleDto result = ruleService.updateRule(1L, new UpdateRuleCommand().enabled(false));

        assertFalse(result.getEnabled());
        assertEquals("Shop mails", result.getName());
        assertEquals(10, result.getPriority());
        assertEquals(1, result.getConditions().get("a"));
    }

    @Test
    void findAll() {
        when(ruleRepository.findAllByOrderByPriorityAscNameAsc()).thenReturn(List.of(Rule.builder().name("x").build()));

        assertEquals(1, ruleService.findAll().size());
    }

    @Test
    void findById_NotFound() {
        when(ruleRepository.findById(new Rule.RuleId(1L))).thenReturn(Optional.empty());

        assertThrows(RuleNotFoundException.class, () -> ruleService.findById(1L));
    }

    @Test
    void deleteRule_NotFound() {
        when(ruleRepository.existsById(new Rule.RuleId(1L))).thenReturn(false);

        assertThrows(RuleNotFoundException.class, () -> ruleService.deleteRule(1L));
        verify(ruleRepository, never()).deleteById(any());
    }
}
