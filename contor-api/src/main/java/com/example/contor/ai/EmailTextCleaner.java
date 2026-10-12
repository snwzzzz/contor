package com.example.contor.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reduces an email body to what the sender wrote in this message: quoted lines, reply headers with the history below
 * them, forwarded-message headers, the signature after {@code -- } and mobile footers are removed, whitespace is
 * collapsed, and the text is cut at a word boundary to a budget (embedding models read a limited context).
 */
public final class EmailTextCleaner {

    private static final Pattern REPLY_HEADER = Pattern.compile("^(On|Am) .{3,300}$");
    private static final Pattern WROTE = Pattern.compile("(?i).*\\b(wrote|schrieb)\\b.*:\\s*$");
    private static final Pattern SEPARATOR = Pattern.compile(
            "(?i)^[-_=\\s]{2,}(original message|ursprüngliche nachricht|forwarded message|weitergeleitete nachricht|original-nachricht)[-_=\\s]{2,}$");
    private static final Pattern HEADER_START = Pattern.compile("(?i)^(from|von):\\s.+");
    private static final Pattern HEADER_FOLLOW = Pattern.compile("(?i)^(sent|date|gesendet|datum):\\s.+");
    private static final Pattern MOBILE_FOOTER = Pattern.compile("(?i)^(sent from my|gesendet von meinem|get outlook for).*");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");

    private EmailTextCleaner() {
    }

    public static String clean(String body, int maxChars) {
        if (body == null || body.isBlank()) {
            return "";
        }
        List<String> lines = body.lines().map(line -> line.stripTrailing()).toList();
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (startsHistory(lines, i) || line.equals("--")) {
                break;
            }
            if (line.stripLeading().startsWith(">") || MOBILE_FOOTER.matcher(line.strip()).matches()) {
                continue;
            }
            kept.add(SPACES.matcher(line).replaceAll(" ").strip());
        }
        return truncate(collapseBlankLines(kept), maxChars);
    }

    /** A reply header ("On … wrote:"), a separator line, or a From:/Sent: header block starts at line {@code i}. */
    private static boolean startsHistory(List<String> lines, int i) {
        String line = lines.get(i).strip();
        if (SEPARATOR.matcher(line).matches()) {
            return true;
        }
        if (REPLY_HEADER.matcher(line).matches()
                && (WROTE.matcher(line).matches() || (i + 1 < lines.size() && WROTE.matcher(lines.get(i + 1).strip()).matches()))) {
            return true;
        }
        if (HEADER_START.matcher(line).matches()) {
            for (int j = i + 1; j <= i + 3 && j < lines.size(); j++) {
                if (HEADER_FOLLOW.matcher(lines.get(j).strip()).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String collapseBlankLines(List<String> lines) {
        StringBuilder text = new StringBuilder();
        int blanks = 0;
        for (String line : lines) {
            if (line.isEmpty()) {
                blanks++;
                continue;
            }
            if (!text.isEmpty()) {
                text.append(blanks > 0 ? "\n\n" : "\n");
            }
            blanks = 0;
            text.append(line);
        }
        return text.toString();
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        String cut = text.substring(0, maxChars);
        if (!Character.isWhitespace(text.charAt(maxChars))) {
            int lastSpace = lastWhitespace(cut);
            if (lastSpace > 0) {
                cut = cut.substring(0, lastSpace);
            }
        }
        return cut.strip();
    }

    private static int lastWhitespace(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
