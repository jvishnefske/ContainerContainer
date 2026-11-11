/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.springframework.stereotype.Component;

/**
 * Marks the thread that is running a plugin index handler, together with the repository it indexes, so that writes
 * the handler makes to that repository do not schedule the handler again.
 *
 * <p>Storage interceptors run synchronously on the thread that writes, so a thread-local mark set around the handler
 * call is seen by every {@code afterCreate} the handler's own writes cause. The mark is cleared in a {@code finally}
 * block and restored to its previous value, so nesting (an index handler of one repository writing to another
 * plugin-typed repository) is handled.
 */
@Component
public class PluginIndexReentryGuard {

    private final ThreadLocal<String> indexingRepoKey = new ThreadLocal<>();

    /** Whether the current thread is running an index handler for {@code repoKey}. */
    public boolean isIndexing(String repoKey) {
        return repoKey != null && repoKey.equals(indexingRepoKey.get());
    }

    /** Runs {@code body} with the current thread marked as indexing {@code repoKey}. */
    public void runIndexing(String repoKey, Runnable body) {
        String previous = indexingRepoKey.get();
        indexingRepoKey.set(repoKey);
        try {
            body.run();
        } finally {
            if (previous == null) {
                indexingRepoKey.remove();
            } else {
                indexingRepoKey.set(previous);
            }
        }
    }
}
