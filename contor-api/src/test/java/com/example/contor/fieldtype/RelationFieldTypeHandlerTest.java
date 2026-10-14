package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import java.util.Map;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static com.example.contor.fieldtype.FieldFixtures.record;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RelationFieldTypeHandlerTest {

    private final RelationFieldTypeHandler handler = new RelationFieldTypeHandler();
    private final Map<String, Object> options = Map.of("targetCollectionId", 7);

    @Test
    void uses_a_bigint_column_referencing_the_target_collection() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.RELATION);
        assertThat(handler.columns(field(FieldType.RELATION, options)))
                .containsExactly(new ColumnSpec("f", SQLDataType.BIGINT, 7L));
        assertThat(RelationFieldTypeHandler.targetCollectionId(field(FieldType.RELATION, options))).isEqualTo(7L);
    }

    @Test
    void accepts_positive_integral_record_ids() {
        assertThatCode(() -> handler.validate(field(FieldType.RELATION, options), 12)).doesNotThrowAnyException();
        assertThatCode(() -> handler.validate(field(FieldType.RELATION, options), 12L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(field(FieldType.RELATION, options), 0)).hasMessage("must be a record id");
        assertThatThrownBy(() -> handler.validate(field(FieldType.RELATION, options), 1.5)).hasMessage("must be a record id");
        assertThatThrownBy(() -> handler.validate(field(FieldType.RELATION, options), "12")).hasMessage("must be a record id");
    }

    @Test
    void requires_a_target_collection_option() {
        assertThatCode(() -> handler.validateOptions(options)).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validateOptions(Map.of())).hasMessage("RELATION options need 'targetCollectionId'");
        assertThatThrownBy(() -> handler.validateOptions(Map.of("targetCollectionId", "x"))).hasMessage("RELATION options need 'targetCollectionId'");
    }

    @Test
    void maps_to_and_from_db() {
        assertThat(handler.toDb(field(FieldType.RELATION, options), 12)).containsExactly(entry("f", 12L));
        assertThat(handler.fromDb(field(FieldType.RELATION, options), record("f", 12L))).isEqualTo(12L);
        assertThat(handler.parseFilterValue(field(FieldType.RELATION, options), "12")).isEqualTo(12L);
    }
}
