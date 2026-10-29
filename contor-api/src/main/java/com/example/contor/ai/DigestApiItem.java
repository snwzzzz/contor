package com.example.contor.ai;

import java.time.Instant;

/**
 * An item of one of the user's apps (external APIs) as one line of the insight digest: it appeared or disappeared
 * recently.
 *
 * @param label what the item is (its record's title, else its key)
 * @param data  the item's JSON, compact (cut in the prompt)
 * @param at    when it appeared, or when it disappeared ({@code gone})
 */
public record DigestApiItem(Long id, String app, String endpoint, String label, String data, Instant at, boolean gone) {
}
