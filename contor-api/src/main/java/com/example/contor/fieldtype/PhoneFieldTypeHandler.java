package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Phone number as written by people: digits, spaces, + ( ) - . / ; at least 3 digits. */
@Component
public class PhoneFieldTypeHandler extends StringFieldTypeHandler {

    private static final Pattern PATTERN = Pattern.compile("^\\+?[0-9 ()./-]{3,30}$");
    private static final String MESSAGE = "must be a phone number";

    @Override
    public FieldType fieldType() {
        return FieldType.PHONE;
    }

    @Override
    public void validate(CollectionField field, Object value) {
        if (!(value instanceof String text) || !PATTERN.matcher(text).matches()
                || text.chars().filter(Character::isDigit).count() < 3) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }
}
