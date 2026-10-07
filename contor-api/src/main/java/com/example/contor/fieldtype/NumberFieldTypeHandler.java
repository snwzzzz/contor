package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/** Decimal number ({@code numeric}). Optional {@code min}/{@code max}. */
@Component
public class NumberFieldTypeHandler extends SingleColumnFieldTypeHandler<BigDecimal> {

    private static final String MESSAGE = "must be a number";

    @Override
    public FieldType fieldType() {
        return FieldType.NUMBER;
    }

    @Override
    protected DataType<BigDecimal> dataType() {
        return SQLDataType.NUMERIC;
    }

    @Override
    public void validateOptions(Map<String, Object> options) {
        var min = FieldOptions.decimal(options, "min");
        var max = FieldOptions.decimal(options, "max");
        if (min.isPresent() && max.isPresent() && min.get().compareTo(max.get()) > 0) {
            throw new IllegalArgumentException("min must not be greater than max");
        }
    }

    @Override
    public void validate(CollectionField field, Object value) {
        BigDecimal number = Decimals.fromJson(value, MESSAGE);
        FieldOptions.decimal(field.getOptions(), "min").ifPresent(min -> {
            if (number.compareTo(min) < 0) throw new IllegalArgumentException("must be at least " + Decimals.normalize(min));
        });
        FieldOptions.decimal(field.getOptions(), "max").ifPresent(max -> {
            if (number.compareTo(max) > 0) throw new IllegalArgumentException("must be at most " + Decimals.normalize(max));
        });
    }

    @Override
    protected BigDecimal toDbValue(CollectionField field, Object value) {
        return Decimals.fromJson(value, MESSAGE);
    }

    @Override
    protected Object toApiValue(BigDecimal dbValue) {
        return Decimals.normalize(dbValue);
    }

    @Override
    protected BigDecimal parse(String raw) {
        return Decimals.parse(raw, MESSAGE);
    }
}
