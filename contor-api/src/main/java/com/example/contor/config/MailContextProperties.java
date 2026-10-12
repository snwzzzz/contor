package com.example.contor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * What the model gets to see of the user's other mail when it reads an email ({@code app.ai.context.*}, ADR 0009).
 *
 * @param enabled            off: every email is read on its own, as before
 * @param budgetChars        upper bound of the context text in the prompt (reading is cheap, writing the answer is not)
 * @param senderEmails       earlier emails from the same sender
 * @param relatedEmails      emails close in meaning (embeddings)
 * @param relatedMaxDistance cosine distance up to which an email counts as related (0.25 = similarity 0.75)
 * @param relatedWithin      how far back related emails are searched
 */
@ConfigurationProperties("app.ai.context")
public record MailContextProperties(@DefaultValue("true") boolean enabled, @DefaultValue("6000") int budgetChars,
                                    @DefaultValue("5") int senderEmails, @DefaultValue("5") int relatedEmails,
                                    @DefaultValue("0.25") double relatedMaxDistance, @DefaultValue("180d") Duration relatedWithin) {
}
