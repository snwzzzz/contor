package com.example.contor.domain;

import com.example.contor.domain.ReportScope.RecordRef;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ReportScopeTest {

    @Test
    public void can_create_a_scope_of_an_anchor_and_its_connections() {
        var scope = new ReportScope(List.of(new RecordRef(1L, 4711L), new RecordRef(2L, 88L)), 1, List.of(7L, 9L));

        assertThat(scope.records()).containsExactly(new RecordRef(1L, 4711L), new RecordRef(2L, 88L));
        assertThat(scope.depth()).isEqualTo(1);
        assertThat(scope.emailIds()).containsExactly(7L, 9L);
    }

    @Test
    public void missing_email_ids_mean_none() {
        assertThat(new ReportScope(List.of(new RecordRef(1L, 1L)), 0, null).emailIds()).isEmpty();
    }

    @Test
    public void the_lists_are_copied() {
        var records = new ArrayList<>(List.of(new RecordRef(1L, 1L)));
        var scope = new ReportScope(records, 0, List.of());
        records.add(new RecordRef(1L, 2L));

        assertThat(scope.records()).hasSize(1);
    }

    @Test
    public void a_scope_needs_at_least_one_record() {
        assertThatThrownBy(() -> new ReportScope(null, 0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportScope(List.of(), 0, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void too_many_records_are_rejected() {
        var records = LongStream.rangeClosed(1, ReportScope.MAX_RECORDS + 1).mapToObj(id -> new RecordRef(1L, id)).toList();

        assertThatThrownBy(() -> new ReportScope(records, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(ReportScope.MAX_RECORDS));
    }

    @Test
    public void duplicate_records_are_rejected() {
        assertThatThrownBy(() -> new ReportScope(List.of(new RecordRef(1L, 1L), new RecordRef(1L, 1L)), 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void the_same_record_id_in_another_collection_is_another_record() {
        assertThat(new ReportScope(List.of(new RecordRef(1L, 1L), new RecordRef(2L, 1L)), 1, List.of()).records()).hasSize(2);
    }

    @Test
    public void depth_is_zero_to_two() {
        var records = List.of(new RecordRef(1L, 1L));

        assertThatThrownBy(() -> new ReportScope(records, -1, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportScope(records, ReportScope.MAX_DEPTH + 1, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void too_many_emails_are_rejected() {
        var emails = LongStream.rangeClosed(1, ReportScope.MAX_EMAILS + 1).boxed().toList();

        assertThatThrownBy(() -> new ReportScope(List.of(new RecordRef(1L, 1L)), 0, emails)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void record_refs_need_positive_ids() {
        assertThatThrownBy(() -> new RecordRef(null, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecordRef(1L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecordRef(0L, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecordRef(1L, -1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
