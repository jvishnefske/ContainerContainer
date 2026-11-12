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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The state the host keeps for one loaded WASM plugin (W3): one immutable {@link JsonText}, replaced whole when a
 * call that finished with a result carried a replacement. Failed calls never reach {@link #commit}. The
 * {@link AtomicReference} is the one shared handle per plugin, since concurrent requests run calls of the same
 * plugin; concurrent commits are last-writer-wins, each a complete value. State lives as long as the loaded plugin
 * generation: a reload starts the plugin with no state.
 */
public final class PluginState {

    private final AtomicReference<Optional<JsonText>> current = new AtomicReference<>(Optional.empty());

    /** @return the current state; empty before any call stored one */
    public Optional<JsonText> current() {
        return current.get();
    }

    /** Stores {@code replacement} when present; an empty one keeps the current state. */
    public void commit(Optional<JsonText> replacement) {
        replacement.ifPresent(value -> current.set(Optional.of(value)));
    }
}
