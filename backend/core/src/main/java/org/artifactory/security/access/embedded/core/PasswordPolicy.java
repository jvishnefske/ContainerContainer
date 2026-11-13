/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The IA6 password policy, checked without network access. A password is acceptable when it
 * <ul>
 *     <li>has at least {@value #MIN_LENGTH} characters (Unicode code points);</li>
 *     <li>is not equal to and does not contain the username, ignoring case;</li>
 *     <li>is not in the common-password set, ignoring case.</li>
 * </ul>
 * {@link #check} reports every violation, not only the first.
 * <p>
 * The bundled list ({@value #BUNDLED_RESOURCE}, loaded by {@link #withBundledList()}) is a short self-authored list
 * of obvious passwords. A larger list, for example one of the MIT-licensed SecLists password lists, can replace that
 * resource without code changes: one password per line, blank lines and lines starting with {@code #} ignored.
 */
public final class PasswordPolicy {

    /** The minimum number of code points. */
    public static final int MIN_LENGTH = 12;
    /** The classpath resource holding the bundled common-password list. */
    public static final String BUNDLED_RESOURCE = "embedded-access/common-passwords.txt";

    /** The closed set of policy violations. */
    public enum Violation {
        /** Fewer than {@link #MIN_LENGTH} characters, or no password at all. */
        TOO_SHORT,
        /** Equal to or containing the username, ignoring case. */
        CONTAINS_USERNAME,
        /** In the common-password set, ignoring case. */
        COMMON_PASSWORD
    }

    /**
     * The outcome of a policy check.
     *
     * @param violations the violations found, in {@link Violation} order; empty when acceptable
     */
    public record Verdict(Set<Violation> violations) {
        /** Copies the violations. */
        public Verdict {
            violations = violations.isEmpty() ? Set.of()
                    : Collections.unmodifiableSet(EnumSet.copyOf(violations));
        }

        /**
         * Tells whether the password passed.
         *
         * @return {@code true} if there are no violations
         */
        public boolean acceptable() {
            return violations.isEmpty();
        }
    }

    private final Set<String> commonPasswords;

    /**
     * Creates the policy with a common-password set; entries are compared ignoring case.
     *
     * @param commonPasswords the passwords to reject
     */
    public PasswordPolicy(Set<String> commonPasswords) {
        this.commonPasswords = Objects.requireNonNull(commonPasswords, "commonPasswords").stream()
                .map(PasswordPolicy::fold)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Checks a password for a user.
     *
     * @param username the account name, or {@code null}/blank to skip the username rule
     * @param password the candidate password, or {@code null}
     * @return the verdict
     */
    public Verdict check(String username, String password) {
        EnumSet<Violation> found = EnumSet.noneOf(Violation.class);
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            found.add(Violation.TOO_SHORT);
        }
        if (password != null) {
            String folded = fold(password);
            if (username != null && !username.isBlank() && folded.contains(fold(username))) {
                found.add(Violation.CONTAINS_USERNAME);
            }
            if (commonPasswords.contains(folded)) {
                found.add(Violation.COMMON_PASSWORD);
            }
        }
        return new Verdict(found);
    }

    /**
     * Checks a password and turns violations into a {@code Refused} whose reason lists them.
     *
     * @param username the account name
     * @param password the candidate password
     * @return {@code Ok(DONE)} if acceptable, otherwise {@code Refused}
     */
    public EaOutcome<EaOutcome.Done> validate(String username, String password) {
        Verdict verdict = check(username, password);
        if (verdict.acceptable()) {
            return EaOutcome.done();
        }
        return EaOutcome.refused("password does not meet the policy: " + verdict.violations().stream()
                .map(PasswordPolicy::describe).collect(Collectors.joining("; ")));
    }

    /**
     * Returns the number of distinct entries in the common-password set.
     *
     * @return the set size
     */
    public int commonPasswordCount() {
        return commonPasswords.size();
    }

    /**
     * Parses a common-password list: one entry per line, trimmed; blank lines and {@code #} comments are skipped.
     *
     * @param lines the lines of the list
     * @return the entries
     */
    public static Set<String> parseList(Stream<String> lines) {
        return lines.map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Creates the policy with the bundled list {@value #BUNDLED_RESOURCE} from this class's class loader.
     *
     * @return the policy, or {@code Refused} if the resource is missing, unreadable or empty
     */
    public static EaOutcome<PasswordPolicy> withBundledList() {
        ClassLoader loader = PasswordPolicy.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) {
                return EaOutcome.refused("common-password list not found: " + BUNDLED_RESOURCE);
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                reader.lines().forEach(lines::add);
            }
            Set<String> entries = parseList(lines.stream());
            if (entries.isEmpty()) {
                return EaOutcome.refused("common-password list is empty: " + BUNDLED_RESOURCE);
            }
            return EaOutcome.ok(new PasswordPolicy(entries));
        } catch (IOException | UncheckedIOException e) {
            return EaOutcome.refused("common-password list unreadable: " + BUNDLED_RESOURCE);
        }
    }

    private static String describe(Violation violation) {
        return switch (violation) {
            case TOO_SHORT -> "at least " + MIN_LENGTH + " characters are required";
            case CONTAINS_USERNAME -> "must not contain the username";
            case COMMON_PASSWORD -> "is a common password";
        };
    }

    private static String fold(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
