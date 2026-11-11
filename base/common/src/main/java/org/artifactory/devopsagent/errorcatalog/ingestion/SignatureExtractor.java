package org.artifactory.devopsagent.errorcatalog.ingestion;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import org.artifactory.devopsagent.errorcatalog.ErrorCatalogEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * Derives the deduplication signature and template for an ILoggingEvent.
 *
 * <p>Strategy:
 * <ul>
 *   <li>If the event carries parameterized arguments ({@code event.getArgumentArray()} is non-empty),
 *       use {@code event.getMessage()} — Logback's raw message pattern with {@code {}} placeholders —
 *       as the template. This is the zero-cost, zero-false-merge path.</li>
 *   <li>Otherwise the message is a concatenated string. Regex-normalize variable-looking content
 *       (UUIDs, numeric ids, hex sequences, file paths, IP addresses) into placeholders so that
 *       repeated occurrences with different runtime values still share a signature.</li>
 * </ul>
 */
public final class SignatureExtractor {

    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern HEX_LONG = Pattern.compile("\\b[0-9a-fA-F]{16,}\\b");
    private static final Pattern NUMBER = Pattern.compile("(?<!\\w)-?\\d+(?:\\.\\d+)?");
    private static final Pattern POSIX_PATH = Pattern.compile("(?:/[^/\\s]++)++");
    private static final Pattern WIN_PATH = Pattern.compile("[A-Za-z]:\\\\\\S++");
    private static final Pattern IPV4 = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b");
    private static final Pattern IPV6 = Pattern.compile(
            "\\b(?:[A-Fa-f0-9]{1,4}:){2,7}[A-Fa-f0-9]{1,4}\\b");

    private SignatureExtractor() {
    }

    public static ErrorCatalogEvent extract(ILoggingEvent event) {
        String severity = event.getLevel().toString();
        String loggerClass = event.getLoggerName();
        String exceptionClass = rootCauseClass(event.getThrowableProxy());

        String rawFormatted = ErrorCatalogLogSanitizer.sanitize(event.getFormattedMessage());

        boolean parameterized = event.getArgumentArray() != null && event.getArgumentArray().length > 0;
        String template;
        if (parameterized) {
            // Logback's raw message pattern, e.g. "User '{}' not found" — already PII-free.
            template = event.getMessage();
        } else {
            template = normalizeForTemplate(rawFormatted);
        }

        String signatureHash = sha256Hex(severity + "|" + loggerClass + "|"
                + (exceptionClass == null ? "" : exceptionClass) + "|"
                + (template == null ? "" : template));

        return new ErrorCatalogEvent(severity, loggerClass, exceptionClass,
                template, rawFormatted, signatureHash, event.getTimeStamp());
    }

    /**
     * Walks the cause chain and returns the root cause's class name, or {@code null} if no throwable.
     */
    private static String rootCauseClass(IThrowableProxy throwable) {
        if (throwable == null) {
            return null;
        }
        IThrowableProxy cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getClassName();
    }

    /**
     * Replaces variable-looking content with stable placeholders. Order matters: longest specific
     * patterns first so they aren't swallowed by NUMBER.
     */
    static String normalizeForTemplate(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        out = UUID.matcher(out).replaceAll("<uuid>");
        out = WIN_PATH.matcher(out).replaceAll("<path>");
        out = POSIX_PATH.matcher(out).replaceAll("<path>");
        out = IPV4.matcher(out).replaceAll("<ip>");
        out = IPV6.matcher(out).replaceAll("<ip>");
        out = HEX_LONG.matcher(out).replaceAll("<hex>");
        out = NUMBER.matcher(out).replaceAll("<n>");
        return out;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by every JRE — but if it's missing we can't compute a signature.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
