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

import java.util.Objects;

/**
 * What a hosted (non-Groovy) plugin hook answered: either a value or a failure reason. Hosted hooks, such as those
 * of WASM plugins, never throw across the plugin boundary; they report every problem as {@link Failed}, and the
 * caller fails closed with it exactly as it does for a Groovy closure that threw: a realm counts as "not mine" and
 * the failure is reported ({@link RealmInvoker}), an execution answers 500 ({@link GroovyPluginEngine#execute}).
 *
 * @param <T> the type of a successful answer
 */
public sealed interface HostedOutcome<T> {

    /** The hook answered with {@code value}. */
    record Answered<T>(T value) implements HostedOutcome<T> {
        public Answered {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * The hook failed; nothing it asked for takes effect.
     *
     * @param reason human-readable reason for logs and plugin status; never contains credentials
     */
    record Failed<T>(String reason) implements HostedOutcome<T> {
        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
