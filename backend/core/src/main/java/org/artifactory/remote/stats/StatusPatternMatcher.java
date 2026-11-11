package org.artifactory.remote.stats;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles a status-code filter pattern into a regex {@link Pattern}.
 * <p>
 * Supported formats:
 * <ul>
 *   <li>{@code 404} — exact code</li>
 *   <li>{@code 404,503} — comma-separated codes</li>
 *   <li>{@code 4xx} — group shorthand (400–499)</li>
 *   <li>{@code 4xx,5xx} — multiple groups</li>
 *   <li>{@code 5.*} — raw regex</li>
 * </ul>
 *
 * @author Hussein Alshqerat
 */
public final class StatusPatternMatcher {

    private final Pattern pattern;

    private StatusPatternMatcher(Pattern pattern) {
        this.pattern = pattern;
    }

    /**
     * Compiles the given status pattern string into a matcher.
     *
     * @param statusPattern the pattern string
     * @return a compiled matcher
     * @throws IllegalArgumentException if the pattern is invalid
     */
    public static StatusPatternMatcher compile(String statusPattern) {
        if (statusPattern == null || statusPattern.isBlank()) {
            throw new IllegalArgumentException("statusPattern must not be blank");
        }
        String regex = toRegex(statusPattern);
        try {
            return new StatusPatternMatcher(Pattern.compile("^(" + regex + ")$"));
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid statusPattern regex: " + statusPattern, e);
        }
    }

    /**
     * Tests whether the given HTTP status code matches this pattern.
     */
    public boolean matches(int statusCode) {
        return pattern.matcher(String.valueOf(statusCode)).matches();
    }

    /**
     * Converts the user-facing pattern string into a regex alternation.
     * Handles comma-separated tokens and {@code Nxx} group shorthands.
     */
    private static String toRegex(String input) {
        String[] tokens = input.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            String token = tokens[i].trim();
            if (token.matches("\\d[xX]{2}")) {
                sb.append(token.charAt(0)).append("\\d{2}");
            } else {
                sb.append(token);
            }
        }
        return sb.toString();
    }
}
