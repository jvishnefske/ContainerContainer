/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A fixed-window rate limit on initial-admin claim attempts (IA4), per client address and in total, as a pure
 * function {@code (state, client, now) -> (decision, new state)}.
 * <p>
 * Each window lasts {@link #window()} and starts at the first attempt after the previous one ended. An attempt is
 * allowed when the global window and the client's window both have room; an allowed attempt counts against both,
 * and a denied one counts against neither. The global limit is checked first.
 * <p>
 * At most {@link #maxTrackedClients()} client windows are kept. When a new client arrives at the limit, windows that
 * have ended are dropped first, then the window that started earliest. Evicting a client resets its count, which the
 * global limit bounds. The limiter and its {@link State} are immutable; the caller stores the returned state.
 */
public final class ClaimRateLimit {

    private final int perClientLimit;
    private final int globalLimit;
    private final Duration window;
    private final int maxTrackedClients;

    /**
     * Creates a limiter.
     *
     * @param perClientLimit    allowed attempts per client per window, positive
     * @param globalLimit       allowed attempts in total per window, positive
     * @param window            the window length, positive
     * @param maxTrackedClients the most client windows kept, positive
     * @throws IllegalArgumentException if a limit or the window is not positive
     */
    public ClaimRateLimit(int perClientLimit, int globalLimit, Duration window, int maxTrackedClients) {
        if (perClientLimit <= 0 || globalLimit <= 0 || maxTrackedClients <= 0) {
            throw new IllegalArgumentException("limits must be positive");
        }
        if (Objects.requireNonNull(window, "window").isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.perClientLimit = perClientLimit;
        this.globalLimit = globalLimit;
        this.window = window;
        this.maxTrackedClients = maxTrackedClients;
    }

    /**
     * Returns the defaults: 5 attempts per client and 30 in total per 15 minutes, tracking up to 1024 clients.
     *
     * @return the default limiter
     */
    public static ClaimRateLimit defaults() {
        return new ClaimRateLimit(5, 30, Duration.ofMinutes(15), 1024);
    }

    /**
     * Returns the window length.
     *
     * @return the window
     */
    public Duration window() {
        return window;
    }

    /**
     * Returns the most client windows kept.
     *
     * @return the bound
     */
    public int maxTrackedClients() {
        return maxTrackedClients;
    }

    /** The result of one check. */
    public enum Decision {
        /** The attempt may proceed. */
        ALLOWED,
        /** The client exhausted its window. */
        CLIENT_LIMITED,
        /** All clients together exhausted the global window. */
        GLOBAL_LIMITED
    }

    /**
     * One fixed window.
     *
     * @param start when the window started
     * @param count attempts counted in it
     */
    public record Window(Instant start, int count) {
        /** Validates the fields. */
        public Window {
            Objects.requireNonNull(start, "start");
            if (count < 0) {
                throw new IllegalArgumentException("count must not be negative");
            }
        }
    }

    /**
     * The limiter state: the global window, if any, and client windows in insertion order.
     *
     * @param global  the global window, or {@code null} before the first attempt
     * @param clients the client windows, keyed by client address
     */
    public record State(Window global, Map<String, Window> clients) {
        /** Copies the client map, keeping its order. */
        public State {
            clients = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(clients, "clients")));
        }

        /**
         * Returns the empty state.
         *
         * @return a state with no windows
         */
        public static State empty() {
            return new State(null, Map.of());
        }
    }

    /**
     * The result of {@link #check}.
     *
     * @param decision the decision
     * @param state    the state to store
     */
    public record Result(Decision decision, State state) {
    }

    /**
     * Checks one attempt.
     *
     * @param state  the current state
     * @param client the client address
     * @param now    the current time
     * @return the decision and the new state
     */
    public Result check(State state, String client, Instant now) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(now, "now");
        Window global = current(state.global(), now);
        if (global.count() >= globalLimit) {
            return new Result(Decision.GLOBAL_LIMITED, new State(global, state.clients()));
        }
        Window existing = state.clients().get(client);
        Window mine = current(existing, now);
        if (mine.count() >= perClientLimit) {
            return new Result(Decision.CLIENT_LIMITED, new State(global, state.clients()));
        }
        LinkedHashMap<String, Window> clients = new LinkedHashMap<>(state.clients());
        if (existing == null) {
            makeRoom(clients, now);
        }
        clients.put(client, new Window(mine.start(), mine.count() + 1));
        return new Result(Decision.ALLOWED, new State(new Window(global.start(), global.count() + 1), clients));
    }

    private Window current(Window w, Instant now) {
        return w == null || !now.isBefore(w.start().plus(window)) ? new Window(now, 0) : w;
    }

    private void makeRoom(LinkedHashMap<String, Window> clients, Instant now) {
        if (clients.size() < maxTrackedClients) {
            return;
        }
        clients.values().removeIf(w -> !now.isBefore(w.start().plus(window)));
        while (clients.size() >= maxTrackedClients) {
            String oldest = null;
            Instant oldestStart = null;
            for (Map.Entry<String, Window> e : clients.entrySet()) {
                if (oldestStart == null || e.getValue().start().isBefore(oldestStart)) {
                    oldest = e.getKey();
                    oldestStart = e.getValue().start();
                }
            }
            clients.remove(oldest);
        }
    }
}
