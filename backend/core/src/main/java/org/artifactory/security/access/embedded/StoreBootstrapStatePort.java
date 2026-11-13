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
import org.artifactory.security.access.embedded.bootstrap.BootstrapStatePort;
import org.artifactory.security.access.embedded.core.BootstrapRules;
import org.artifactory.security.access.embedded.core.BootstrapRules.BootstrapState;
import org.artifactory.security.access.embedded.core.BootstrapRules.Claimed;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.EaOutcome.Done;
import org.artifactory.security.access.embedded.store.EaKvEntry;
import org.artifactory.security.access.embedded.store.EmbeddedAccessStore;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@link BootstrapStatePort} over the embedded Access store.
 * <p>
 * The state is the {@code ea_kv} entry {@value #STATE_KEY}, holding {@link BootstrapRules#serialise} text.
 * {@link #save} is a single compare-and-swap on that entry's revision after checking its value;
 * {@link #claim} is {@link EmbeddedAccessStore#setPasswordAndReplaceKv}, so the administrator's password, its
 * must-change flag and the claimed state are written in one transaction.
 */
public final class StoreBootstrapStatePort implements BootstrapStatePort {

    /** The {@code ea_kv} key of the bootstrap state. */
    public static final String STATE_KEY = "bootstrap.state";

    private final EmbeddedAccessStore store;
    private final Clock clock;

    /**
     * Creates the port.
     *
     * @param store the store
     * @param clock the source of the password modification time on claim
     */
    public StoreBootstrapStatePort(EmbeddedAccessStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public EaOutcome<Optional<BootstrapState>> load() {
        return store.getKv(STATE_KEY).flatMap(entry -> entry.isEmpty()
                ? EaOutcome.ok(Optional.<BootstrapState>empty())
                : BootstrapRules.parse(entry.get().value()).map(Optional::of));
    }

    @Override
    public EaOutcome<Done> save(BootstrapState newState, Optional<String> expectedPriorSerialised) {
        String value = BootstrapRules.serialise(newState);
        return store.getKv(STATE_KEY).flatMap(entry -> {
            if (!entry.map(EaKvEntry::value).equals(expectedPriorSerialised)) {
                return EaOutcome.refused("conflict: bootstrap state changed");
            }
            long revision = entry.map(EaKvEntry::revision).orElse(EaKvEntry.ABSENT_REVISION);
            return store.compareAndSetKv(STATE_KEY, revision, value).flatMap(written -> EaOutcome.done());
        });
    }

    @Override
    public EaOutcome<Done> claim(Claimed newState, Optional<String> expectedPriorSerialised,
            String adminPasswordHash, boolean mustChangePassword) {
        return store.setPasswordAndReplaceKv(AdminBootstrapService.ADMIN_USERNAME, adminPasswordHash,
                mustChangePassword, clock.instant(), STATE_KEY, expectedPriorSerialised,
                BootstrapRules.serialise(newState)).flatMap(user -> EaOutcome.done());
    }
}
