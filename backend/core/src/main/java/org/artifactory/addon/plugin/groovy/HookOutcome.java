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

import org.artifactory.exception.CancelException;

import java.util.Objects;

/**
 * The closed set of outcomes of running one plugin closure. {@link HookInvoker} lets no exception
 * escape (only fatal VM errors such as {@code OutOfMemoryError}); it classifies it here and the caller
 * decides what to do:
 * <ul>
 *     <li>{@link Completed}: the closure returned normally, carrying its return value (may be {@code null});</li>
 *     <li>{@link Failed}: the closure threw; the failure is reported with the plugin name and the operation
 *         continues as if the hook were absent;</li>
 *     <li>{@link Cancelled}: the closure threw {@link CancelException}, the explicit way for a hook to fail the
 *         operation with an error status; the caller rethrows it at the plugin boundary.</li>
 * </ul>
 */
public sealed interface HookOutcome {

    /** The closure returned normally. */
    record Completed(Object value) implements HookOutcome {
    }

    /** The closure threw something other than {@link CancelException}. */
    record Failed(Throwable error) implements HookOutcome {
        public Failed {
            Objects.requireNonNull(error, "error");
        }
    }

    /** The closure cancelled the operation. */
    record Cancelled(CancelException cancel) implements HookOutcome {
        public Cancelled {
            Objects.requireNonNull(cancel, "cancel");
        }
    }
}
