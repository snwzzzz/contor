package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Amount + ISO 4217 currency: {@code {"amount": 19.99, "currency": "EUR"}}, stored in {@code <column>_amount numeric(19,4)}
 * and {@code <column>_currency char(3)}. Filters and sorting use the amount.
 */
@Component
public class MoneyFieldTypeHandler implements FieldTypeHandler {

    private static final int MAX_SCALE = 4;
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    @Override
    public FieldType fieldType() {
        return FieldType.MONEY;
    }

    @Override
    public List<ColumnSpec> columns(CollectionField field) {
        return List.of(
                ColumnSpec.of(amountColumn(field), SQLDataType.NUMERIC(19, MAX_SCALE)),
                ColumnSpec.of(currencyColumn(field), SQLDataType.CHAR(3)));
    }

    @Override
    public void validate(CollectionField field, Object value) {
        if (!(value instanceof Map<?, ?> money)) {
            throw new IllegalArgumentException("must be an object with amount and currency");
        }
        BigDecimal amount = Decimals.fromJson(money.get("amount"), "amount must be a number");
        if (Decimals.normalize(amount).scale() > MAX_SCALE) {
            throw new IllegalArgumentException("amount must have at most 4 decimal places");
        }
        if (!(money.get("currency") instanceof String currency) || !CURRENCY.matcher(currency).matches() || !isIsoCurrency(currency)) {
            throw new IllegalArgumentException("currency must be an ISO 4217 code");
        }
    }

    @Override
    public Map<String, Object> toDb(CollectionField field, Object value) {
        Map<String, Object> columns = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> money) {
            columns.put(amountColumn(field), Decimals.fromJson(money.get("amount"), "amount must be a number"));
            columns.put(currencyColumn(field), money.get("currency"));
        } else {
            columns.put(amountColumn(field), null);
            columns.put(currencyColumn(field), null);
        }
        return columns;
    }

    @Override
    public Object fromDb(CollectionField field, Record record) {
        BigDecimal amount = record.get(DSL.name(amountColumn(field)), BigDecimal.class);
        if (amount == null) {
            return null;
        }
        Map<String, Object> money = new LinkedHashMap<>();
        money.put("amount", Decimals.normalize(amount));
        money.put("currency", record.get(DSL.name(currencyColumn(field)), String.class));
        return money;
    }

    @Override
    public Object parseFilterValue(CollectionField field, String raw) {
        return Decimals.parse(raw, "must be a number");
    }

    private static boolean isIsoCurrency(String code) {
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String amountColumn(CollectionField field) {
        return field.getColumnName() + "_amount";
    }

    private static String currencyColumn(CollectionField field) {
        return field.getColumnName() + "_currency";
    }
}
