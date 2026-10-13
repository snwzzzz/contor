package com.example.contor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dev profile only: import the fictional business mailbox ({@code ai-eval/business/mailbox}) into the dev login.
 *
 * @param enabled      import on start (once per dev login)
 * @param path         folder with the generated {@code m-*.json} messages
 * @param shiftToToday move all dates by whole weeks so the newest mail is from the last seven days (weekdays stay right;
 *                     dates written inside the mails do not move)
 */
@ConfigurationProperties("app.demo-mailbox")
public record DemoMailboxProperties(boolean enabled, String path, boolean shiftToToday) {
}
