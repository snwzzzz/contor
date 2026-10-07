package com.example.contor.controller;

import com.example.contor.api.RulesApi;
import com.example.contor.api.model.NewRuleCommand;
import com.example.contor.api.model.RuleDto;
import com.example.contor.api.model.UpdateRuleCommand;
import com.example.contor.service.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class RuleController implements RulesApi {

    private final RuleService ruleService;

    @Override
    public ResponseEntity<List<RuleDto>> listRules() {
        return ResponseEntity.ok(ruleService.findAll());
    }

    @Override
    public ResponseEntity<RuleDto> getRule(Long ruleId) {
        return ResponseEntity.ok(ruleService.findById(ruleId));
    }

    @Override
    public ResponseEntity<RuleDto> createRule(NewRuleCommand newRuleCommand) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleService.createRule(newRuleCommand));
    }

    @Override
    public ResponseEntity<RuleDto> updateRule(Long ruleId, UpdateRuleCommand updateRuleCommand) {
        return ResponseEntity.ok(ruleService.updateRule(ruleId, updateRuleCommand));
    }

    @Override
    public ResponseEntity<Void> deleteRule(Long ruleId) {
        ruleService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}
