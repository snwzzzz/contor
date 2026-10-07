package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Collects every {@link FieldTypeHandler} bean, keyed by its field type. */
@Component
public class FieldTypeRegistry {

    private final Map<FieldType, FieldTypeHandler> handlers = new EnumMap<>(FieldType.class);

    public FieldTypeRegistry(List<FieldTypeHandler> handlers) {
        for (FieldTypeHandler handler : handlers) {
            FieldTypeHandler previous = this.handlers.putIfAbsent(handler.fieldType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Two FieldTypeHandlers for " + handler.fieldType() + ": "
                        + previous.getClass().getSimpleName() + ", " + handler.getClass().getSimpleName());
            }
        }
    }

    public FieldTypeHandler handler(FieldType type) {
        FieldTypeHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalStateException("No FieldTypeHandler for " + type);
        }
        return handler;
    }
}
