package com.example.contor.externalapi;

import java.time.Duration;

/**
 * The answer of one external API call.
 *
 * @param contentType the {@code Content-Type} header, {@code null} when missing
 * @param body        decoded with the answer's charset (UTF-8 when it names none)
 * @param truncated   the answer was longer than {@code app.external-apis.max-response-size} and is cut there
 * @param retryAfter  how long the server asks to wait ({@code Retry-After}), {@code null} when it does not say
 */
public record ApiResponse(int status, String contentType, String body, boolean truncated, Duration retryAfter) {

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
