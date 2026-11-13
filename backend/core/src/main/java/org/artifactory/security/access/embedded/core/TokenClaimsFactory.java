/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Builds the JWT claim set of an access token minted by embedded access, in the shape Artifactory's token parser
 * expects from JFrog Access: {@code sub}, {@code scp}, {@code aud}, {@code iss}, {@code exp} (omitted for a
 * non-expiring token), {@code iat} and {@code jti}. Times are whole epoch seconds; {@code now} is truncated to the
 * second. Signing is not done here.
 * <p>
 * Inputs are validated and every problem is a {@code Refused}: a blank subject or jti (at most
 * {@value #MAX_JTI_LENGTH} characters), an issuer that is not a concrete service id ({@code <type>@<id>}, for
 * example {@code jfac@<id>}), an empty audience or one with a malformed entry ({@code <type>@<id>} where either part
 * may be {@code *}), a scope the {@link ScopeGrammar} refuses, a negative lifetime, or an expiry that overflows.
 * The subject is taken as given; callers build it with Access's {@code SubjectFQN} (for example
 * {@code jfac@<id>/users/admin}).
 */
public final class TokenClaimsFactory {

    /** The Access service type: {@code jfac@<id>} is the issuer of embedded-access tokens. */
    public static final String ACCESS_SERVICE_TYPE = "jfac";
    /** The Artifactory service type, as in {@code jfrt@<id>}. */
    public static final String ARTIFACTORY_SERVICE_TYPE = "jfrt";
    /** The longest accepted token id. */
    public static final int MAX_JTI_LENGTH = 128;

    private static final Pattern INSTANCE_ID = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern CONCRETE_SERVICE_ID = Pattern.compile("[a-z][a-z0-9-]*@[A-Za-z0-9_.-]+");
    private static final Pattern AUDIENCE_ENTRY = Pattern.compile("([a-z][a-z0-9-]*|\\*)@([^\\s@]+|\\*)");

    private final ScopeGrammar grammar;

    /**
     * Creates a factory that validates scopes with {@code grammar}.
     *
     * @param grammar the scope grammar
     */
    public TokenClaimsFactory(ScopeGrammar grammar) {
        this.grammar = Objects.requireNonNull(grammar, "grammar");
    }

    /**
     * The validated claims of one token.
     *
     * @param subject   the {@code sub} claim
     * @param scope     the {@code scp} claim, space-separated
     * @param audience  the {@code aud} entries, at least one
     * @param issuer    the {@code iss} claim
     * @param issuedAt  the {@code iat} claim in epoch seconds
     * @param expiresAt the {@code exp} claim in epoch seconds, or {@code null} for a non-expiring token
     * @param jti       the {@code jti} claim
     */
    public record TokenClaims(String subject, String scope, List<String> audience, String issuer, long issuedAt,
            Long expiresAt, String jti) {

        /** Copies the audience. */
        public TokenClaims {
            audience = List.copyOf(audience);
        }

        /**
         * Returns the expiry as an instant.
         *
         * @return the expiry, or empty for a non-expiring token
         */
        public Optional<Instant> expiry() {
            return Optional.ofNullable(expiresAt).map(Instant::ofEpochSecond);
        }

        /**
         * Returns the claims as an unmodifiable, ordered map ready for a JWT library. {@code aud} is a string when
         * there is one entry and a list otherwise; {@code exp} is absent for a non-expiring token; times are
         * {@code Long} epoch seconds.
         *
         * @return the claim map
         */
        public Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sub", subject);
            m.put("scp", scope);
            m.put("aud", audience.size() == 1 ? audience.get(0) : audience);
            m.put("iss", issuer);
            if (expiresAt != null) {
                m.put("exp", expiresAt);
            }
            m.put("iat", issuedAt);
            m.put("jti", jti);
            return Collections.unmodifiableMap(m);
        }
    }

    /**
     * Validates the inputs and builds the claims.
     *
     * @param subject          the subject
     * @param issuer           the issuing service id, for example {@code jfac@<id>}
     * @param scope            the space-separated scope
     * @param audience         the audience entries, for example {@code jfrt@*}
     * @param now              the issue time
     * @param expiresInSeconds the lifetime in seconds; {@code 0} means the token does not expire
     * @param jti              the token id
     * @return the claims, or {@code Refused} naming the first invalid input
     */
    public EaOutcome<TokenClaims> create(String subject, String issuer, String scope, List<String> audience,
            Instant now, long expiresInSeconds, String jti) {
        if (subject == null || subject.isBlank()) {
            return EaOutcome.refused("subject is blank");
        }
        if (issuer == null || !CONCRETE_SERVICE_ID.matcher(issuer).matches()) {
            return EaOutcome.refused("issuer is not a service id: " + issuer);
        }
        if (audience == null || audience.isEmpty()) {
            return EaOutcome.refused("audience is empty");
        }
        for (String entry : audience) {
            if (entry == null || !AUDIENCE_ENTRY.matcher(entry).matches()) {
                return EaOutcome.refused("audience entry is not a service id: " + entry);
            }
        }
        if (jti == null || jti.isBlank() || jti.length() > MAX_JTI_LENGTH) {
            return EaOutcome.refused("jti is blank or too long");
        }
        if (now == null) {
            return EaOutcome.refused("issue time is missing");
        }
        if (expiresInSeconds < 0) {
            return EaOutcome.refused("expiresIn is negative");
        }
        long iat = now.getEpochSecond();
        Long exp;
        if (expiresInSeconds == 0) {
            exp = null;
        } else if (iat > Long.MAX_VALUE - expiresInSeconds) {
            return EaOutcome.refused("expiry overflows");
        } else {
            exp = iat + expiresInSeconds;
        }
        return grammar.parse(scope).map(tokens -> new TokenClaims(subject, ScopeGrammar.format(tokens),
                audience, issuer, iat, exp, jti));
    }

    /**
     * Returns the Access service id for an instance id: {@code jfac@<instanceId>}.
     *
     * @param instanceId the instance id shared with Artifactory's {@code jfrt@<instanceId>}
     * @return the service id, or {@code Refused} if the instance id is malformed
     */
    public static EaOutcome<String> accessServiceId(String instanceId) {
        return serviceId(ACCESS_SERVICE_TYPE, instanceId);
    }

    /**
     * Returns the Artifactory service id for an instance id: {@code jfrt@<instanceId>}.
     *
     * @param instanceId the instance id
     * @return the service id, or {@code Refused} if the instance id is malformed
     */
    public static EaOutcome<String> artifactoryServiceId(String instanceId) {
        return serviceId(ARTIFACTORY_SERVICE_TYPE, instanceId);
    }

    private static EaOutcome<String> serviceId(String type, String instanceId) {
        if (instanceId == null || !INSTANCE_ID.matcher(instanceId).matches()) {
            return EaOutcome.refused("instance id is malformed: " + instanceId);
        }
        return EaOutcome.ok(type + "@" + instanceId);
    }
}
