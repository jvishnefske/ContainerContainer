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

import org.artifactory.api.security.SecurityService;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The {@code security} binding of plugin scripts.
 *
 * <p>Everywhere: read-only facts about the user on whose behalf the current code runs, as
 * {@code security.currentUsername}, {@code security.admin} and {@code security.anonymous}.
 *
 * <p>In realm and execution code only (the {@link PluginCallScope}; elsewhere they refuse, P4):
 * <ul>
 *     <li>{@link #findUser(String)}: an immutable view of a user, or empty;</li>
 *     <li>{@link #getRealmAuthentication()}: which plugin realm authenticated the current request, and until when,
 *         or empty when another provider did;</li>
 *     <li>{@link #createAccessToken(long)}: an Access token for the current non-anonymous user, scope
 *         {@code applied-permissions/user}, not refreshable, minted through Access with the user's own rights. Its
 *         lifetime is the smallest of the requested one, {@link #MAX_TOKEN_SECONDS}, the optional
 *         {@code notAfterEpochSeconds} argument and, when a plugin realm authenticated the request with an expiry,
 *         the time left until that expiry. The result is a {@link PluginAccessToken} value, never an exception.</li>
 * </ul>
 * In realm code no user is authenticated yet, so {@code createAccessToken} refuses there; it is meant for
 * executions such as a token exchange that a realm-authenticated caller runs.
 */
public final class PluginSecurity {

    /** Longest lifetime of a token minted by {@link #createAccessToken(long)}: one hour. */
    public static final long MAX_TOKEN_SECONDS = 3600;

    /** Scope of every token minted by {@link #createAccessToken(long)}. */
    public static final String TOKEN_SCOPE = "applied-permissions/user";

    private static final Logger log = LoggerFactory.getLogger(PluginSecurity.class);

    /** Mints an Access token; the production implementation calls Access as the current user. */
    @FunctionalInterface
    public interface TokenMinter {

        /** A minter that is not wired; every call is refused. */
        TokenMinter UNAVAILABLE = (username, seconds) -> {
            throw new IllegalStateException("token minting is not available");
        };

        /**
         * @return the minted token, its value and lifetime in seconds
         * @throws RuntimeException when Access refuses or fails
         */
        Minted mint(String username, long expiresInSeconds);
    }

    /** What Access returned for a minted token. */
    public record Minted(String token, long expiresIn) {
        public Minted {
            Objects.requireNonNull(token, "token");
        }
    }

    /** An immutable view of a user, as {@link #findUser(String)} returns it. */
    public record PluginUser(String username, String realm, boolean admin, Set<String> groups, Optional<String> email) {
        public PluginUser {
            Objects.requireNonNull(username, "username");
            groups = Set.copyOf(groups);
            Objects.requireNonNull(email, "email");
        }

        static PluginUser of(UserInfo user) {
            Set<String> groups = new TreeSet<>();
            if (user.getGroups() != null) {
                for (UserGroupInfo group : user.getGroups()) {
                    groups.add(group.getGroupName());
                }
            }
            return new PluginUser(user.getUsername(), user.getRealm(), user.isEffectiveAdmin(), groups,
                    Optional.ofNullable(user.getEmail()));
        }
    }

    /** Which plugin realm authenticated the current request; {@code expiresAt} in epoch seconds. */
    public record RealmAuthentication(String pluginName, String realm, String username, OptionalLong expiresAt) {
    }

    /**
     * The result of {@link #createAccessToken(long)}: either issued, with the token, its type ({@code Bearer}),
     * lifetime in seconds and scope, or refused with a reason. Refusal reasons never contain credentials.
     */
    public record PluginAccessToken(boolean issued, String accessToken, String tokenType, long expiresIn,
            String scope, String reason) {

        static PluginAccessToken issued(Minted minted) {
            return new PluginAccessToken(true, minted.token(), "Bearer", minted.expiresIn(), TOKEN_SCOPE, "");
        }

        static PluginAccessToken refused(String reason) {
            return new PluginAccessToken(false, "", "", 0, "", reason);
        }
    }

    private final SecurityService securityService;
    private final Function<String, Optional<UserInfo>> users;
    private final TokenMinter minter;
    private final Supplier<Authentication> authentication;
    private final LongSupplier nowSeconds;

    /** A binding with the read-only facts only; {@code findUser} finds nobody and tokens are refused. */
    public PluginSecurity(SecurityService securityService) {
        this(securityService, name -> Optional.empty(), TokenMinter.UNAVAILABLE, () -> null,
                () -> System.currentTimeMillis() / 1000);
    }

    /**
     * @param users          looks up a user by name
     * @param minter         mints Access tokens
     * @param authentication the current request's authentication
     * @param nowSeconds     the clock, in epoch seconds
     */
    public PluginSecurity(SecurityService securityService, Function<String, Optional<UserInfo>> users,
            TokenMinter minter, Supplier<Authentication> authentication, LongSupplier nowSeconds) {
        this.securityService = Objects.requireNonNull(securityService, "securityService");
        this.users = Objects.requireNonNull(users, "users");
        this.minter = Objects.requireNonNull(minter, "minter");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.nowSeconds = Objects.requireNonNull(nowSeconds, "nowSeconds");
    }

    /** @return the current user's name */
    public String getCurrentUsername() {
        return securityService.currentUsername();
    }

    /** @return whether the current user is an administrator */
    public boolean isAdmin() {
        return securityService.isAdmin();
    }

    /** @return whether the current user is anonymous */
    public boolean isAnonymous() {
        return securityService.isAnonymous();
    }

    /**
     * @return the user named {@code name}; empty when it does not exist, the name is null, or the caller is not
     *         realm or execution code
     */
    public Optional<PluginUser> findUser(String name) {
        if (name == null || PluginCallScope.current().isEmpty()) {
            return Optional.empty();
        }
        return users.apply(name).map(PluginUser::of);
    }

    /** @return the plugin realm that authenticated the current request, empty for any other authentication */
    public Optional<RealmAuthentication> getRealmAuthentication() {
        if (PluginCallScope.current().isEmpty()) {
            return Optional.empty();
        }
        if (authentication.get() instanceof PluginRealmAuthentication realm) {
            return Optional.of(new RealmAuthentication(realm.pluginName(), realm.realmName(), realm.getName(),
                    realm.expiresAt()));
        }
        return Optional.empty();
    }

    /** Mints a token for the current user; see the class documentation for the lifetime rule. */
    public PluginAccessToken createAccessToken(long expiresInSeconds) {
        return createAccessToken(expiresInSeconds, Long.MAX_VALUE);
    }

    /**
     * Mints a token for the current user that also expires no later than {@code notAfterEpochSeconds}.
     */
    public PluginAccessToken createAccessToken(long expiresInSeconds, long notAfterEpochSeconds) {
        if (PluginCallScope.current().filter(kind -> kind == PluginCallScope.Kind.EXECUTION).isEmpty()) {
            return PluginAccessToken.refused("createAccessToken is available to plugin executions only");
        }
        String username = securityService.currentUsername();
        if (username == null || securityService.isAnonymous() || UserInfo.isAnonymous(username)) {
            return PluginAccessToken.refused("the caller is not authenticated");
        }
        long now = nowSeconds.getAsLong();
        OptionalLong realmExpiry = authentication.get() instanceof PluginRealmAuthentication realm
                ? realm.expiresAt() : OptionalLong.empty();
        OptionalLong lifetime = tokenLifetime(expiresInSeconds, now, notAfterEpochSeconds, realmExpiry);
        if (lifetime.isEmpty()) {
            return PluginAccessToken.refused("no lifetime is left for the token");
        }
        try {
            Minted minted = minter.mint(username, lifetime.getAsLong());
            return PluginAccessToken.issued(minted);
        } catch (RuntimeException e) {
            log.debug("Minting a token for user '{}' failed: {}", username, e.getMessage(), e);
            return PluginAccessToken.refused("Access refused to create the token");
        }
    }

    /**
     * The lifetime rule of minted tokens. Pure.
     *
     * @param requested   the lifetime asked for, in seconds
     * @param now         the current time, epoch seconds
     * @param notAfter    the latest allowed expiry, epoch seconds
     * @param realmExpiry the expiry of the credential that authenticated the request, if any
     * @return the lifetime in seconds: the minimum of {@code requested}, {@link #MAX_TOKEN_SECONDS},
     *         {@code notAfter - now} and {@code realmExpiry - now}; empty when that is not positive
     */
    public static OptionalLong tokenLifetime(long requested, long now, long notAfter, OptionalLong realmExpiry) {
        long lifetime = Math.min(requested, MAX_TOKEN_SECONDS);
        if (notAfter != Long.MAX_VALUE) {
            lifetime = Math.min(lifetime, notAfter - now);
        }
        if (realmExpiry.isPresent()) {
            lifetime = Math.min(lifetime, realmExpiry.getAsLong() - now);
        }
        return lifetime > 0 ? OptionalLong.of(lifetime) : OptionalLong.empty();
    }
}
