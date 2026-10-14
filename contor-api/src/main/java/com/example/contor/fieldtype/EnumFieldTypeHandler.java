package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** One value out of {@code options.values}. */
@Component
public class EnumFieldTypeHandler extends StringFieldTypeHandler {

    @Override
    public FieldType fieldType() {
        return FieldType.ENUM;
    }

    @Override
    public void validateOptions(Map<String, Object> options) {
        values(options);
    }

    @Override
    public void validate(CollectionField field, Object value) {
        List<String> values = values(field.getOptions());
        if (!values.contains(value)) {
            throw new IllegalArgumentException("must be one of " + values);
        }
    }

    private static List<String> values(Map<String, Object> options) {
        return FieldOptions.distinctStrings(options, "values",
                "ENUM options need a non-empty list 'values'", "values must be distinct, non-blank strings");
    }
}
