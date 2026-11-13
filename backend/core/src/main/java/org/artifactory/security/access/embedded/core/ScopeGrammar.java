/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import org.artifactory.security.access.AppliedPermissionsScopeToken;
import org.artifactory.security.access.ArtifactoryAdminScopeToken;
import org.artifactory.security.access.ArtifactoryRepoPathScopeToken;
import org.artifactory.security.access.MemberOfGroupsScopeToken;
import org.jfrog.access.common.PermissionScopeMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses and validates access-token scopes with the grammar Artifactory already accepts, so a token minted by
 * embedded access means the same as one minted by JFrog Access.
 * <p>
 * A scope is a list of scope tokens, written space-separated in the {@code scp} claim. Each token is classified,
 * in this order, as:
 * <ul>
 *     <li>{@link Api}: {@code api:*};</li>
 *     <li>{@link AppliedUser}: {@code applied-permissions/user}, the identity scope;</li>
 *     <li>{@link Admin}: {@code applied-permissions/admin}, or the v1 form {@code jfrt@<id>:admin} as
 *     {@link ArtifactoryAdminScopeToken} accepts it;</li>
 *     <li>{@link AppliedGroups}: {@code applied-permissions/groups:a,b}, as {@link AppliedPermissionsScopeToken}
 *     parses it;</li>
 *     <li>{@link MemberOfGroups}: {@code member-of-groups:a,b}, as {@link MemberOfGroupsScopeToken} parses it;</li>
 *     <li>{@link RepoPath}: {@code path:<repo/path>:read} or {@code path.checksum:<sha1>:read}, as
 *     {@link ArtifactoryRepoPathScopeToken} accepts it;</li>
 *     <li>{@link Internal}: the internal scopes Artifactory itself requests ({@code internal:mpu:x},
 *     {@code internal:service-trust/pairing/...}, {@code internal:artifactory-cold/...},
 *     {@code internal:federated-repo/...});</li>
 *     <li>{@link ResourcePermission}: a fine-grained permission scope such as {@code artifact:my-repo:r}, as
 *     {@link PermissionScopeMatcher#tryParse} accepts it;</li>
 *     <li>{@link Extension}: a token matching one of the extra patterns this grammar was built with (Artifactory
 *     lets add-ons register accepted scope patterns at run time).</li>
 * </ul>
 * Anything else, an empty scope, or an empty token (two adjacent spaces) is refused. Instances are immutable.
 */
public final class ScopeGrammar {

    /** The separator between scope tokens in the {@code scp} claim. */
    public static final String DELIMITER = " ";

    private static final String API_ALL = "api:*";
    private static final String APPLIED_ADMIN = "applied-permissions/admin";
    private static final String V1_ADMIN_SUFFIX = ":admin";
    private static final List<Pattern> INTERNAL_PATTERNS = List.of(
            Pattern.compile(Pattern.quote("internal:mpu:x")),
            Pattern.compile("internal:service-trust/pairing/.*"),
            Pattern.compile("internal:artifactory-cold/.*"),
            Pattern.compile("internal:federated-repo/.*"));
    private static final ScopeGrammar STANDARD = new ScopeGrammar(List.of());

    private final List<Pattern> extraPatterns;

    private ScopeGrammar(List<Pattern> extraPatterns) {
        this.extraPatterns = extraPatterns;
    }

    /**
     * Returns the grammar with only the built-in token kinds.
     *
     * @return the standard grammar
     */
    public static ScopeGrammar standard() {
        return STANDARD;
    }

    /**
     * Returns a grammar that also accepts tokens matching any of {@code patterns} (whole-token match), classified
     * as {@link Extension}.
     *
     * @param patterns the extra accepted patterns
     * @return the extended grammar
     */
    public ScopeGrammar withExtraPatterns(List<Pattern> patterns) {
        List<Pattern> all = new ArrayList<>(extraPatterns);
        all.addAll(Objects.requireNonNull(patterns, "patterns"));
        return new ScopeGrammar(List.copyOf(all));
    }

    /** One classified scope token; {@link #text()} is the token exactly as written. */
    public sealed interface ScopeToken {
        /**
         * Returns the token as written.
         *
         * @return the token text
         */
        String text();

        /**
         * Tells whether only an administrator may request this token.
         *
         * @return {@code true} for {@link Admin}
         */
        default boolean adminOnly() {
            return false;
        }
    }

    /**
     * {@code api:*}: the token may call the REST API.
     *
     * @param text the token text
     */
    public record Api(String text) implements ScopeToken {
    }

    /**
     * {@code applied-permissions/user}: the token carries the subject user's own permissions.
     *
     * @param text the token text
     */
    public record AppliedUser(String text) implements ScopeToken {
    }

    /**
     * An administrator scope: {@code applied-permissions/admin} (with {@code serviceId == null}), or the v1 form
     * {@code <serviceId>:admin}.
     *
     * @param text      the token text
     * @param serviceId the service id of the v1 form, or {@code null} for the v2 form
     */
    public record Admin(String text, String serviceId) implements ScopeToken {
        /**
         * Returns the service id of the v1 form.
         *
         * @return the service id, or empty for {@code applied-permissions/admin}
         */
        public Optional<String> service() {
            return Optional.ofNullable(serviceId);
        }

        /**
         * Tells whether this token grants administration on the given Artifactory service: the v2 form always
         * does, and the v1 form does when its service id is equal.
         *
         * @param artifactoryServiceId the service id of this Artifactory, for example {@code jfrt@<id>}
         * @return {@code true} if the token is an admin scope for that service
         */
        public boolean appliesTo(String artifactoryServiceId) {
            return serviceId == null || serviceId.equals(artifactoryServiceId);
        }

        @Override
        public boolean adminOnly() {
            return true;
        }
    }

    /**
     * {@code applied-permissions/groups:a,b}: the token carries the permissions of the listed groups.
     *
     * @param text   the token text
     * @param groups the group names, in written order
     */
    public record AppliedGroups(String text, Set<String> groups) implements ScopeToken {
        /** Keeps the groups unmodifiable. */
        public AppliedGroups {
            groups = Collections.unmodifiableSet(groups);
        }
    }

    /**
     * {@code member-of-groups:a,b}: the subject is treated as a member of the listed groups.
     *
     * @param text   the token text
     * @param groups the group names, in written order
     */
    public record MemberOfGroups(String text, Set<String> groups) implements ScopeToken {
        /** Keeps the groups unmodifiable. */
        public MemberOfGroups {
            groups = Collections.unmodifiableSet(groups);
        }
    }

    /**
     * A repository-path read scope, {@code path:<repo/path>:read} or {@code path.checksum:<sha1>:read}.
     *
     * @param text         the token text
     * @param checksumPath whether the path is given as a checksum
     * @param path         the path or checksum part
     */
    public record RepoPath(String text, boolean checksumPath, String path) implements ScopeToken {
    }

    /**
     * An internal scope Artifactory requests for its own service-to-service tokens.
     *
     * @param text the token text
     */
    public record Internal(String text) implements ScopeToken {
    }

    /**
     * A fine-grained permission scope in the {@code <resource-type>:<target>...:<actions>} form.
     *
     * @param text the token text
     */
    public record ResourcePermission(String text) implements ScopeToken {
    }

    /**
     * A token accepted by an extra pattern given to {@link #withExtraPatterns}.
     *
     * @param text the token text
     */
    public record Extension(String text) implements ScopeToken {
    }

    /**
     * Parses a space-separated scope.
     *
     * @param scope the {@code scp} claim value
     * @return the tokens in order, or {@code Refused} naming the first token that is not accepted
     */
    public EaOutcome<List<ScopeToken>> parse(String scope) {
        if (scope == null || scope.isEmpty()) {
            return EaOutcome.refused("scope is empty");
        }
        return parse(List.of(scope.split(DELIMITER, -1)));
    }

    /**
     * Parses a list of scope tokens.
     *
     * @param tokens the tokens
     * @return the tokens in order, or {@code Refused} naming the first token that is not accepted
     */
    public EaOutcome<List<ScopeToken>> parse(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return EaOutcome.refused("scope is empty");
        }
        List<ScopeToken> out = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            EaOutcome<ScopeToken> parsed = parseToken(token);
            if (!(parsed instanceof EaOutcome.Ok<ScopeToken> ok)) {
                return parsed.map(List::of);
            }
            out.add(ok.value());
        }
        return EaOutcome.ok(List.copyOf(out));
    }

    /**
     * Classifies one scope token.
     *
     * @param token the token
     * @return the classified token, or {@code Refused} if it is not accepted
     */
    public EaOutcome<ScopeToken> parseToken(String token) {
        if (token == null || token.isEmpty()) {
            return EaOutcome.refused("empty scope token");
        }
        if (API_ALL.equals(token)) {
            return EaOutcome.ok(new Api(token));
        }
        if (AppliedPermissionsScopeToken.APPLIED_PERMISSIONS_USER.equals(token)) {
            return EaOutcome.ok(new AppliedUser(token));
        }
        if (ArtifactoryAdminScopeToken.accepts(token)) {
            String serviceId = APPLIED_ADMIN.equals(token) ? null
                    : token.substring(0, token.length() - V1_ADMIN_SUFFIX.length());
            return EaOutcome.ok(new Admin(token, serviceId));
        }
        if (AppliedPermissionsScopeToken.accepts(token)) {
            return groups(token, true);
        }
        if (MemberOfGroupsScopeToken.accepts(token)) {
            return groups(token, false);
        }
        if (ArtifactoryRepoPathScopeToken.accepts(token)) {
            boolean checksum = token.startsWith(ArtifactoryRepoPathScopeToken.PATH_CHECKSUM_PREFIX + ":");
            return EaOutcome.ok(new RepoPath(token, checksum, ArtifactoryRepoPathScopeToken.getPath(token)));
        }
        if (matchesAny(INTERNAL_PATTERNS, token)) {
            return EaOutcome.ok(new Internal(token));
        }
        if (permissionScope(token)) {
            return EaOutcome.ok(new ResourcePermission(token));
        }
        if (matchesAny(extraPatterns, token)) {
            return EaOutcome.ok(new Extension(token));
        }
        return EaOutcome.refused("unaccepted scope token: '" + token + "'");
    }

    /**
     * Writes tokens back as a space-separated scope.
     *
     * @param tokens the tokens
     * @return the {@code scp} claim value
     */
    public static String format(List<? extends ScopeToken> tokens) {
        return tokens.stream().map(ScopeToken::text).collect(Collectors.joining(DELIMITER));
    }

    /**
     * Tells whether any token is an admin scope for the given Artifactory service.
     *
     * @param tokens               the parsed scope
     * @param artifactoryServiceId the service id of this Artifactory
     * @return {@code true} if the scope grants administration on it
     */
    public static boolean grantsAdmin(List<? extends ScopeToken> tokens, String artifactoryServiceId) {
        return tokens.stream().anyMatch(t -> t instanceof Admin admin && admin.appliesTo(artifactoryServiceId));
    }

    private static EaOutcome<ScopeToken> groups(String token, boolean applied) {
        try {
            return applied
                    ? EaOutcome.ok(new AppliedGroups(token, AppliedPermissionsScopeToken.parse(token).getGroupNames()))
                    : EaOutcome.ok(new MemberOfGroups(token, MemberOfGroupsScopeToken.parse(token).getGroupNames()));
        } catch (RuntimeException e) {
            return EaOutcome.refused("invalid group scope token: '" + token + "'");
        }
    }

    private static boolean permissionScope(String token) {
        try {
            return PermissionScopeMatcher.tryParse(token).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean matchesAny(List<Pattern> patterns, String token) {
        return patterns.stream().anyMatch(p -> p.matcher(token).matches());
    }
}
