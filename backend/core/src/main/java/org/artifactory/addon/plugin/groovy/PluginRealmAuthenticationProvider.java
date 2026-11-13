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

import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.InternalUsernamePasswordAuthenticationToken;
import org.artifactory.security.PluginRealmGateway;
import org.artifactory.security.RealmAwareAuthenticationProvider;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService;
import org.artifactory.security.props.auth.OauthManager;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The Spring shell of plugin realms: one provider for all realms of all loaded plugins (P2).
 *
 * <p>It plays three roles:
 * <ul>
 *     <li>As a {@link RealmAwareAuthenticationProvider} in the {@code authenticationManager} chain of
 *         {@code security.xml}, placed after LDAP and Crowd and before Access user/password authentication and the
 *         internal database, it offers ordinary user name and password authentications to the {@code authenticate}
 *         closures of exclusive realms, so a realm can claim credentials before stored passwords are checked.
 *         Internal (service to service) authentications and the anonymous login are not offered.</li>
 *     <li>As the {@link PluginRealmGateway} of {@code PasswordDecryptingManager}, it is offered Bearer tokens and
 *         Basic passwords that are JWTs not issued by Access ({@link CredentialRouting}), which would otherwise go
 *         straight to Access token verification and never reach the chain. Access tokens keep their path and are
 *         never shown to plugins.</li>
 *     <li>As {@link GroovyPluginsAddon.AdditiveRealms}, it applies additive realms after another provider
 *         authenticated a request.</li>
 * </ul>
 * With no realm loaded every role is a no-op that returns before touching user data (P6).
 *
 * <p>The engine and the user service are resolved lazily because this bean is created while the security beans,
 * which the plugin engine's own collaborators depend on, are still being wired.
 */
@Component("pluginRealmAuthenticationProvider")
public class PluginRealmAuthenticationProvider implements RealmAwareAuthenticationProvider, PluginRealmGateway,
        GroovyPluginsAddon.AdditiveRealms {

    /** {@link #getRealm()} of this provider; the realms of users it creates are {@code plugin:<realm name>}. */
    public static final String REALM = "plugin";

    private final PluginRealmAuthenticator authenticator;

    /** Spring constructor. */
    @Autowired
    public PluginRealmAuthenticationProvider(ObjectProvider<GroovyPluginsAddon> addon,
            ObjectProvider<UserGroupService> userGroupService) {
        this(new PluginRealmAuthenticator(
                () -> realmsOf(addon.getIfAvailable()),
                new LazyDirectory(userGroupService),
                HookFailureReporter.logging(),
                () -> System.currentTimeMillis() / 1000,
                PluginRealmAuthenticationProvider::currentAdminMapping));
    }

    /**
     * The external administrator mapping in effect now (IA7): the groups configured in
     * {@link ConstantValues#securityExternalAdminGroups}, effective only once {@link AdminBootstrapService} reports
     * the server claimed. With no groups configured the bootstrap state is not even looked up.
     */
    static ExternalAdminMapping currentAdminMapping() {
        Set<String> groups = ExternalAdminMapping.parseGroups(ConstantValues.securityExternalAdminGroups.getString());
        if (groups.isEmpty()) {
            return ExternalAdminMapping.DISABLED;
        }
        boolean claimed = AdminBootstrapService.lookup(ContextHelper.get())
                .map(AdminBootstrapService::isClaimed).orElse(false);
        return new ExternalAdminMapping(claimed, groups);
    }

    /** Wires the provider to an explicit authenticator; used by tests. */
    public PluginRealmAuthenticationProvider(PluginRealmAuthenticator authenticator) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!offeredToChain(authentication) || !authenticator.hasExclusiveRealms()) {
            return null;
        }
        Object credentials = authentication.getCredentials();
        return authenticator.basic(String.valueOf(authentication.getPrincipal()),
                credentials == null ? "" : credentials.toString(), authentication.getDetails()).orElse(null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication)
                && !InternalUsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public Optional<Authentication> claim(Authentication authentication) {
        if (authentication == null || authentication.getCredentials() == null) {
            return Optional.empty();
        }
        String credential = authentication.getCredentials().toString();
        boolean bearer = authentication instanceof PropsAuthenticationToken props
                && OauthManager.OAUTH_KEY.equals(props.getPropsKey());
        boolean basic = offeredToChain(authentication);
        if (!(bearer || basic) || !authenticator.hasExclusiveRealms()) {
            return Optional.empty();
        }
        CredentialRouting.Kind kind = CredentialRouting.classify(credential);
        return switch (CredentialRouting.route(true, bearer, basic, kind)) {
            case REALM_BEARER -> authenticator.bearer(credential, authentication.getDetails());
            case REALM_BASIC -> authenticator.basic(String.valueOf(authentication.getPrincipal()), credential,
                    authentication.getDetails());
            case STOCK -> Optional.empty();
        };
    }

    @Override
    public Authentication applyAdditive(Authentication authentication) {
        if (!authenticator.hasAdditiveRealms()) {
            return authentication;
        }
        return authenticator.additive(authentication);
    }

    @Override
    public String getRealm() {
        return REALM;
    }

    @Override
    public void addExternalGroups(String username, Set<UserGroupInfo> groups) {
        // Realm groups are applied at authentication time.
    }

    @Override
    public boolean userExists(String userName) {
        return authenticator.userExists(userName);
    }

    /**
     * Whether {@code authentication} is an ordinary user name and password authentication: not an internal one and
     * not the anonymous login {@code AccessFilter} performs for requests without credentials. Pure.
     */
    static boolean offeredToChain(Authentication authentication) {
        return authentication instanceof UsernamePasswordAuthenticationToken
                && !(authentication instanceof InternalUsernamePasswordAuthenticationToken)
                && authentication.getPrincipal() != null
                && !UserInfo.isAnonymous(String.valueOf(authentication.getPrincipal()));
    }

    private static List<PluginRealm> realmsOf(GroovyPluginsAddon addon) {
        return addon == null ? List.of() : addon.realms();
    }

    /** Resolves the user service on first use. */
    private static final class LazyDirectory implements RealmDirectory {

        private final ObjectProvider<UserGroupService> provider;

        LazyDirectory(ObjectProvider<UserGroupService> provider) {
            this.provider = provider;
        }

        private RealmDirectory delegate() {
            return RealmDirectory.over(provider.getObject());
        }

        @Override
        public Optional<UserInfo> findUser(String username) {
            return delegate().findUser(username);
        }

        @Override
        public Optional<GroupInfo> findGroup(String name) {
            return delegate().findGroup(name);
        }

        @Override
        public boolean createUser(String username, String userRealm, Set<String> groups, Optional<String> email) {
            return delegate().createUser(username, userRealm, groups, email);
        }

        @Override
        public void replaceGroups(UserInfo user, Set<String> groups) {
            delegate().replaceGroups(user, groups);
        }
    }
}
