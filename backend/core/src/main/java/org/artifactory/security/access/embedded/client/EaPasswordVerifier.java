/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

/**
 * Hashes and checks passwords for embedded access. The client never sees a hashing algorithm: the wiring binds an
 * implementation (the bootstrap {@code PasswordHasher}) to this interface.
 * <p>
 * Implementations must be safe for concurrent use and must not log or keep the raw password.
 */
public interface EaPasswordVerifier {

    /**
     * Checks a raw password against a stored hash.
     * <p>
     * When {@code hashOrNull} is {@code null} (no password set, or an unknown user) the implementation must still
     * spend about the same time as a real comparison, for example against a fixed dummy hash, and return
     * {@code false}, so timing does not reveal whether an account exists or has a password.
     *
     * @param raw        the presented password, never {@code null}
     * @param hashOrNull the stored hash, or {@code null}
     * @return {@code true} only if the hash is present and matches
     */
    boolean matches(String raw, String hashOrNull);

    /**
     * Hashes a raw password for storage.
     *
     * @param raw the new password, never {@code null}
     * @return the encoded hash, self-describing (for example {@code {bcrypt}...})
     */
    String encode(String raw);
}
