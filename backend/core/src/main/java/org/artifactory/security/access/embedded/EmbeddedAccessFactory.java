/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService;
import org.artifactory.security.access.embedded.bootstrap.BootstrapStatePort;
import org.artifactory.security.access.embedded.bootstrap.PasswordHasher;
import org.artifactory.security.access.embedded.client.EaPasswordVerifier;
import org.artifactory.security.access.embedded.client.EmbeddedAccessClient;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.EaOutcome.Done;
import org.artifactory.security.access.embedded.core.EaUser;
import org.artifactory.security.access.embedded.core.TokenClaimsFactory;
import org.artifactory.security.access.embedded.store.EaChangeBus;
import org.artifactory.security.access.embedded.store.EmbeddedAccessSchema;
import org.artifactory.security.access.embedded.store.EmbeddedAccessStore;
import org.artifactory.security.access.embedded.store.JdbcEmbeddedAccessStore;
import org.jfrog.access.common.ServiceId;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.storage.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

/**
 * Builds the embedded Access instance that replaces the external JFrog Access server
 * ({@code artifactory.access.embedded.enabled}, on by default). Called once per application context by
 * {@code AccessServiceImpl.bootstrapAccessClient()}.
 * <p>
 * {@link #create} runs these steps and stops at the first refusal, which the caller must turn into a startup
 * failure (the reason is meant for the operator):
 * <ol>
 *     <li>{@link EmbeddedAccessSchema#ensure}: creates the {@code ea_*} tables on Derby or PostgreSQL and seeds the
 *     passwordless {@code admin}; any other database type is refused.</li>
 *     <li>Creates the {@link EaChangeBus} and the {@link JdbcEmbeddedAccessStore} over Artifactory's data
 *     source.</li>
 *     <li>Seeds the user {@value #ANONYMOUS_USERNAME} if absent, as stock Access has one: internal, enabled, not
 *     an administrator and without a password, so it can never authenticate by password. The embedded client
 *     lets it log in only while anonymous access is enabled.</li>
 *     <li>{@link SigningKeyVault#loadOrCreate}: the token-signing key, encrypted with the master key in
 *     {@code ea_kv["signing.key"]}.</li>
 *     <li>Builds the {@link EmbeddedAccessClient} with a {@link PasswordHasher} as its password verifier and the
 *     master encryption wrapper for secret settings.</li>
 *     <li>Builds the {@link AdminBootstrapService} over a {@link StoreBootstrapStatePort}
 *     ({@code ea_kv["bootstrap.state"]}) and calls {@link AdminBootstrapService#start}. A refused start is logged
 *     and does not fail: the server then stays closed by the initial-setup filter until the operator fixes the
 *     cause and restarts.</li>
 *     <li>Starts the {@link EaTokenPurgeJob}.</li>
 * </ol>
 * On a refusal after the bus exists, the bus is closed again. Publishing the bootstrap service in the
 * application context and storing the root certificate are the caller's job.
 */
public final class EmbeddedAccessFactory {

    /** The username of the seeded anonymous user, as Artifactory's {@code UserInfo.ANONYMOUS}. */
    public static final String ANONYMOUS_USERNAME = "anonymous";

    private static final Logger log = LoggerFactory.getLogger(EmbeddedAccessFactory.class);

    private EmbeddedAccessFactory() {
    }

    /**
     * What {@link #create} needs.
     *
     * @param dataSource           Artifactory's data source
     * @param dbType               the configured database type
     * @param artifactoryServiceId Artifactory's service id, {@code jfrt@<id>}
     * @param encryption           the master encryption wrapper
     * @param clock                the clock
     * @param purgeInterval        the time between expired-token purges
     * @param bootstrapFactory     builds the (not yet started) bootstrap service over the state port
     */
    public record Settings(DataSource dataSource, DbType dbType, ServiceId artifactoryServiceId,
            EncryptionWrapper encryption, Clock clock, Duration purgeInterval,
            Function<BootstrapStatePort, AdminBootstrapService> bootstrapFactory) {

        /** Checks that nothing is missing except the encryption wrapper, which {@link #create} checks. */
        public Settings {
            Objects.requireNonNull(dataSource, "dataSource");
            Objects.requireNonNull(dbType, "dbType");
            Objects.requireNonNull(artifactoryServiceId, "artifactoryServiceId");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(purgeInterval, "purgeInterval");
            Objects.requireNonNull(bootstrapFactory, "bootstrapFactory");
        }

        /**
         * The settings of a running Artifactory: the home's master encryption wrapper, the system UTC clock, the
         * purge interval {@code artifactory.access.embedded.tokenPurgeIntervalMinutes} (60 if not positive) and
         * {@link AdminBootstrapService#forHome}.
         *
         * @param home                 the Artifactory home
         * @param dataSource           Artifactory's data source
         * @param dbType               the configured database type
         * @param artifactoryServiceId Artifactory's service id
         * @return the settings
         */
        public static Settings forHome(ArtifactoryHome home, DataSource dataSource, DbType dbType,
                ServiceId artifactoryServiceId) {
            long minutes = ConstantValues.accessEmbeddedTokenPurgeIntervalMinutes.getLong(home);
            Duration interval = Duration.ofMinutes(minutes > 0 ? minutes : 60);
            return new Settings(dataSource, dbType, artifactoryServiceId, home.getMasterEncryptionWrapper(),
                    Clock.systemUTC(), interval, port -> AdminBootstrapService.forHome(home, port));
        }
    }

    /**
     * Builds and starts embedded Access, see the class description.
     *
     * @param settings what to build it from
     * @return the running instance, or {@code Refused} with the reason startup must stop
     */
    public static EaOutcome<EmbeddedAccess> create(Settings settings) {
        EaOutcome<Done> schema = EmbeddedAccessSchema.ensure(settings.dataSource(), settings.dbType(),
                settings.clock());
        if (!schema.isOk()) {
            return schema.flatMap(ignored -> EaOutcome.refused("unreachable"));
        }
        EaChangeBus bus = new EaChangeBus();
        EaOutcome<EmbeddedAccess> built;
        try {
            built = build(settings, bus);
        } catch (RuntimeException e) {
            bus.close();
            throw e;
        }
        if (!built.isOk()) {
            bus.close();
        }
        return built;
    }

    private static EaOutcome<EmbeddedAccess> build(Settings settings, EaChangeBus bus) {
        Clock clock = settings.clock();
        JdbcEmbeddedAccessStore store = new JdbcEmbeddedAccessStore(settings.dataSource(), clock, bus);
        EaOutcome<Done> anonymous = ensureAnonymousUser(store, clock);
        if (!anonymous.isOk()) {
            return anonymous.flatMap(ignored -> EaOutcome.refused("unreachable"));
        }
        PasswordHasher hasher = PasswordHasher.standard();
        return TokenClaimsFactory.accessServiceId(settings.artifactoryServiceId().getInstanceId())
                .map(ServiceId::fromFormattedName)
                .flatMap(accessId -> SigningKeyVault.loadOrCreate(store, settings.encryption(), accessId, clock))
                .flatMap(keys -> EmbeddedAccessClient.builder()
                        .store(store)
                        .changeBus(bus)
                        .serviceId(settings.artifactoryServiceId())
                        .signingKeys(keys)
                        .passwordVerifier(verifier(hasher))
                        .encryption(settings.encryption())
                        .clock(clock)
                        .build())
                .map(client -> {
                    AdminBootstrapService bootstrap = settings.bootstrapFactory()
                            .apply(new StoreBootstrapStatePort(store, clock));
                    EaOutcome<Done> started = bootstrap.start(clock.instant());
                    if (!started.isOk()) {
                        log.error("The initial admin setup did not start ({}); the server stays closed until the "
                                + "cause is fixed and Artifactory is restarted", started);
                    }
                    EaTokenPurgeJob purge = EaTokenPurgeJob.start(store, clock,
                            EmbeddedAccessClient.DEFAULT_REFRESH_WINDOW, settings.purgeInterval());
                    log.info("Embedded access started as {}", client.accessServiceId().getFormattedName());
                    return new EmbeddedAccess(client, store, bus, bootstrap, purge);
                });
    }

    /**
     * Creates the user {@value #ANONYMOUS_USERNAME} if absent: internal, enabled, not an administrator, no
     * password.
     *
     * @param store the store
     * @param clock the creation time source
     * @return {@code Done}, or the store's refusal
     */
    static EaOutcome<Done> ensureAnonymousUser(EmbeddedAccessStore store, Clock clock) {
        return store.getUser(ANONYMOUS_USERNAME).flatMap(found -> found.isPresent()
                ? EaOutcome.done()
                : store.createUser(EaUser.builder(ANONYMOUS_USERNAME, clock.instant()).build())
                        .flatMap(created -> {
                            log.info("Created the built-in '{}' user without a password", ANONYMOUS_USERNAME);
                            return EaOutcome.done();
                        }));
    }

    /**
     * Binds a {@link PasswordHasher} to the client's {@link EaPasswordVerifier}. A password the hasher refuses to
     * encode (longer than bcrypt accepts) is an {@link IllegalArgumentException}, which the client reports as a
     * failed request.
     *
     * @param hasher the hasher
     * @return the verifier
     */
    static EaPasswordVerifier verifier(PasswordHasher hasher) {
        return new EaPasswordVerifier() {
            @Override
            public boolean matches(String raw, String hashOrNull) {
                return hasher.matches(raw, hashOrNull);
            }

            @Override
            public String encode(String raw) {
                EaOutcome<String> encoded = hasher.encode(raw);
                if (encoded instanceof EaOutcome.Ok<String> ok) {
                    return ok.value();
                }
                throw new IllegalArgumentException("the password cannot be hashed");
            }
        };
    }
}
