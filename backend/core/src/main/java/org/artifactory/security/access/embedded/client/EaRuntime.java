/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.artifactory.security.access.embedded.core.PasswordPolicy;
import org.artifactory.security.access.embedded.core.ScopeGrammar;
import org.artifactory.security.access.embedded.store.EaChangeBus;
import org.artifactory.security.access.embedded.store.EmbeddedAccessStore;
import org.jfrog.access.common.ServiceId;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * What every adapter of one {@link EmbeddedAccessClient} shares: the store, the change bus, the clock, both service
 * ids, the password verifier and policy, the scope grammar and the signing material. Immutable; the adapters form
 * a tree under the client and each holds this record, so there is no shared mutable state beyond the store and the
 * bus, which own their own concurrency.
 *
 * @param store                the persistent store
 * @param bus                  the change bus the store publishes to
 * @param clock                the clock
 * @param artifactoryServiceId the Artifactory service id, {@code jfrt@<id>}: the audience tokens are verified for
 * @param accessServiceId      the embedded Access service id, {@code jfac@<id>}: the token issuer
 * @param passwords            hashes and checks passwords
 * @param passwordPolicy       the IA6 password policy
 * @param grammar              the scope grammar tokens are validated with
 * @param signingKey           the RS256 private key
 * @param certificate          the certificate of the signing key
 */
record EaRuntime(EmbeddedAccessStore store, EaChangeBus bus, Clock clock, ServiceId artifactoryServiceId,
        ServiceId accessServiceId, EaPasswordVerifier passwords, PasswordPolicy passwordPolicy, ScopeGrammar grammar,
        PrivateKey signingKey, X509Certificate certificate) {

    /** Checks that nothing is missing. */
    EaRuntime {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(artifactoryServiceId, "artifactoryServiceId");
        Objects.requireNonNull(accessServiceId, "accessServiceId");
        Objects.requireNonNull(passwords, "passwords");
        Objects.requireNonNull(passwordPolicy, "passwordPolicy");
        Objects.requireNonNull(grammar, "grammar");
        Objects.requireNonNull(signingKey, "signingKey");
        Objects.requireNonNull(certificate, "certificate");
    }

    /**
     * Returns the current time.
     *
     * @return now
     */
    Instant now() {
        return clock.instant();
    }
}
