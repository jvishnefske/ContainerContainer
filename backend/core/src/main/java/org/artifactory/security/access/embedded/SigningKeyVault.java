/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded;

import org.artifactory.security.access.embedded.client.EaKeyMaterial;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.SigningKeys;
import org.artifactory.security.access.embedded.store.EaKvEntry;
import org.artifactory.security.access.embedded.store.EmbeddedAccessStore;
import org.jfrog.access.common.ServiceId;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Optional;

/**
 * Keeps the embedded Access token-signing key in the store, encrypted with Artifactory's master key.
 * <p>
 * The {@code ea_kv} entry {@value #KEY} holds {@link SigningKeys#serialise()} encrypted by
 * {@link EncryptionWrapper#encryptIfNeeded} (the master encryption wrapper of the Artifactory home). On the first
 * start {@link #loadOrCreate} generates a key with {@link EaKeyMaterial#generate} and stores it by
 * compare-and-swap from absent; on later starts it decrypts and parses the stored key, so tokens signed before a
 * restart still verify. The key is never stored or logged in clear: without an encryption wrapper, or when the
 * stored value is not encrypted by this wrapper (for example after the master key was replaced), the call is
 * {@code Refused} and startup must stop.
 */
public final class SigningKeyVault {

    /** The {@code ea_kv} key of the encrypted signing key. */
    public static final String KEY = "signing.key";

    private static final Logger log = LoggerFactory.getLogger(SigningKeyVault.class);

    private SigningKeyVault() {
    }

    /**
     * Returns the stored signing keys, creating them on the first call.
     *
     * @param store           the store
     * @param encryption      the master encryption wrapper
     * @param accessServiceId the embedded Access service id, {@code jfac@<id>}, the subject of a new certificate
     * @param clock           the clock for a new certificate's validity
     * @return the cleartext keys, or {@code Refused} with a reason for the operator
     */
    public static EaOutcome<SigningKeys> loadOrCreate(EmbeddedAccessStore store, EncryptionWrapper encryption,
            ServiceId accessServiceId, Clock clock) {
        if (encryption == null) {
            return EaOutcome.refused("the master encryption key is not available; the embedded access signing "
                    + "key is never stored in clear");
        }
        EaOutcome<Optional<EaKvEntry>> stored = store.getKv(KEY);
        if (stored instanceof EaOutcome.Ok<Optional<EaKvEntry>> ok && ok.value().isPresent()) {
            return decode(ok.value().get().value(), encryption);
        }
        if (!stored.isOk()) {
            return stored.flatMap(ignored -> EaOutcome.refused("unreachable"));
        }
        return EaKeyMaterial.generate(accessServiceId, clock).flatMap(keys -> {
            String encrypted = encryption.encryptIfNeeded(keys.serialise());
            if (!encryption.isEncodedByMe(encrypted)) {
                return EaOutcome.refused("the master encryption key did not encrypt the signing key");
            }
            EaOutcome<EaKvEntry> written = store.compareAndSetKv(KEY, EaKvEntry.ABSENT_REVISION, encrypted);
            if (written.isOk()) {
                log.info("Generated the embedded access token signing key");
                return EaOutcome.ok(keys);
            }
            // Someone stored a key first: use theirs, so every token verifies against one key.
            return store.getKv(KEY).flatMap(entry -> entry
                    .map(e -> decode(e.value(), encryption))
                    .orElseGet(() -> written.flatMap(ignored -> EaOutcome.refused("unreachable"))));
        });
    }

    private static EaOutcome<SigningKeys> decode(String stored, EncryptionWrapper encryption) {
        if (!encryption.isEncodedByMe(stored)) {
            return EaOutcome.refused("the stored embedded access signing key is not encrypted with the current "
                    + "master key");
        }
        String clear;
        try {
            clear = encryption.decryptIfNeeded(stored).getDecryptedData();
        } catch (RuntimeException e) {
            return EaOutcome.refused("the stored embedded access signing key cannot be decrypted: "
                    + e.getClass().getSimpleName());
        }
        return SigningKeys.parse(clear).flatMap(keys -> EaKeyMaterial.parse(keys).map(parsed -> keys));
    }
}
