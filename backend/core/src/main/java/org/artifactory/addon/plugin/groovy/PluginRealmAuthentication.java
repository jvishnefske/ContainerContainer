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

import org.artifactory.security.ExpiringAuthentication;
import org.artifactory.security.SimpleUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;

/**
 * A successful authentication by a plugin realm. The principal is a {@link SimpleUser} whose descriptor carries the
 * granted groups; it is never an administrator, because {@link RealmGrantPolicy} refuses admin users and admin
 * groups. It keeps which plugin and realm authenticated the request and when the credential expires, so execution
 * code can tell a realm-authenticated caller from others ({@code security.realmAuthentication}).
 *
 * <p>{@code AccessFilter} stops reusing it from its caches once it expires or is {@link #revoked()}: it is revoked
 * as soon as the realm that granted it is no longer the loaded one (the plugin was reloaded or removed), so removing
 * a realm takes effect immediately rather than after the cache idle time. The check is held in a transient field; an
 * authentication restored from a serialized session has none and counts as revoked.
 */
public final class PluginRealmAuthentication extends AbstractAuthenticationToken implements ExpiringAuthentication {

    private final SimpleUser principal;
    private final String pluginName;
    private final String realmName;
    private final OptionalLong expiresAt;
    private final transient BooleanSupplier stillGranted;

    /**
     * An authentication that is never revoked; for authentications built outside the realm provider, such as tests.
     */
    public PluginRealmAuthentication(SimpleUser principal, String pluginName, String realmName, OptionalLong expiresAt,
            Object details) {
        this(principal, pluginName, realmName, expiresAt, details, () -> true);
    }

    /**
     * @param expiresAt    epoch seconds after which the authentication is not reused, empty when the realm gave none
     * @param stillGranted answers whether the realm that granted it is still loaded
     */
    public PluginRealmAuthentication(SimpleUser principal, String pluginName, String realmName, OptionalLong expiresAt,
            Object details, BooleanSupplier stillGranted) {
        super(SimpleUser.USER_GAS);
        this.principal = Objects.requireNonNull(principal, "principal");
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.realmName = Objects.requireNonNull(realmName, "realmName");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.stillGranted = Objects.requireNonNull(stillGranted, "stillGranted");
        setDetails(details);
        setAuthenticated(true);
    }

    /** @return an empty string: the credential is never kept */
    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public SimpleUser getPrincipal() {
        return principal;
    }

    /** @return name of the plugin that declared the realm */
    public String pluginName() {
        return pluginName;
    }

    /** @return name of the realm that authenticated the request */
    public String realmName() {
        return realmName;
    }

    @Override
    public OptionalLong expiresAt() {
        return expiresAt;
    }

    @Override
    public boolean revoked() {
        return stillGranted == null || !stillGranted.getAsBoolean();
    }

    /** @return a copy with another principal, keeping realm, expiry and revocation */
    PluginRealmAuthentication withPrincipal(SimpleUser other) {
        return new PluginRealmAuthentication(other, pluginName, realmName, expiresAt, getDetails(),
                stillGranted == null ? () -> false : stillGranted);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PluginRealmAuthentication that && super.equals(that)
                && pluginName.equals(that.pluginName) && realmName.equals(that.realmName)
                && expiresAt.equals(that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), pluginName, realmName, expiresAt);
    }
}
