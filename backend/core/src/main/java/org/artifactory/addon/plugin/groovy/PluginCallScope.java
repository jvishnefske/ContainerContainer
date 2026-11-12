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

package org.artifactory.addon.plugin.groovy;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Which kind of plugin code the current thread is running, so the {@code security} binding can offer
 * {@code createAccessToken} and {@code findUser} to realm and execution code only (P4).
 *
 * <p>This is a {@link ThreadLocal}, the one piece of shared state the realm support adds. It is justified because
 * the {@code security} binding is a single object bound into the script at load time, while the kind of code
 * calling it is only known at call time on the request thread; a thread-local is the narrowest way to carry that
 * without changing the closure calling convention of every hook. It is always set and restored in a
 * {@code try/finally} by {@link #run}, so it never leaks to the next request on a pooled thread.
 */
public final class PluginCallScope {

    /** The kinds of plugin code that may use the realm and execution parts of the {@code security} binding. */
    public enum Kind {
        REALM, EXECUTION
    }

    private static final ThreadLocal<Kind> CURRENT = new ThreadLocal<>();

    private PluginCallScope() {
    }

    /** Runs {@code body} with {@code kind} as the current scope and restores the previous one afterwards. */
    public static <T> T run(Kind kind, Supplier<T> body) {
        Kind previous = CURRENT.get();
        CURRENT.set(kind);
        try {
            return body.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** @return the scope of the code running on this thread, empty outside realm and execution code */
    public static Optional<Kind> current() {
        return Optional.ofNullable(CURRENT.get());
    }
}
