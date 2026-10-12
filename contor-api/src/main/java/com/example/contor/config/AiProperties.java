package com.example.contor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.ZoneId;

/**
 * Local AI settings ({@code app.ai.*}). Ollama runs natively on the host and must stay bound to localhost.
 *
 * @param models     routing: decides what an email is about (discovery); extraction: fills proposals; embedding:
 *                   embedding model (768 dimensions expected). Routing and extraction default to the same model so
 *                   Ollama keeps one model resident instead of swapping per email.
 * @param processing background processing of new mail for users who completed the AI setup
 * @param timezone   the user's time zone: local times in emails ("Friday 9:00") are read in it (the JVM runs in UTC)
 */
@ConfigurationProperties("app.ai")
public record AiProperties(@DefaultValue Ollama ollama, @DefaultValue Models models, @DefaultValue Processing processing,
                           @DefaultValue("Europe/Berlin") ZoneId timezone) {

    /**
     * @param timeout   read timeout of one model call (a long email on a busy GPU can take a while)
     * @param numCtx    context window in tokens; larger needs more VRAM
     * @param keepAlive how long Ollama keeps the model loaded after a call
     */
    public record Ollama(
            @DefaultValue("http://127.0.0.1:11434") URI baseUrl,
            @DefaultValue("5m") Duration timeout,
            @DefaultValue("16384") int numCtx,
            @DefaultValue("30m") String keepAlive) {

        public Ollama {
            if (!isLoopback(baseUrl)) {
                throw new IllegalArgumentException("app.ai.ollama.base-url must point to localhost (127.0.0.1, ::1 or localhost), got " + baseUrl);
            }
        }

        /** Only literal loopback addresses or {@code localhost}; host names are never resolved. */
        private static boolean isLoopback(URI uri) {
            String host = uri != null ? uri.getHost() : null;
            if (host == null) {
                return false;
            }
            if (host.equalsIgnoreCase("localhost")) {
                return true;
            }
            String literal = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
            if (!literal.matches("[0-9.]+|[0-9a-fA-F:]+")) {
                return false;
            }
            try {
                return InetAddress.getByName(literal).isLoopbackAddress();
            } catch (UnknownHostException e) {
                return false;
            }
        }
    }

    public record Models(
            @DefaultValue("qwen3.5:9b") String routing,
            @DefaultValue("qwen3.5:9b") String extraction,
            @DefaultValue("nomic-embed-text") String embedding) {
    }

    /**
     * @param interval  pause between two processing passes
     * @param batchSize emails per user and pass
     */
    public record Processing(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("2m") Duration interval,
            @DefaultValue("20") int batchSize) {
    }
}
