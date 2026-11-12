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

import org.artifactory.addon.plugin.groovy.ExecutionHook;
import org.artifactory.addon.plugin.groovy.RealmResult;

import java.util.Objects;

/**
 * The {@code result} of a reply, decoded for the hook of the call: realm hooks answer a {@link RealmResult} (mapped
 * one to one from {@code authenticated} / {@code not_mine} / {@code rejected}), executions an HTTP response.
 */
public sealed interface GuestResult {

    /** The answer of a realm hook; Java enforcement still decides what it grants. */
    record Realm(RealmResult result) implements GuestResult {
        public Realm {
            Objects.requireNonNull(result, "result");
        }
    }

    /** The answer of an execution. */
    record Execution(ExecutionHook.Response response) implements GuestResult {
        public Execution {
            Objects.requireNonNull(response, "response");
        }
    }
}
