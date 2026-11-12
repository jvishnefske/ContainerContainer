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

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The {@code realm} binding of plugin scripts: builds {@link RealmResult} values. Stateless and immutable, so one
 * instance serves every plugin.
 *
 * <pre>
 * realm.authenticated('alice')
 * realm.authenticated('ci-bot', groups: ['ci'], jit: false, expiresAt: claims.exp)
 * realm.notMine()
 * realm.rejected('token expired')
 * </pre>
 *
 * <p>Options of {@code authenticated} are {@code groups} (a list of names), {@code jit} (boolean),
 * {@code expiresAt} (epoch seconds) and {@code email}. An unknown option or a value of the wrong type throws
 * {@link IllegalArgumentException}, which the realm invoker turns into "not mine", so a mistake never authenticates.
 */
public final class RealmResults {

    /** The shared instance bound as {@code realm}. */
    public static final RealmResults INSTANCE = new RealmResults();

    private static final Set<String> OPTIONS = Set.of("groups", "jit", "expiresAt", "email");

    private RealmResults() {
    }

    /** @return authenticated as {@code username}, no groups, no just-in-time creation */
    public RealmResult authenticated(String username) {
        return authenticated(Map.of(), username);
    }

    /**
     * Groovy calls this for {@code realm.authenticated(name, groups: [...], jit: true)}: named arguments arrive
     * as the leading map.
     */
    public RealmResult authenticated(Map<?, ?> options, String username) {
        if (username == null) {
            throw new IllegalArgumentException("authenticated() needs a user name");
        }
        Map<?, ?> opts = options == null ? Map.of() : options;
        for (Object key : opts.keySet()) {
            if (!OPTIONS.contains(String.valueOf(key))) {
                throw new IllegalArgumentException("Unknown option '" + key + "' of realm.authenticated");
            }
        }
        return new RealmResult.Authenticated(username, groups(opts.get("groups")), jit(opts.get("jit")),
                expiresAt(opts.get("expiresAt")), email(opts.get("email")));
    }

    /** @return the credentials are not this realm's */
    public RealmResult notMine() {
        return new RealmResult.NotMine();
    }

    /** @return the credentials are this realm's and are refused, for {@code reason} (logged only) */
    public RealmResult rejected(String reason) {
        return new RealmResult.Rejected(reason == null ? "rejected" : reason);
    }

    private static Set<String> groups(Object value) {
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof Iterable<?> items)) {
            throw new IllegalArgumentException("groups must be a list of group names");
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : items) {
            if (!(item instanceof CharSequence name)) {
                throw new IllegalArgumentException("groups must be a list of group names");
            }
            result.add(name.toString());
        }
        return result;
    }

    private static boolean jit(Object value) {
        if (value == null) {
            return false;
        }
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException("jit must be true or false");
        }
        return flag;
    }

    private static OptionalLong expiresAt(Object value) {
        if (value == null) {
            return OptionalLong.empty();
        }
        if (!(value instanceof Number number) || number instanceof Double || number instanceof Float) {
            throw new IllegalArgumentException("expiresAt must be whole epoch seconds");
        }
        return OptionalLong.of(number.longValue());
    }

    private static Optional<String> email(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("email must be a string");
        }
        return Optional.of(text.toString());
    }
}
