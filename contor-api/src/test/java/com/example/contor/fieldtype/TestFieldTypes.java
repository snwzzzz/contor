package com.example.contor.fieldtype;

/** A registry with every real handler (they are pure, so service tests use them instead of mocks). */
public final class TestFieldTypes {

    private TestFieldTypes() {
    }

    public static FieldTypeRegistry registry() {
        return new FieldTypeRegistry(FieldTypeRegistryTest.allHandlers());
    }
}
