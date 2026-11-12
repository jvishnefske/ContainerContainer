/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import groovy.lang.Closure;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An authentication realm declared in a plugin's {@code realms} block.
 *
 * <pre>
 * realms {
 *     corp(autoCreateUsers: true, realmPolicy: 'exclusive') {
 *         authenticate { username, credentials -&gt; realm.notMine() }
 *         bearer { token -&gt; realm.authenticated('alice', groups: ['readers']) }
 *         userExists { username -&gt; false }
 *     }
 * }
 * </pre>
 *
 * <p>An {@link Policy#EXCLUSIVE exclusive} realm authenticates: its {@code authenticate} closure is offered Basic
 * credentials and its {@code bearer} closure the token of an {@code Authorization: Bearer} header (see
 * {@link PluginRealmAuthenticator}). An {@link Policy#ADDITIVE additive} realm never authenticates anyone; after
 * another provider authenticated the request, its {@code authenticate} closure is called with the user name and
 * {@code null} credentials and may only add groups. Each closure returns a {@link RealmResult}.
 *
 * <p>Users a realm creates just in time live in the Artifactory realm {@link #userRealm()},
 * {@code plugin:<name>}.
 *
 * @param pluginName      name of the declaring plugin
 * @param name            realm name, {@code [A-Za-z0-9_-]+}, unique across loaded plugins
 * @param policy          whether the realm authenticates or only adds groups
 * @param autoCreateUsers whether a result may ask for a just-in-time user ({@code jit: true}); default false
 * @param authenticate    closure {@code (username, credentials) -> RealmResult}, optional
 * @param bearer          closure {@code (token) -> RealmResult}, optional
 * @param userExists      closure {@code (username) -> boolean}, optional
 */
public record PluginRealm(
        String pluginName,
        String name,
        Policy policy,
        boolean autoCreateUsers,
        Optional<Closure<?>> authenticate,
        Optional<Closure<?>> bearer,
        Optional<Closure<?>> userExists) {

    /** Prefix of the Artifactory realm of users a plugin realm creates. */
    public static final String USER_REALM_PREFIX = "plugin:";

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Set<String> OPTIONS = Set.of("autoCreateUsers", "realmPolicy");

    /** Whether a realm authenticates ({@code exclusive}) or only adds groups after others did ({@code additive}). */
    public enum Policy {
        EXCLUSIVE, ADDITIVE;

        /**
         * Parses the DSL value, ignoring case. Pure.
         *
         * @throws IllegalArgumentException for anything other than {@code exclusive} or {@code additive}
         */
        public static Policy parse(Object value) {
            if (value == null) {
                return EXCLUSIVE;
            }
            String text = String.valueOf(value).toLowerCase(Locale.ROOT);
            return switch (text) {
                case "exclusive" -> EXCLUSIVE;
                case "additive" -> ADDITIVE;
                default -> throw new IllegalArgumentException("realmPolicy must be 'exclusive' or 'additive', not '"
                        + value + "'");
            };
        }
    }

    public PluginRealm {
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(authenticate, "authenticate");
        Objects.requireNonNull(bearer, "bearer");
        Objects.requireNonNull(userExists, "userExists");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Realm name '" + name + "' must match [A-Za-z0-9_-]{1,64}");
        }
        if (policy == Policy.EXCLUSIVE && authenticate.isEmpty() && bearer.isEmpty()) {
            throw new IllegalArgumentException("Realm '" + name + "' declares neither authenticate nor bearer");
        }
        if (policy == Policy.ADDITIVE && (authenticate.isEmpty() || bearer.isPresent())) {
            throw new IllegalArgumentException("Additive realm '" + name
                    + "' must declare authenticate and cannot declare bearer");
        }
    }

    /**
     * Builds a realm from the options map of the DSL. Unknown options fail the load, so a typo cannot silently
     * change the policy.
     *
     * @throws IllegalArgumentException for unknown options or invalid values
     */
    public static PluginRealm fromOptions(String pluginName, String name, Map<?, ?> options,
            Optional<Closure<?>> authenticate, Optional<Closure<?>> bearer, Optional<Closure<?>> userExists) {
        Map<?, ?> opts = options == null ? Map.of() : options;
        for (Object key : opts.keySet()) {
            if (!OPTIONS.contains(String.valueOf(key))) {
                throw new IllegalArgumentException("Unknown realm option '" + key + "' on realm '" + name + "'");
            }
        }
        Object auto = opts.get("autoCreateUsers");
        if (auto != null && !(auto instanceof Boolean)) {
            throw new IllegalArgumentException("autoCreateUsers of realm '" + name + "' must be true or false");
        }
        return new PluginRealm(pluginName, name, Policy.parse(opts.get("realmPolicy")), Boolean.TRUE.equals(auto),
                authenticate, bearer, userExists);
    }

    /** @return the Artifactory realm of users this realm creates, {@code plugin:<name>} */
    public String userRealm() {
        return USER_REALM_PREFIX + name;
    }
}
