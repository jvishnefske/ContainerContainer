/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.bootstrap;

import org.artifactory.security.access.embedded.core.BootstrapRules;
import org.artifactory.security.access.embedded.core.BootstrapRules.BootstrapState;
import org.artifactory.security.access.embedded.core.BootstrapRules.Claimed;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.EaOutcome.Done;

import java.util.Objects;
import java.util.Optional;

/**
 * An in-memory {@link BootstrapStatePort} for tests, with the same compare-and-swap semantics as the store: the
 * state is kept in its serialised form, and the admin password hash is kept beside it. Methods are synchronized so
 * the port is atomic like a transaction.
 */
public final class InMemoryBootstrapStatePort implements BootstrapStatePort {

    private String serialised;
    private String adminPasswordHash;
    private int saves;
    private int loads;

    /** Creates an empty port: no state stored, no admin password. */
    public InMemoryBootstrapStatePort() {
    }

    /**
     * Creates a port holding a state.
     *
     * @param state the initial state
     * @return the port
     */
    public static InMemoryBootstrapStatePort holding(BootstrapState state) {
        InMemoryBootstrapStatePort port = new InMemoryBootstrapStatePort();
        port.serialised = BootstrapRules.serialise(state);
        return port;
    }

    @Override
    public synchronized EaOutcome<Optional<BootstrapState>> load() {
        loads++;
        if (serialised == null) {
            return EaOutcome.ok(Optional.empty());
        }
        return BootstrapRules.parse(serialised).map(Optional::of);
    }

    @Override
    public synchronized EaOutcome<Done> save(BootstrapState newState, Optional<String> expectedPriorSerialised) {
        if (!Objects.equals(serialised, expectedPriorSerialised.orElse(null))) {
            return EaOutcome.refused("bootstrap state changed concurrently");
        }
        serialised = BootstrapRules.serialise(newState);
        saves++;
        return EaOutcome.done();
    }

    @Override
    public synchronized EaOutcome<Done> claim(Claimed newState, String expectedPriorSerialised,
            String passwordHash) {
        if (!Objects.equals(serialised, expectedPriorSerialised)) {
            return EaOutcome.refused("bootstrap state changed concurrently");
        }
        serialised = BootstrapRules.serialise(newState);
        adminPasswordHash = passwordHash;
        return EaOutcome.done();
    }

    /**
     * Returns the stored state in serialised form.
     *
     * @return the text, or {@code null} if none
     */
    public synchronized String serialised() {
        return serialised;
    }

    /**
     * Returns the admin password hash set by a claim.
     *
     * @return the hash, or {@code null} if never claimed
     */
    public synchronized String adminPasswordHash() {
        return adminPasswordHash;
    }

    /**
     * Returns the number of successful {@link #save} calls.
     *
     * @return the count
     */
    /**
     * Returns how many times {@link #load()} was called.
     *
     * @return the load count
     */
    public synchronized int loads() {
        return loads;
    }

    public synchronized int saves() {
        return saves;
    }
}
