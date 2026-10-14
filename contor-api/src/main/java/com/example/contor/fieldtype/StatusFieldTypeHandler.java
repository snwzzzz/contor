package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Workflow state: ordered {@code options.states}; optional {@code options.transitions} maps a state to the states
 * reachable from it (no transitions = any change allowed). New records start in the first state.
 */
@Component
public class StatusFieldTypeHandler extends StringFieldTypeHandler {

    @Override
    public FieldType fieldType() {
        return FieldType.STATUS;
    }

    @Override
    public void validateOptions(Map<String, Object> options) {
        List<String> states = states(options);
        Object transitions = FieldOptions.get(options, "transitions").orElse(null);
        if (transitions == null) {
            return;
        }
        if (!(transitions instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("transitions must map a state to a list of states");
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof List<?> targets)) {
                throw new IllegalArgumentException("transitions must map a state to a list of states");
            }
            if (!states.contains(entry.getKey()) || !states.containsAll(targets)) {
                throw new IllegalArgumentException("transitions may only reference states");
            }
        }
    }

    @Override
    public void validate(CollectionField field, Object value) {
        List<String> states = states(field.getOptions());
        if (!states.contains(value)) {
            throw new IllegalArgumentException("must be one of " + states);
        }
    }

    @Override
    public void validateChange(CollectionField field, Object oldValue, Object newValue) {
        if (oldValue == null || newValue == null || Objects.equals(oldValue, newValue)) {
            return;
        }
        Object transitions = FieldOptions.get(field.getOptions(), "transitions").orElse(null);
        if (!(transitions instanceof Map<?, ?> map)) {
            return;
        }
        Object allowed = map.get(oldValue);
        if (!(allowed instanceof List<?> targets) || !targets.contains(newValue)) {
            throw new IllegalArgumentException("transition from '" + oldValue + "' to '" + newValue + "' is not allowed");
        }
    }

    @Override
    public Object defaultValue(CollectionField field) {
        return states(field.getOptions()).getFirst();
    }

    private static List<String> states(Map<String, Object> options) {
        return FieldOptions.distinctStrings(options, "states",
                "STATUS options need a non-empty list 'states'", "states must be distinct, non-blank strings");
    }
}
