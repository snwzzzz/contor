package com.example.contor.ai.eval;

import com.example.contor.ai.ImportanceScorer;
import com.example.contor.domain.ProposalImportance;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The importance rules ({@link ImportanceScorer}) on the labelled business mailbox, without a model: the main proposal of
 * each mail (see {@link ImportanceCase}) is scored with the signals its earlier mail gives and compared with the labelled
 * band. Prints a confusion matrix (rows: labelled, columns: scored) and the mails that are off by two bands or more.
 * The triage's urgency is missing here, so URGENT is under-scored by design; read the matrix with that in mind. Report
 * only; {@code ./mvnw verify -Pai-eval}.
 */
@Tag("ai-eval")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiImportanceEvalTest {

    @Test
    void importance_rules_against_the_labelled_bands() {
        List<BusinessMessage> all = BusinessCorpus.messages();
        Map<ProposalImportance, Map<ProposalImportance, Integer>> matrix = new EnumMap<>(ProposalImportance.class);
        List<ImportanceCase> cases = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            BusinessMessage message = all.get(i);
            if (!message.labelled()) {
                continue;
            }
            ImportanceCase scored = ImportanceCase.of(message, all.subList(0, i));
            if (scored == null || scored.expected() == null) {
                continue;
            }
            cases.add(scored);
            matrix.computeIfAbsent(scored.expected(), key -> new EnumMap<>(ProposalImportance.class)).merge(scored.scored(), 1, Integer::sum);
        }

        StringBuilder table = new StringBuilder("[ai-eval] importance (rules only, %d mails): labelled \\ scored%n".formatted(cases.size()));
        table.append(String.format("%-8s", ""));
        for (ProposalImportance column : ProposalImportance.values()) {
            table.append(String.format("%8s", column));
        }
        table.append('\n');
        for (ProposalImportance row : ProposalImportance.values()) {
            table.append(String.format("%-8s", row));
            for (ProposalImportance column : ProposalImportance.values()) {
                table.append(String.format("%8d", matrix.getOrDefault(row, Map.of()).getOrDefault(column, 0)));
            }
            table.append('\n');
        }
        long exact = cases.stream().filter(c -> c.expected() == c.scored()).count();
        long offByTwo = cases.stream().filter(c -> Math.abs(c.expected().ordinal() - c.scored().ordinal()) >= 2).count();
        table.append("[ai-eval] importance: %d of %d exact, %d off by two bands or more%n".formatted(exact, cases.size(), offByTwo));
        System.out.print(table);
        cases.stream().filter(c -> Math.abs(c.expected().ordinal() - c.scored().ordinal()) >= 2)
                .forEach(c -> System.out.printf("[ai-eval]   %s: labelled %s, scored %s (%s %s, %s)%n", c.messageId(), c.expected(), c.scored(),
                        c.kind(), Objects.requireNonNullElse(c.collection(), ""), c.signals()));

        assertThat(cases).as("labelled business mails with a proposal").isNotEmpty();
    }
}
