/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.PasswordPolicy;
import org.artifactory.security.access.embedded.core.ScopeGrammar;
import org.artifactory.security.access.embedded.core.SigningKeys;
import org.artifactory.security.access.embedded.core.TokenClaimsFactory;
import org.artifactory.security.access.embedded.store.EaChangeBus;
import org.artifactory.security.access.embedded.store.EmbeddedAccessStore;
import org.artifactory.storage.db.security.service.access.BulkPasswordExpiry;
import org.jfrog.access.client.AccessAuth;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.auth.AuthClient;
import org.jfrog.access.client.auth.GrpcAuthClient;
import org.jfrog.access.client.auth.ldap.AuthSettingsClient;
import org.jfrog.access.client.cert.CertClient;
import org.jfrog.access.client.config.ConfigClient;
import org.jfrog.access.client.group.GroupsClient;
import org.jfrog.access.client.group.GrpcGroupsClient;
import org.jfrog.access.client.imports.ImportClient;
import org.jfrog.access.client.lock.LockClient;
import org.jfrog.access.client.notify.NotifyClient;
import org.jfrog.access.client.permission.GrpcPermissionsClient;
import org.jfrog.access.client.permission.GrpcPermissionsV2Client;
import org.jfrog.access.client.permission.PermissionsClient;
import org.jfrog.access.client.platformconfig.GrpcPlatformConfigClient;
import org.jfrog.access.client.platformconfig.LicenseClient;
import org.jfrog.access.client.project.GrpcProjectClient;
import org.jfrog.access.client.proxy.GrpcProxyClient;
import org.jfrog.access.client.proxy.ProxyClient;
import org.jfrog.access.client.registry.RegistryClient;
import org.jfrog.access.client.system.GrpcSystemClient;
import org.jfrog.access.client.system.SystemClient;
import org.jfrog.access.client.token.TokenClient;
import org.jfrog.access.client.topology.TopologyClient;
import org.jfrog.access.client.user.GrpcUsersClient;
import org.jfrog.access.client.user.UsersClient;
import org.jfrog.access.client.vaults.AccessVaultClient;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.router.RouterGrpcClient;
import org.jfrog.client.http.RestRequest;
import org.jfrog.client.http.RestResponse;
import org.jfrog.security.crypto.EncryptionWrapper;

import java.security.cert.Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The in-process replacement for JFrog Access: an {@link AccessClient} backed by the embedded access store.
 * <p>
 * Every sub-client this returns is built once, at construction, by {@link FailClosed#backedBy}: a proxy for the
 * Access interface that dispatches to an adapter ({@code EaTokenClient}, {@code EaUsersClient}, ...) and refuses
 * the methods the adapter does not implement with {@link EmbeddedAccessUnsupportedException}. Surfaces embedded
 * access does not offer at all ({@code cert}, {@code grpcSystem}, {@code grpcUsers}, {@code grpcGroups},
 * {@code grpcPermissionsV2}, {@code imports}, {@code topology}, {@code proxies}, {@code vaults}, {@code lock},
 * federation) are {@link FailClosed#of} proxies. {@code registry()}, {@code notifications()} and {@code router()}
 * are logged no-ops. "Cached" variants return the same object as their plain variant: nothing is remote.
 * <p>
 * Client-level calls: {@code ping}, {@code readiness} and {@code liveness} always succeed; {@code useAuth}
 * returns this client; {@code close} does nothing (the store and the bus belong to the caller);
 * {@code restCall}, {@code updateRootCertificate}, {@code updateServiceId}, {@code getAuth} and {@code lock} are
 * refused. The two REST-only operations Artifactory needs, expiring and revalidating every password, are offered
 * through {@link BulkPasswordExpiry} instead.
 * <p>
 * Build one with {@link #builder()}.
 */
public final class EmbeddedAccessClient implements AccessClient, BulkPasswordExpiry {

    /** What {@code system().getAccessServerName()} answers. */
    public static final String SERVER_NAME = "embedded-access";

    /** The version string embedded access identifies itself with. */
    public static final String VERSION = "embedded-access/1";

    /** What {@link #getAccessServerUrl()} answers: there is no server URL. */
    public static final String SERVER_URL = "embedded:";

    /** How long after an access token expires its refresh token may still be used, unless configured. */
    public static final Duration DEFAULT_REFRESH_WINDOW = Duration.ofDays(7);

    /**
     * Scope tokens Artifactory itself requests that the standard grammar does not know: the remember-me scope and
     * every {@code internal:<feature>:<action>} scope (checksum deploy, event stream, grid and federation
     * operations). The default grammar is {@link ScopeGrammar#standard()} extended with these.
     */
    public static final List<Pattern> ARTIFACTORY_EXTRA_SCOPES = List.of(
            Pattern.compile(Pattern.quote("authentication:remember-me")),
            Pattern.compile("internal:[A-Za-z0-9._/-]+:[a-z]+"));

    private final EaRuntime rt;
    private final TokenClient token;
    private final AuthClient auth;
    private final GrpcAuthClient grpcAuth;
    private final UsersClient users;
    private final GroupsClient groups;
    private final PermissionsClient permissions;
    private final GrpcPermissionsClient grpcPermissions;
    private final GrpcPlatformConfigClient platformConfigs;
    private final ConfigClient config;
    private final SystemClient system;
    private final LicenseClient license;
    private final GrpcProjectClient project;
    private final RegistryClient registry;
    private final NotifyClient notifications;
    private final RouterGrpcClient router;
    private final EaKvDocs docs;
    private final EaAuthSettingsClient.Listeners settingsListeners = new EaAuthSettingsClient.Listeners();
    private final AuthSettingsClient plainAuthSettings;

    private final CertClient cert = FailClosed.of(CertClient.class, "cert");
    private final GrpcSystemClient grpcSystem = FailClosed.of(GrpcSystemClient.class, "grpcSystem");
    private final GrpcUsersClient grpcUsers = FailClosed.of(GrpcUsersClient.class, "grpcUsers");
    private final GrpcGroupsClient grpcGroups = FailClosed.of(GrpcGroupsClient.class, "grpcGroups");
    private final GrpcPermissionsV2Client grpcPermissionsV2 =
            FailClosed.of(GrpcPermissionsV2Client.class, "grpcPermissionsV2");
    private final ImportClient imports = FailClosed.of(ImportClient.class, "imports");
    private final TopologyClient topology = FailClosed.of(TopologyClient.class, "topology");
    private final ProxyClient proxies = FailClosed.of(ProxyClient.class, "proxies");
    private final GrpcProxyClient grpcProxy = FailClosed.of(GrpcProxyClient.class, "grpcProxy");
    private final AccessVaultClient vaults = FailClosed.of(AccessVaultClient.class, "vaults");

    private EmbeddedAccessClient(EaRuntime rt, Duration refreshWindow, EncryptionWrapper encryption) {
        this.rt = rt;
        this.docs = new EaKvDocs(rt, null);
        EaTokenClient tokenAdapter = new EaTokenClient(rt, refreshWindow);
        EaAuthSettingsClient settingsAdapter = new EaAuthSettingsClient(docs.withEncryption(encryption),
                settingsListeners);
        EaConfigClient configAdapter = new EaConfigClient(docs, () -> realms(settingsAdapter),
                settingsAdapter::getLdapConfig);
        EaAuthenticator authenticator = new EaAuthenticator(rt, tokenAdapter,
                () -> configAdapter.getAnonymousAccessEnabled().isEnabled());
        EaPermissionsClient permissionsAdapter = new EaPermissionsClient(rt);
        this.token = FailClosed.backedBy(TokenClient.class, "token", tokenAdapter);
        this.auth = FailClosed.backedBy(AuthClient.class, "auth",
                new EaAuthClient(rt, authenticator, tokenAdapter));
        this.grpcAuth = FailClosed.backedBy(GrpcAuthClient.class, "grpcAuth", new EaGrpcAuthClient(authenticator));
        this.users = FailClosed.backedBy(UsersClient.class, "users",
                new EaUsersClient(rt, authenticator, configAdapter::getUserLockPolicy));
        this.groups = FailClosed.backedBy(GroupsClient.class, "groups", new EaGroupsClient(rt));
        this.permissions = FailClosed.backedBy(PermissionsClient.class, "permissions", permissionsAdapter);
        this.grpcPermissions = FailClosed.backedBy(GrpcPermissionsClient.class, "grpcPermissions",
                new EaGrpcPermissionsClient(rt, permissionsAdapter));
        this.platformConfigs = FailClosed.backedBy(GrpcPlatformConfigClient.class, "platformConfigs",
                new EaPlatformConfigClient(docs, encryption));
        this.config = FailClosed.backedBy(ConfigClient.class, "config", configAdapter);
        this.system = FailClosed.backedBy(SystemClient.class, "system", new EaSystemClient(rt));
        this.license = FailClosed.backedBy(LicenseClient.class, "license", new EaLicenseClient(docs));
        this.project = FailClosed.backedBy(GrpcProjectClient.class, "project", new EaProjectClient());
        this.registry = FailClosed.backedBy(RegistryClient.class, "registry", new EaLoggedNoOps.Registry());
        this.notifications = FailClosed.backedBy(NotifyClient.class, "notifications",
                new EaLoggedNoOps.Notifications());
        this.router = FailClosed.backedBy(RouterGrpcClient.class, "router", new EaLoggedNoOps.Router());
        this.plainAuthSettings = FailClosed.backedBy(AuthSettingsClient.class, "authSettings", settingsAdapter);
    }

    private static List<String> realms(EaAuthSettingsClient settings) {
        List<String> out = new java.util.ArrayList<>();
        out.add("internal");
        out.addAll(settings.enabledRealms());
        return out;
    }

    /**
     * Starts a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the Artifactory service id tokens are verified for.
     *
     * @return {@code jfrt@<id>}
     */
    public ServiceId artifactoryServiceId() {
        return rt.artifactoryServiceId();
    }

    /**
     * Returns the embedded Access service id.
     *
     * @return {@code jfac@<id>}
     */
    public ServiceId accessServiceId() {
        return rt.accessServiceId();
    }

    /**
     * Returns the certificate tokens are signed with, for the caller to publish as Access's root certificate.
     *
     * @return the certificate
     */
    public java.security.cert.X509Certificate certificate() {
        return rt.certificate();
    }

    // ---- AccessClientBase ----

    @Override
    public AccessClient useAuth(AccessAuth accessAuth) {
        return this;
    }

    @Override
    public AccessClient useAuth(AccessAuth accessAuth, Supplier<AccessAuth> supplier) {
        return this;
    }

    @Override
    public boolean hasAuth() {
        return true;
    }

    // ---- surfaces ----

    @Override
    public AccessAuth getAuth() {
        throw new EmbeddedAccessUnsupportedException("client.getAuth");
    }

    @Override
    public TokenClient token() {
        return token;
    }

    @Override
    public CertClient cert() {
        return cert;
    }

    @Override
    public SystemClient system() {
        return system;
    }

    @Override
    public GrpcSystemClient grpcSystem() {
        return grpcSystem;
    }

    @Override
    public AuthClient auth() {
        return auth;
    }

    @Override
    public AuthClient cachedAuth() {
        return auth;
    }

    @Override
    public GrpcAuthClient grpcAuth(AuthSettingsClient authSettingsClient) {
        return grpcAuth;
    }

    @Override
    public UsersClient users() {
        return users;
    }

    @Override
    public UsersClient cachedUsers() {
        return users;
    }

    @Override
    public GrpcUsersClient grpcUsers() {
        return grpcUsers;
    }

    @Override
    public GroupsClient groups() {
        return groups;
    }

    @Override
    public GrpcGroupsClient grpcGroups() {
        return grpcGroups;
    }

    @Override
    public PermissionsClient permissions() {
        return permissions;
    }

    @Override
    public NotifyClient notifications() {
        return notifications;
    }

    @Override
    public GrpcPermissionsClient grpcPermissions() {
        return grpcPermissions;
    }

    @Override
    public GrpcPermissionsV2Client grpcPermissionsV2() {
        return grpcPermissionsV2;
    }

    @Override
    public GrpcPlatformConfigClient cachedGrpcPlatformConfigs() {
        return platformConfigs;
    }

    @Override
    public GrpcPlatformConfigClient cachedGrpcPlatformConfigs(Set<String> prefetchKeys) {
        return platformConfigs;
    }

    @Override
    public GrpcPlatformConfigClient grpcPlatformConfigs() {
        return platformConfigs;
    }

    @Override
    public LicenseClient cachedLicense() {
        return license;
    }

    /**
     * Returns the auth-settings surface. Documents are encrypted with {@code wrapper}, or, when it is
     * {@code null}, with the client's configured encryption wrapper, if any.
     *
     * @param wrapper the encryption wrapper, or {@code null}
     * @return the surface
     */
    @Override
    public AuthSettingsClient cachedAuthSettingClient(EncryptionWrapper wrapper) {
        if (wrapper == null) {
            return plainAuthSettings;
        }
        return FailClosed.backedBy(AuthSettingsClient.class, "authSettings",
                new EaAuthSettingsClient(docs.withEncryption(wrapper), settingsListeners));
    }

    @Override
    public GrpcProjectClient cachedGrpcProject() {
        return project;
    }

    @Override
    public GrpcProjectClient cachedGrpcProject(boolean prefetch) {
        return project;
    }

    @Override
    public GrpcProjectClient grpcProject() {
        return project;
    }

    @Override
    public RegistryClient registry() {
        return registry;
    }

    @Override
    public ImportClient imports() {
        return imports;
    }

    @Override
    public ConfigClient config() {
        return config;
    }

    @Override
    public RouterGrpcClient router() {
        return router;
    }

    @Override
    public TopologyClient topology() {
        return topology;
    }

    @Override
    public ProxyClient proxies() {
        return proxies;
    }

    @Override
    public GrpcProxyClient cachedGrpcProxy(EncryptionWrapper wrapper, String key) {
        return grpcProxy;
    }

    @Override
    public AccessVaultClient vaults() {
        return vaults;
    }

    // ---- client-level calls ----

    /** Always healthy: embedded access runs in process. */
    @Override
    public void ping() {
        // in process: nothing to reach
    }

    /** Always ready. */
    @Override
    public void readiness() {
        // in process: nothing to reach
    }

    /** Always live. */
    @Override
    public void liveness() {
        // in process: nothing to reach
    }

    @Override
    public void updateRootCertificate(Certificate certificate) {
        throw new EmbeddedAccessUnsupportedException("client.updateRootCertificate");
    }

    @Override
    public void updateServiceId(ServiceId serviceId) {
        throw new EmbeddedAccessUnsupportedException("client.updateServiceId");
    }

    @Override
    public RestResponse restCall(RestRequest request) {
        throw new EmbeddedAccessUnsupportedException("client.restCall");
    }

    @Override
    public String getAccessServerUrl() {
        return SERVER_URL;
    }

    @Override
    public boolean isServerBypassed() {
        return false;
    }

    @Override
    public boolean shouldSkipNodeRegistration() {
        return true;
    }

    @Override
    public Optional<String> getTenantID() {
        return Optional.empty();
    }

    @Override
    public LockClient lock(String name, String owner, Duration timeout) {
        throw new EmbeddedAccessUnsupportedException("client.lock");
    }

    /**
     * Sets or clears the password-expired flag of every user in one store transaction. Expiring touches only users
     * with a password (the passwordless {@code admin} before the claim, {@code anonymous} and external users have
     * none to expire); revalidating clears the flag wherever it is set.
     *
     * @param expired {@code true} to expire, {@code false} to revalidate
     * @return the number of users changed
     * @throws org.jfrog.access.client.AccessClientException if the store refused
     */
    @Override
    public int setPasswordExpiredForAllUsers(boolean expired) {
        Instant now = rt.now();
        return EaErrors.orThrow(rt.store().updateAllUsers(user -> {
            boolean flagged = Boolean.parseBoolean(user.customData().get(EaModels.PASSWORD_EXPIRED));
            if (expired == flagged || (expired && user.passwordHash() == null)) {
                return user;
            }
            Map<String, String> custom = new TreeMap<>(user.customData());
            if (expired) {
                custom.put(EaModels.PASSWORD_EXPIRED, "true");
            } else {
                custom.remove(EaModels.PASSWORD_EXPIRED);
            }
            return user.toBuilder().customData(custom).modified(now).build();
        }));
    }

    /** Does nothing: the store and the change bus belong to the caller. */
    @Override
    public void close() {
        // nothing owned
    }

    @Override
    public String toString() {
        return "EmbeddedAccessClient[" + rt.accessServiceId().getFormattedName() + "]";
    }

    /**
     * Collects what an {@link EmbeddedAccessClient} needs. Required: {@link #store}, {@link #changeBus},
     * {@link #serviceId}, {@link #signingKeys} and {@link #passwordVerifier}. Optional: {@link #clock} (system UTC),
     * {@link #scopeGrammar} (standard plus {@link #ARTIFACTORY_EXTRA_SCOPES}), {@link #passwordPolicy} (the
     * bundled list), {@link #encryption} (none) and {@link #refreshWindow} ({@link #DEFAULT_REFRESH_WINDOW}).
     */
    public static final class Builder {
        private EmbeddedAccessStore store;
        private EaChangeBus bus;
        private ServiceId serviceId;
        private SigningKeys signingKeys;
        private EaPasswordVerifier passwords;
        private Clock clock = Clock.systemUTC();
        private ScopeGrammar grammar = ScopeGrammar.standard().withExtraPatterns(ARTIFACTORY_EXTRA_SCOPES);
        private PasswordPolicy passwordPolicy;
        private EncryptionWrapper encryption;
        private Duration refreshWindow = DEFAULT_REFRESH_WINDOW;

        private Builder() {
        }

        /**
         * Sets the store.
         *
         * @param value the store
         * @return this builder
         */
        public Builder store(EmbeddedAccessStore value) {
            this.store = value;
            return this;
        }

        /**
         * Sets the change bus the store publishes to.
         *
         * @param value the bus
         * @return this builder
         */
        public Builder changeBus(EaChangeBus value) {
            this.bus = value;
            return this;
        }

        /**
         * Sets Artifactory's service id, {@code jfrt@<id>}; the Access service id is {@code jfac@<id>}.
         *
         * @param value the Artifactory service id
         * @return this builder
         */
        public Builder serviceId(ServiceId value) {
            this.serviceId = value;
            return this;
        }

        /**
         * Sets the cleartext signing keys (decrypted by the caller).
         *
         * @param value the keys
         * @return this builder
         */
        public Builder signingKeys(SigningKeys value) {
            this.signingKeys = value;
            return this;
        }

        /**
         * Sets the password verifier.
         *
         * @param value the verifier
         * @return this builder
         */
        public Builder passwordVerifier(EaPasswordVerifier value) {
            this.passwords = value;
            return this;
        }

        /**
         * Sets the clock.
         *
         * @param value the clock
         * @return this builder
         */
        public Builder clock(Clock value) {
            this.clock = value;
            return this;
        }

        /**
         * Sets the scope grammar tokens are validated with.
         *
         * @param value the grammar
         * @return this builder
         */
        public Builder scopeGrammar(ScopeGrammar value) {
            this.grammar = value;
            return this;
        }

        /**
         * Sets the password policy {@code auth().verifyPassword} applies.
         *
         * @param value the policy
         * @return this builder
         */
        public Builder passwordPolicy(PasswordPolicy value) {
            this.passwordPolicy = value;
            return this;
        }

        /**
         * Sets the wrapper that encrypts secret platform configuration and, when the caller passes none, auth
         * settings.
         *
         * @param value the wrapper, or {@code null}
         * @return this builder
         */
        public Builder encryption(EncryptionWrapper value) {
            this.encryption = value;
            return this;
        }

        /**
         * Sets how long after expiry a token may still be refreshed.
         *
         * @param value the window, not negative
         * @return this builder
         */
        public Builder refreshWindow(Duration value) {
            this.refreshWindow = value;
            return this;
        }

        /**
         * Builds the client.
         *
         * @return the client, or {@code Refused} naming what is missing or invalid
         */
        public EaOutcome<EmbeddedAccessClient> build() {
            if (store == null || bus == null || serviceId == null || signingKeys == null || passwords == null
                    || clock == null || grammar == null) {
                return EaOutcome.refused("store, changeBus, serviceId, signingKeys, passwordVerifier, clock and "
                        + "scopeGrammar are required");
            }
            if (refreshWindow == null || refreshWindow.isNegative()) {
                return EaOutcome.refused("refreshWindow must not be negative");
            }
            if (!TokenClaimsFactory.ARTIFACTORY_SERVICE_TYPE.equals(serviceId.getServiceType())) {
                return EaOutcome.refused("serviceId is not an Artifactory service id: " + serviceId);
            }
            EaOutcome<PasswordPolicy> policy = passwordPolicy != null ? EaOutcome.ok(passwordPolicy)
                    : PasswordPolicy.withBundledList();
            return policy.flatMap(p -> EaKeyMaterial.parse(signingKeys).flatMap(keys ->
                    TokenClaimsFactory.accessServiceId(serviceId.getInstanceId()).map(accessId ->
                            new EmbeddedAccessClient(new EaRuntime(store, bus, clock, serviceId,
                                    ServiceId.fromFormattedName(accessId), passwords, p, grammar, keys.privateKey(),
                                    keys.certificate()), refreshWindow, encryption))));
        }
    }
}
