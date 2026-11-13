/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.bootstrap;

import org.artifactory.security.access.embedded.core.BootstrapRules.BootstrapState;
import org.artifactory.security.access.embedded.core.BootstrapRules.Claimed;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.EaOutcome.Done;

import java.util.Optional;

/**
 * The persistence that {@link AdminBootstrapService} needs, and nothing more: the stored bootstrap state, written
 * with compare-and-swap, and the one transaction that claims the server.
 * <p>
 * States are compared by their {@link org.artifactory.security.access.embedded.core.BootstrapRules#serialise}
 * text. A write whose expected prior state no longer matches is {@code Refused} and changes nothing. The embedded
 * store implements this port (wired by the embedded access factory); tests use an in-memory implementation.
 */
public interface BootstrapStatePort {

    /**
     * Reads the stored state.
     *
     * @return the state, empty if none is stored, or {@code Refused} if it cannot be read or parsed
     */
    EaOutcome<Optional<BootstrapState>> load();

    /**
     * Replaces the stored state if the stored state is still {@code expectedPriorSerialised}.
     *
     * @param newState                the state to store
     * @param expectedPriorSerialised the serialised state the caller read, or empty if it read none
     * @return {@code Done}, or {@code Refused} if the stored state changed or the write failed
     */
    EaOutcome<Done> save(BootstrapState newState, Optional<String> expectedPriorSerialised);

    /**
     * Claims the server in one transaction: sets the password hash of {@code admin} (clearing its
     * must-change-password flag, since the holder chose the password) and replaces the stored state with
     * {@code newState} if it is still {@code expectedPriorSerialised}. Either both writes happen or neither does.
     *
     * @param newState                the claimed state
     * @param expectedPriorSerialised the serialised unclaimed state the claim was checked against
     * @param adminPasswordHash       the encoded password hash, as produced by {@link PasswordHasher#encode}
     * @return {@code Done}, or {@code Refused} if the stored state changed or the transaction failed
     */
    EaOutcome<Done> claim(Claimed newState, String expectedPriorSerialised, String adminPasswordHash);
}
