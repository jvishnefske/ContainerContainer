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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives failures of plugin closures that were swallowed so the request could continue. Production code
 * logs them through {@link #logging()}; tests pass a recording implementation.
 */
@FunctionalInterface
public interface HookFailureReporter {

    /**
     * @param pluginName name of the plugin whose closure failed
     * @param what       which closure failed, for example {@code storage.afterCreate}
     * @param error      the failure
     */
    void report(String pluginName, String what, Throwable error);

    /** @return a reporter that logs at error level, naming the plugin and the closure */
    static HookFailureReporter logging() {
        Logger log = LoggerFactory.getLogger(HookFailureReporter.class);
        return (pluginName, what, error) ->
                log.error("User plugin '{}' failed in {}: {}", pluginName, what, HookInvoker.describe(error), error);
    }
}
