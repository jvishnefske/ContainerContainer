/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded;

import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService;
import org.artifactory.security.access.embedded.client.EmbeddedAccessClient;
import org.artifactory.security.access.embedded.store.EaChangeBus;
import org.artifactory.security.access.embedded.store.EmbeddedAccessStore;

import java.util.Objects;

/**
 * One running embedded Access instance, as built by {@link EmbeddedAccessFactory#create}: the client Artifactory
 * talks to, the store and change bus beneath it, the initial-admin bootstrap service and the token purge job.
 * <p>
 * Ownership is a tree rooted here: the instance owns the bus and the purge job and stops both in {@link #close};
 * the store is stateless over the Artifactory data source, which Artifactory owns. {@code AccessServiceImpl} holds
 * the instance for the life of the application context.
 *
 * @param client    the {@code AccessClient} implementation
 * @param store     the store
 * @param bus       the change bus the store publishes to and the client subscribes Artifactory's listeners on
 * @param bootstrap the started initial-admin bootstrap service, to be published in the application context
 * @param purgeJob  the scheduled expired-token purge
 */
public record EmbeddedAccess(EmbeddedAccessClient client, EmbeddedAccessStore store, EaChangeBus bus,
        AdminBootstrapService bootstrap, EaTokenPurgeJob purgeJob) implements AutoCloseable {

    /** Checks that nothing is missing. */
    public EmbeddedAccess {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(bootstrap, "bootstrap");
        Objects.requireNonNull(purgeJob, "purgeJob");
    }

    /** Stops the purge job and the change bus. */
    @Override
    public void close() {
        purgeJob.close();
        bus.close();
    }
}
