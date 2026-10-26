package com.example.contor.mailprovider;

import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.SyncFolder;
import org.jsoup.parser.Parser;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a Gmail message ({@code format=full}: headers plus the MIME tree with base64url bodies, transfer encoding
 * already removed, original charset kept) into a {@link SyncedMessage}.
 */
final class GmailMessageParser {

    private static final Pattern ENCODED_WORD = Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]*)\\?=");
    /** Whitespace between two encoded words is not part of the text (RFC 2047, section 6.2). */
    private static final Pattern BETWEEN_ENCODED_WORDS = Pattern.compile("(\\?=)\\s+(=\\?)");
    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*\"?([^\";\\s]+)\"?", Pattern.CASE_INSENSITIVE);

    private GmailMessageParser() {
    }

    static SyncedMessage parse(GmailJson.Message message, SyncFolder folder) {
        GmailJson.Part payload = message.payload();
        List<GmailJson.Header> headers = payload != null ? payload.headers() : null;
        Bodies bodies = new Bodies();
        if (payload != null) {
            bodies.collect(payload);
        }
        List<EmailRecipient> from = parseAddresses(header(headers, "From"));
        return SyncedMessage.builder()
                .providerMessageId(message.id())
                .conversationId(message.threadId())
                .internetMessageId(header(headers, "Message-ID"))
                .from(from.isEmpty() ? null : from.get(0))
                .to(parseAddresses(header(headers, "To")))
                .cc(parseAddresses(header(headers, "Cc")))
                .subject(decodeHeader(header(headers, "Subject")))
                .snippet(message.snippet() != null ? Parser.unescapeEntities(message.snippet(), false) : null)
                .bodyHtml(bodies.html)
                .bodyText(bodies.html == null ? bodies.text : null)
                .receivedAt(message.internalDate() != null ? Instant.ofEpochMilli(Long.parseLong(message.internalDate())) : null)
                .read(message.labelIds() == null || !message.labelIds().contains("UNREAD"))
                .hasAttachments(bodies.attachments)
                .build();
    }

    /** Walks the MIME tree depth-first: the first text/plain and text/html parts are the bodies. */
    private static final class Bodies {
        private String text;
        private String html;
        private boolean attachments;

        void collect(GmailJson.Part part) {
            String mimeType = part.mimeType() != null ? part.mimeType().toLowerCase(Locale.ROOT) : "";
            if (part.filename() != null && !part.filename().isBlank()) {
                String disposition = header(part.headers(), "Content-Disposition");
                if (disposition == null || !disposition.trim().toLowerCase(Locale.ROOT).startsWith("inline")) {
                    attachments = true;
                }
                return;
            }
            if (mimeType.startsWith("multipart/")) {
                if (part.parts() != null) {
                    part.parts().forEach(this::collect);
                }
            } else if (mimeType.equals("text/html") && html == null) {
                html = decodeBody(part);
            } else if (mimeType.equals("text/plain") && text == null) {
                text = decodeBody(part);
            }
        }
    }

    private static String decodeBody(GmailJson.Part part) {
        if (part.body() == null || part.body().data() == null) {
            return null;
        }
        byte[] bytes = Base64.getUrlDecoder().decode(part.body().data());
        return new String(bytes, charsetOf(header(part.headers(), "Content-Type")));
    }

    private static Charset charsetOf(String contentType) {
        if (contentType != null) {
            Matcher matcher = CHARSET.matcher(contentType);
            if (matcher.find()) {
                return charset(matcher.group(1));
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static Charset charset(String name) {
        try {
            return Charset.forName(name);
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String header(List<GmailJson.Header> headers, String name) {
        if (headers == null) {
            return null;
        }
        return headers.stream()
                .filter(header -> name.equalsIgnoreCase(header.name()))
                .map(GmailJson.Header::value)
                .findFirst()
                .orElse(null);
    }

    /** Decodes RFC 2047 encoded words ({@code =?UTF-8?B?...?=}, {@code =?ISO-8859-1?Q?...?=}); other text stays as is. */
    static String decodeHeader(String value) {
        if (value == null || !value.contains("=?")) {
            return value;
        }
        String joined = BETWEEN_ENCODED_WORDS.matcher(value).replaceAll("$1$2");
        Matcher matcher = ENCODED_WORD.matcher(joined);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String decoded;
            try {
                Charset charset = charset(matcher.group(1));
                byte[] bytes = matcher.group(2).equalsIgnoreCase("B")
                        ? Base64.getMimeDecoder().decode(matcher.group(3))
                        : decodeQ(matcher.group(3));
                decoded = new String(bytes, charset);
            } catch (RuntimeException e) {
                decoded = matcher.group();
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(decoded));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static byte[] decodeQ(String text) {
        byte[] out = new byte[text.length()];
        int length = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '_') {
                out[length++] = ' ';
            } else if (c == '=' && i + 2 < text.length()) {
                out[length++] = (byte) Integer.parseInt(text.substring(i + 1, i + 3), 16);
                i += 2;
            } else {
                out[length++] = (byte) c;
            }
        }
        byte[] result = new byte[length];
        System.arraycopy(out, 0, result, 0, length);
        return result;
    }

    /**
     * An address header ({@code "Doe, Anna" <anna@acme.com>, bob@example.com}) as recipients; commas inside quotes
     * or angle brackets do not split, entries without an address (groups like {@code undisclosed-recipients:;}) drop.
     */
    static List<EmailRecipient> parseAddresses(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<EmailRecipient> recipients = new ArrayList<>();
        for (String entry : splitAddresses(value)) {
            EmailRecipient recipient = parseAddress(entry.trim());
            if (recipient != null) {
                recipients.add(recipient);
            }
        }
        return recipients;
    }

    private static List<String> splitAddresses(String value) {
        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean bracket = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && quoted && i + 1 < value.length()) {
                current.append(c).append(value.charAt(++i));
                continue;
            }
            if (c == '"') quoted = !quoted;
            else if (c == '<' && !quoted) bracket = true;
            else if (c == '>' && !quoted) bracket = false;
            if (c == ',' && !quoted && !bracket) {
                entries.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        entries.add(current.toString());
        return entries;
    }

    private static EmailRecipient parseAddress(String entry) {
        int open = entry.lastIndexOf('<');
        int close = entry.lastIndexOf('>');
        String address;
        String name = null;
        if (open >= 0 && close > open) {
            address = entry.substring(open + 1, close).trim();
            String rawName = entry.substring(0, open).trim();
            if (rawName.length() >= 2 && rawName.startsWith("\"") && rawName.endsWith("\"")) {
                rawName = rawName.substring(1, rawName.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
            }
            if (!rawName.isBlank()) {
                name = decodeHeader(rawName);
            }
        } else {
            address = entry;
        }
        if (!address.contains("@") || address.contains(" ") || address.contains(":")) {
            return null;
        }
        return new EmailRecipient(name, address);
    }
}
