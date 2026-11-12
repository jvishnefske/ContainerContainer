/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.wasm;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The resource limits of one WASM plugin, from the manifest's {@code limits} block. Every limit has a default and
 * hard bounds ({@link Limit}); a manifest value outside the bounds fails the load, so an administrator can tighten
 * limits but never lift them past what the host is built to bear.
 *
 * @param memoryPages      maximum linear memory, in 64 KiB pages
 * @param stepTimeoutMs    deadline of one step (instantiation and {@code _start}), in milliseconds
 * @param maxSteps         steps per call, including the one that returns the result
 * @param maxStdinBytes    size of the largest envelope the host writes
 * @param maxStdoutBytes   size of the largest reply the host reads; more output fails the call
 * @param maxStateBytes    size of the largest state the host keeps; a larger replacement fails the call
 * @param httpTimeoutMs    deadline of one {@code http_get} effect, connection and body included
 * @param maxHttpBodyBytes size of the largest {@code http_get} response body
 */
public record PluginLimits(int memoryPages, int stepTimeoutMs, int maxSteps, int maxStdinBytes, int maxStdoutBytes,
        int maxStateBytes, int httpTimeoutMs, int maxHttpBodyBytes) {

    private static final int KIB = 1024;
    private static final int MIB = 1024 * KIB;

    /** Every limit with its manifest key, default and inclusive bounds. */
    public enum Limit {
        MEMORY_PAGES("memoryPages", 256, 1, 1024),
        STEP_TIMEOUT_MS("stepTimeoutMs", 2000, 10, 30_000),
        MAX_STEPS("maxSteps", 4, 1, 16),
        MAX_STDIN_BYTES("maxStdinBytes", MIB, KIB, 16 * MIB),
        MAX_STDOUT_BYTES("maxStdoutBytes", MIB, KIB, 16 * MIB),
        MAX_STATE_BYTES("maxStateBytes", 64 * KIB, 0, MIB),
        HTTP_TIMEOUT_MS("httpTimeoutMs", 5000, 100, 30_000),
        MAX_HTTP_BODY_BYTES("maxHttpBodyBytes", 256 * KIB, 0, 4 * MIB);

        private final String key;
        private final int defaultValue;
        private final int min;
        private final int max;

        Limit(String key, int defaultValue, int min, int max) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
        }

        /** @return the manifest key */
        public String key() {
            return key;
        }

        /** @return the value used when the manifest does not set it */
        public int defaultValue() {
            return defaultValue;
        }

        /** @return the smallest allowed value */
        public int min() {
            return min;
        }

        /** @return the largest allowed value */
        public int max() {
            return max;
        }

        /** @return the limit with manifest key {@code key}, if any. Pure. */
        public static Optional<Limit> byKey(String key) {
            for (Limit limit : values()) {
                if (limit.key.equals(key)) {
                    return Optional.of(limit);
                }
            }
            return Optional.empty();
        }
    }

    public PluginLimits {
        check(Limit.MEMORY_PAGES, memoryPages);
        check(Limit.STEP_TIMEOUT_MS, stepTimeoutMs);
        check(Limit.MAX_STEPS, maxSteps);
        check(Limit.MAX_STDIN_BYTES, maxStdinBytes);
        check(Limit.MAX_STDOUT_BYTES, maxStdoutBytes);
        check(Limit.MAX_STATE_BYTES, maxStateBytes);
        check(Limit.HTTP_TIMEOUT_MS, httpTimeoutMs);
        check(Limit.MAX_HTTP_BODY_BYTES, maxHttpBodyBytes);
    }

    /** @return every limit at its default */
    public static PluginLimits defaults() {
        return of(Map.of());
    }

    /**
     * @param values the limits the manifest sets; the others take their default
     * @throws IllegalArgumentException when a value is out of its bounds
     */
    public static PluginLimits of(Map<Limit, Integer> values) {
        Map<Limit, Integer> all = new EnumMap<>(Limit.class);
        for (Limit limit : Limit.values()) {
            all.put(limit, values.getOrDefault(limit, limit.defaultValue));
        }
        return new PluginLimits(all.get(Limit.MEMORY_PAGES), all.get(Limit.STEP_TIMEOUT_MS),
                all.get(Limit.MAX_STEPS), all.get(Limit.MAX_STDIN_BYTES), all.get(Limit.MAX_STDOUT_BYTES),
                all.get(Limit.MAX_STATE_BYTES), all.get(Limit.HTTP_TIMEOUT_MS), all.get(Limit.MAX_HTTP_BODY_BYTES));
    }

    private static void check(Limit limit, int value) {
        if (value < limit.min || value > limit.max) {
            throw new IllegalArgumentException("limits." + limit.key + " must be between " + limit.min + " and "
                    + limit.max + ", not " + value);
        }
    }
}
