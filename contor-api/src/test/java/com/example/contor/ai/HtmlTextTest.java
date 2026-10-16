package com.example.contor.ai;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HtmlTextTest {

    @Test
    void null_and_blank_become_empty() {
        assertThat(HtmlText.toText(null)).isEmpty();
        assertThat(HtmlText.toText("  ")).isEmpty();
    }

    @Test
    void html_paragraphs_become_paragraphs() {
        assertThat(HtmlText.toText("<html><body><p>Hello Alex,</p><p>your order has <b>shipped</b>.</p></body></html>"))
                .isEqualTo("Hello Alex,\n\nyour order has shipped.");
    }

    @Test
    void line_breaks_and_divs_are_lines() {
        assertThat(HtmlText.toText("<div>Order 4711<br>Total: 110 EUR</div><div>Thanks</div>"))
                .isEqualTo("Order 4711\nTotal: 110 EUR\nThanks");
    }

    @Test
    void outlook_lines_with_spacer_paragraphs_keep_the_spacers_as_paragraph_breaks() {
        String html = "<p class=MsoNormal>Hi Tom,<o:p></o:p></p>"
                + "<p class=MsoNormal>the shipment is held at customs.<o:p></o:p></p>"
                + "<p class=MsoNormal>We expect it on Friday.<o:p></o:p></p>"
                + "<p class=MsoNormal><o:p>&nbsp;</o:p></p>"
                + "<p class=MsoNormal>Best regards<o:p></o:p></p>";

        assertThat(HtmlText.toText(html))
                .isEqualTo("Hi Tom,\nthe shipment is held at customs.\nWe expect it on Friday.\n\nBest regards");
    }

    @Test
    void outlook_web_divs_with_empty_line_divs() {
        assertThat(HtmlText.toText("<div>First line</div><div>second line</div><div><br></div><div>Next paragraph</div>"))
                .isEqualTo("First line\nsecond line\n\nNext paragraph");
    }

    @Test
    void scripts_styles_and_head_are_dropped() {
        assertThat(HtmlText.toText("<html><head><title>T</title><style>p{color:red}</style></head><body><script>x()</script><p>Text</p></body></html>"))
                .isEqualTo("Text");
    }

    @Test
    void quoted_history_is_dropped() {
        assertThat(HtmlText.toText("<p>Friday works.</p><blockquote><p>Can we meet?</p></blockquote>")).isEqualTo("Friday works.");
        assertThat(HtmlText.toText("<div>Friday works.</div><div class=\"gmail_quote\"><div>On Mon … wrote:</div>Can we meet?</div>"))
                .isEqualTo("Friday works.");
    }

    @Test
    void entities_are_decoded_and_non_breaking_spaces_become_spaces() {
        assertThat(HtmlText.toText("<p>Fish&nbsp;&amp;&nbsp;Chips &lt;3 &quot;yes&quot;</p>")).isEqualTo("Fish & Chips <3 \"yes\"");
    }

    @Test
    void whitespace_inside_a_line_is_collapsed() {
        assertThat(HtmlText.toText("<p>Your    order\n   has\tshipped.</p>")).isEqualTo("Your order has shipped.");
    }

    @Test
    void list_items_are_lines_with_markers() {
        assertThat(HtmlText.toText("<p>Please bring:</p><ul><li>passport</li><li>visa</li></ul><ol><li>first</li><li>second</li></ol>"))
                .isEqualTo("Please bring:\n\n- passport\n- visa\n\n1. first\n2. second");
    }

    @Test
    void table_rows_are_lines_and_cells_are_separated() {
        assertThat(HtmlText.toText("<table><tr><td>Item</td><td>Price</td></tr><tr><td>Chair</td><td>49 EUR</td></tr></table>"))
                .isEqualTo("Item | Price\nChair | 49 EUR");
    }
}
