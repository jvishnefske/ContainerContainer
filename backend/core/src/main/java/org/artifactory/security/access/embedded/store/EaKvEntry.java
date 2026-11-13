/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.store;

import java.util.Objects;

/**
 * One row of the {@code ea_kv} table: a value with the revision that compare-and-swap writes check.
 * <p>
 * Revisions start at 1 when a key is first written and grow by one on every write. Revision
 * {@link #ABSENT_REVISION} (0) never names a stored row; passing it as the expected revision means "the key must not
 * exist yet".
 *
 * @param key      the key, never blank
 * @param revision the revision, at least 1
 * @param value    the stored text, never {@code null}
 */
public record EaKvEntry(String key, long revision, String value) {

    /** The expected revision that means "the key is absent". */
    public static final long ABSENT_REVISION = 0L;

    /** Validates the fields. */
    public EaKvEntry {
        if (Objects.requireNonNull(key, "key").isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (revision <= ABSENT_REVISION) {
            throw new IllegalArgumentException("revision must be at least 1");
        }
        Objects.requireNonNull(value, "value");
    }
}
