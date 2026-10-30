package com.example.contor.domain;

import com.example.contor.domain.ChatBlock.Item;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ChatBlockTest {

    @Test
    public void can_create_a_list_with_cited_items() {
        var block = new ChatBlock(ReportBlockKind.BULLETS, null, List.of(new Item("Late delivery.", List.of(1, 2))), null);

        assertThat(block.items()).containsExactly(new Item("Late delivery.", List.of(1, 2)));
        assertThat(block.columns()).isEmpty();
        assertThat(block.rows()).isEmpty();
    }

    @Test
    public void can_create_a_table() {
        var block = new ChatBlock(ReportBlockKind.TABLE, List.of("Customer", "Orders"), null,
                List.of(List.of(new Item("Kessler", null), new Item("3", List.of(4)))));

        assertThat(block.columns()).containsExactly("Customer", "Orders");
        assertThat(block.items()).isEmpty();
        assertThat(block.rows().getFirst().get(0).markers()).isEmpty();
        assertThat(block.rows().getFirst().get(1).markers()).containsExactly(4);
    }

    @Test
    public void the_lists_are_copied() {
        var items = new ArrayList<>(List.of(new Item("One.", List.of())));
        var block = new ChatBlock(ReportBlockKind.PARAGRAPH, null, items, null);
        items.add(new Item("Two.", List.of()));

        assertThat(block.items()).hasSize(1);
    }

    @Test
    public void a_block_needs_a_kind() {
        assertThatThrownBy(() -> new ChatBlock(null, null, List.of(new Item("One.", List.of())), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void only_a_table_has_rows() {
        assertThatThrownBy(() -> new ChatBlock(ReportBlockKind.PARAGRAPH, null, null, List.of(List.of(new Item("x", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void a_table_has_no_items() {
        assertThatThrownBy(() -> new ChatBlock(ReportBlockKind.TABLE, List.of("A"), List.of(new Item("x", null)), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void an_item_needs_text_and_positive_markers() {
        assertThatThrownBy(() -> new Item(null, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item("x", List.of(0))).isInstanceOf(IllegalArgumentException.class);
    }
}
