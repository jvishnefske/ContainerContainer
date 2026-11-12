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

import groovy.lang.Closure;

import java.util.List;
import java.util.Objects;

/**
 * The code behind a realm's {@code authenticate} or {@code bearer} hook: a Groovy closure or a hosted call, such as a
 * WASM module run by the host. Both are invoked by {@link RealmInvoker}, so their answers go through the same Java
 * enforcement ({@link PluginRealmAuthenticator}, {@link RealmGrantPolicy}) and fail closed the same way.
 */
public sealed interface RealmHook {

    /** A closure of a Groovy {@code realms {}} block. */
    record Script(Closure<?> closure) implements RealmHook {
        public Script {
            Objects.requireNonNull(closure, "closure");
        }
    }

    /** A hook served by the host on behalf of a non-Groovy plugin. */
    record Hosted(Call call) implements RealmHook {
        public Hosted {
            Objects.requireNonNull(call, "call");
        }
    }

    /** A hosted realm hook. Implementations never throw; they answer {@link HostedOutcome.Failed} instead. */
    @FunctionalInterface
    interface Call {

        /**
         * @param credentials {@code [username, password]} for {@code authenticate}, {@code [token]} for
         *                    {@code bearer}; the list may hold {@code null}
         * @return the realm's answer, or why it could not give one
         */
        HostedOutcome<RealmResult> call(List<String> credentials);
    }
}
