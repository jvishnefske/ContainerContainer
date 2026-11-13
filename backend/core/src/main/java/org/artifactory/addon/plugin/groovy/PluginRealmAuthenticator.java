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

import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.security.AnonymousAuthenticationToken;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Authenticates credentials against the loaded plugin realms and carries out what {@link RealmGrantPolicy} decides.
 *
 * <p>Exclusive realms are asked in load order (plugin file name, then declaration order). For each realm that
 * declares the relevant hook ({@code authenticate} for user name and password, {@code bearer} for a token), whether
 * a Groovy closure or a hosted WASM call ({@link RealmHook}), the answer is classified by {@link RealmInvoker}:
 * <ul>
 *     <li>not mine: the next realm is asked; when no realm claims the credentials the result is empty and the
 *         caller continues with the next provider or the stock path;</li>
 *     <li>rejected: {@link PluginRealmRejectedException}, which ends authentication with 401;</li>
 *     <li>authenticated: the grant policy decides; a denial is also a {@link PluginRealmRejectedException}, never a
 *         fall-through, so a realm that claimed the credentials cannot leak them to later providers.</li>
 * </ul>
 * A granted authentication is a {@link PluginRealmAuthentication}. Just-in-time users are created through the
 * {@link RealmDirectory}; a creation that loses a race with a concurrent one re-reads the user and continues only if
 * it belongs to the realm. After the policy granted a result, {@link ExternalAdminMapping} may make the principal an
 * administrator for that authentication only, when the server is claimed and one of the realm's groups is listed
 * in {@code artifactory.security.externalAdminGroups} (IA7); the policy itself never grants admin.
 *
 * <p>Additive realms ({@link #additive}) are asked after another provider authenticated a request and may only add
 * groups, under the same group rules, to authentications whose type is known to carry no token scope.
 *
 * <p>Nothing is cached here: the request-level caching of {@code AccessFilter} applies as for every other
 * provider, bounded by the realm's {@code expiresAt} and revoked as soon as the realm is no longer loaded (see
 * {@link PluginRealmAuthentication#revoked()}); failures are never cached.
 */
public final class PluginRealmAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(PluginRealmAuthenticator.class);

    private final Supplier<List<PluginRealm>> realms;
    private final RealmDirectory directory;
    private final HookFailureReporter reporter;
    private final LongSupplier nowSeconds;
    private final Supplier<ExternalAdminMapping> adminMapping;

    /**
     * Creates an authenticator without external administrator groups ({@link ExternalAdminMapping#DISABLED}).
     *
     * @param realms     supplies the realms of the current plugin generation, in load order
     * @param directory  the user and group store
     * @param reporter   receives realm closures that failed or returned something invalid
     * @param nowSeconds the clock, in epoch seconds
     */
    public PluginRealmAuthenticator(Supplier<List<PluginRealm>> realms, RealmDirectory directory,
            HookFailureReporter reporter, LongSupplier nowSeconds) {
        this(realms, directory, reporter, nowSeconds, () -> ExternalAdminMapping.DISABLED);
    }

    /**
     * @param realms       supplies the realms of the current plugin generation, in load order
     * @param directory    the user and group store
     * @param reporter     receives realm closures that failed or returned something invalid
     * @param nowSeconds   the clock, in epoch seconds
     * @param adminMapping supplies the current external administrator mapping (IA7), read on each granted
     *                     exclusive-realm authentication
     */
    public PluginRealmAuthenticator(Supplier<List<PluginRealm>> realms, RealmDirectory directory,
            HookFailureReporter reporter, LongSupplier nowSeconds, Supplier<ExternalAdminMapping> adminMapping) {
        this.realms = Objects.requireNonNull(realms, "realms");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.nowSeconds = Objects.requireNonNull(nowSeconds, "nowSeconds");
        this.adminMapping = Objects.requireNonNull(adminMapping, "adminMapping");
    }

    /** @return whether any exclusive realm is loaded */
    public boolean hasExclusiveRealms() {
        return realms.get().stream().anyMatch(realm -> realm.policy() == PluginRealm.Policy.EXCLUSIVE);
    }

    /** @return whether any additive realm is loaded */
    public boolean hasAdditiveRealms() {
        return realms.get().stream().anyMatch(realm -> realm.policy() == PluginRealm.Policy.ADDITIVE);
    }

    /**
     * Offers a user name and password to the {@code authenticate} closures of exclusive realms.
     *
     * @return the granted authentication, or empty when no realm claimed the credentials
     * @throws PluginRealmRejectedException when a realm rejected them or its grant was denied
     */
    public Optional<Authentication> basic(String username, String password, Object details) {
        return firstClaim(PluginRealm::authenticate, "authenticate", details, username, password);
    }

    /**
     * Offers a bearer token to the {@code bearer} closures of exclusive realms.
     *
     * @return the granted authentication, or empty when no realm claimed the token
     * @throws PluginRealmRejectedException when a realm rejected it or its grant was denied
     */
    public Optional<Authentication> bearer(String token, Object details) {
        return firstClaim(PluginRealm::bearer, "bearer", details, token);
    }

    /** @return whether some realm's {@code userExists} closure answered true for {@code username} */
    public boolean userExists(String username) {
        for (PluginRealm realm : realms.get()) {
            if (realm.userExists().isPresent()
                    && RealmInvoker.userExists(realm, realm.userExists().get(), reporter, username)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lets additive realms add groups to an authentication another provider made. Applies only to non-anonymous
     * authentications whose principal is a {@link SimpleUser} and whose type carries no token scope
     * ({@link #additiveApplies}); everything else is returned unchanged. Each additive realm's {@code authenticate}
     * closure is called with the user name and {@code null} credentials; an authenticated answer for the same
     * user adds its allowed groups, any other answer adds nothing. Additive realms can neither authenticate nor
     * refuse anyone.
     *
     * @return {@code authentication} itself when nothing was added, otherwise a copy with the extra groups
     */
    public Authentication additive(Authentication authentication) {
        if (!additiveApplies(authentication)) {
            return authentication;
        }
        SimpleUser principal = (SimpleUser) authentication.getPrincipal();
        String username = principal.getUsername();
        Set<String> extra = new TreeSet<>();
        for (PluginRealm realm : realms.get()) {
            if (realm.policy() != PluginRealm.Policy.ADDITIVE || realm.authenticate().isEmpty()) {
                continue;
            }
            RealmResult result = RealmInvoker.result(realm, realm.authenticate().get(),
                    "realms." + realm.name() + ".authenticate", reporter, username, null);
            if (result instanceof RealmResult.Authenticated granted) {
                if (granted.username().equals(username)) {
                    extra.addAll(RealmGrantPolicy.allowedGroups(granted.groups(), this::groupFacts));
                } else {
                    log.debug("Additive realm '{}' answered for user '{}' instead of '{}'; ignored", realm.name(),
                            granted.username(), username);
                }
            }
        }
        extra.removeAll(groupNames(principal.getDescriptor()));
        if (extra.isEmpty()) {
            return authentication;
        }
        MutableUserInfo copy = InfoFactoryHolder.get().copyUser(principal.getDescriptor());
        extra.forEach(copy::addGroup);
        SimpleUser withGroups = new SimpleUser(copy);
        if (authentication instanceof PluginRealmAuthentication realmAuth) {
            return realmAuth.withPrincipal(withGroups);
        }
        UsernamePasswordAuthenticationToken result = new UsernamePasswordAuthenticationToken(withGroups, "",
                withGroups.getAuthorities());
        result.setDetails(authentication.getDetails());
        return result;
    }

    /**
     * Whether additive realms may add groups to {@code authentication}. Pure. Only types whose authority comes
     * entirely from the user's groups qualify: user name and password authentications (Spring's own token, Access
     * password logins) and plugin realm authentications. Token authentications such as Access tokens are excluded,
     * because their scope may restrict the user and rebuilding them would drop that restriction. The authentication
     * must also be authenticated, not anonymous, and not an administrator (admins need no groups, and a copy must
     * not carry admin rights into a new object).
     */
    public static boolean additiveApplies(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof SimpleUser principal)
                || principal.isAnonymous() || principal.isEffectiveAdmin()) {
            return false;
        }
        Class<?> type = authentication.getClass();
        return type == UsernamePasswordAuthenticationToken.class || type == PluginRealmAuthentication.class
                || "org.artifactory.security.access.AccessResponseAuthentication".equals(type.getName());
    }

    private Optional<Authentication> firstClaim(Function<PluginRealm, Optional<RealmHook>> pick, String closureName,
            Object details, Object... args) {
        for (PluginRealm realm : realms.get()) {
            Optional<RealmHook> hook = pick.apply(realm);
            if (realm.policy() != PluginRealm.Policy.EXCLUSIVE || hook.isEmpty()) {
                continue;
            }
            RealmResult result = RealmInvoker.result(realm, hook.get(),
                    "realms." + realm.name() + "." + closureName, reporter, args);
            switch (result) {
                case RealmResult.NotMine notMine -> {
                }
                case RealmResult.Rejected rejected -> throw rejected(realm, rejected.reason());
                case RealmResult.Authenticated authenticated -> {
                    return Optional.of(grant(realm, authenticated, details));
                }
            }
        }
        return Optional.empty();
    }

    private Authentication grant(PluginRealm realm, RealmResult.Authenticated result, Object details) {
        Optional<UserInfo> user = directory.findUser(result.username());
        RealmGrantPolicy.Grant grant = RealmGrantPolicy.decide(realm, result,
                user.map(RealmGrantPolicy.ExistingUser::of), this::groupFacts, nowSeconds.getAsLong());
        UserInfo principal = switch (grant) {
            case RealmGrantPolicy.Deny deny -> throw rejected(realm, deny.reason());
            case RealmGrantPolicy.UseExisting existing -> withGroups(user.orElseThrow(), existing.groups(), false);
            case RealmGrantPolicy.SyncRealmUser sync -> {
                if (sync.groupsChanged()) {
                    directory.replaceGroups(user.orElseThrow(), sync.groups());
                }
                yield withGroups(user.orElseThrow(), sync.groups(), true);
            }
            case RealmGrantPolicy.CreateRealmUser create -> created(realm, create);
        };
        if (principal.isEffectiveAdmin() || principal.isAdmin()) {
            // Defence in depth: the policy already refuses admins and admin groups.
            throw rejected(realm, "the granted user would be an administrator");
        }
        UserInfo granted = mapExternalAdmin(realm, result, principal);
        return new PluginRealmAuthentication(new SimpleUser(granted), realm.pluginName(), realm.name(),
                result.expiresAt(), details, () -> stillLoaded(realm));
    }

    /**
     * Applies {@link ExternalAdminMapping} after {@link RealmGrantPolicy} (IA7): when the server is claimed and one
     * of the realm's raw groups is a configured external administrator group, the principal is an administrator
     * for this authentication only. Nothing is stored on the user.
     */
    private UserInfo mapExternalAdmin(PluginRealm realm, RealmResult.Authenticated result, UserInfo principal) {
        if (!adminMapping.get().grantsAdmin(result.groups())) {
            return principal;
        }
        MutableUserInfo copy = InfoFactoryHolder.get().copyUser(principal);
        copy.setAdmin(true);
        log.info("Plugin realm '{}' authenticated '{}' as an administrator through {}", realm.name(),
                principal.getUsername(), "artifactory.security.externalAdminGroups");
        return copy;
    }

    /**
     * @return whether {@code realm} is still part of the current plugin generation; a reload builds new realm
     *         values (with new closures), so a realm of an earlier generation never is
     */
    private boolean stillLoaded(PluginRealm realm) {
        for (PluginRealm current : realms.get()) {
            if (current == realm) {
                return true;
            }
        }
        return false;
    }

    private UserInfo created(PluginRealm realm, RealmGrantPolicy.CreateRealmUser create) {
        boolean created = directory.createUser(create.username(), create.userRealm(), create.groups(),
                create.email());
        Optional<UserInfo> stored = directory.findUser(create.username());
        if (stored.isEmpty() || !create.userRealm().equals(stored.get().getRealm())) {
            throw rejected(realm, created ? "the created user cannot be read back"
                    : "user '" + create.username() + "' could not be created");
        }
        if (!created && !groupNames(stored.get()).equals(create.groups())) {
            directory.replaceGroups(stored.get(), create.groups());
        }
        log.info("Plugin realm '{}' created user '{}' in realm '{}'", realm.name(), create.username(),
                create.userRealm());
        return withGroups(stored.get(), create.groups(), true);
    }

    /**
     * @param exact true to make {@code groups} the user's only groups, false to add them to the stored ones
     */
    private static UserInfo withGroups(UserInfo user, Set<String> groups, boolean exact) {
        MutableUserInfo copy = InfoFactoryHolder.get().copyUser(user);
        if (exact) {
            copy.setInternalGroups(Set.copyOf(groups));
        } else {
            groups.forEach(copy::addGroup);
        }
        copy.setAdmin(false);
        return copy;
    }

    private Optional<RealmGrantPolicy.GroupFacts> groupFacts(String name) {
        return directory.findGroup(name)
                .map(group -> new RealmGrantPolicy.GroupFacts(group.getGroupName(), group.isAdminPrivileges()));
    }

    private static Set<String> groupNames(UserInfo user) {
        return RealmGrantPolicy.ExistingUser.of(user).storedGroups();
    }

    private static PluginRealmRejectedException rejected(PluginRealm realm, String reason) {
        log.debug("Plugin realm '{}' of plugin '{}' refused the credentials: {}", realm.name(), realm.pluginName(),
                reason);
        return new PluginRealmRejectedException();
    }
}
