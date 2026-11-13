/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

/**
 * The strictly increasing {@code permissions.last_updated} stamp. Artifactory polls it to notice user, group and
 * permission changes, so every change must move it forward even when the wall clock stands still or steps back:
 * {@code next(now, prev) = max(now, prev + 1)}. Values are epoch milliseconds.
 */
public final class LastUpdatedClock {

    private LastUpdatedClock() {
    }

    /**
     * Returns the next stamp.
     *
     * @param nowMillis  the current wall-clock time
     * @param prevMillis the previous stamp
     * @return {@code max(nowMillis, prevMillis + 1)}, or {@code Refused} if {@code prevMillis} is
     *         {@link Long#MAX_VALUE} and cannot advance
     */
    public static EaOutcome<Long> next(long nowMillis, long prevMillis) {
        if (prevMillis == Long.MAX_VALUE) {
            return EaOutcome.refused("last-updated stamp cannot advance past Long.MAX_VALUE");
        }
        return EaOutcome.ok(Math.max(nowMillis, prevMillis + 1));
    }
}
