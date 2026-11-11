package org.artifactory.devopsagent.errorcatalog.ingestion;

import org.artifactory.common.ConstantValues;

import java.util.regex.Pattern;

/**
 * Write-time sanitization for free-form log message content before it reaches the
 * {@code error_catalog} table. Replaces well-known secret formats with {@code ****}.
 *
 * <p>All patterns are compiled once and chosen to avoid catastrophic backtracking.</p>
 */
public final class ErrorCatalogLogSanitizer {

    public static final String MASK = "****";

    // Order matters: most specific tokens first so they aren't swallowed by broader patterns.
    private static final Pattern BEARER = Pattern.compile(
            "Bearer\\s+[A-Z0-9._\\-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern JWT = Pattern.compile(
            "eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_.+/=\\-]+");
    private static final Pattern AWS_ACCESS_KEY = Pattern.compile(
            "AKIA[0-9A-Z]{16}");
    private static final Pattern QUERY_STRING_SECRETS = Pattern.compile(
            "([?&](?:token|access_token|password|api_key|secret|sig|scope)=)[^&\\s\"']+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IPV4 = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b");
    private static final Pattern IPV6 = Pattern.compile(
            "\\b(?:[A-Fa-f0-9]{1,4}:){2,7}[A-Fa-f0-9]{1,4}\\b");

    private ErrorCatalogLogSanitizer() {
    }

    /**
     * Returns a sanitized copy of {@code input}. Null/empty inputs are returned unchanged.
     * Never throws — sanitization is on the producer's hot path.
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String out = input;
        try {
            out = BEARER.matcher(out).replaceAll("Bearer " + MASK);
            out = JWT.matcher(out).replaceAll(MASK);
            out = AWS_ACCESS_KEY.matcher(out).replaceAll(MASK);
            out = QUERY_STRING_SECRETS.matcher(out).replaceAll("$1" + MASK);
            if (shouldMaskIp()) {
                out = IPV4.matcher(out).replaceAll(MASK);
                out = IPV6.matcher(out).replaceAll(MASK);
            }
        } catch (RuntimeException ex) {
            // Defensive: never make sanitization worse — return whatever we've masked so far.
            return out;
        }
        return out;
    }

    private static boolean shouldMaskIp() {
        try {
            return ConstantValues.errorCatalogSanitizerMaskIp.getBoolean();
        } catch (RuntimeException ex) {
            // ArtifactoryHome not initialized (e.g., unit tests). Use the documented default.
            return true;
        }
    }
}
