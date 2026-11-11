/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.artifactory.async.Async;
import org.artifactory.repo.RepoPath;

/**
 * Runs plugin index handlers off the request thread.
 *
 * <p>{@link #dispatch} is an Artifactory {@link Async} method: the call returns at once and the work runs on
 * Artifactory's bounded async executor after the storing transaction commits, so the handler sees the committed
 * change. It runs as the system user so the handler can write index files regardless of the uploader's
 * permissions. Callers must inject the interface (the async proxy implements it), not the implementation class.
 */
public interface PluginIndexDispatcher {

    /**
     * Runs the index handler of the type named {@code typeName} for {@code stored} (a stored, copied-to or moved-to
     * path, or a removed one; see {@link org.artifactory.addon.plugin.pkgtype.PluginIndexHandler}), if that type is
     * still registered when the work runs.
     */
    @Async(delayUntilAfterCommit = true, authenticateAsSystem = true)
    void dispatch(String typeName, RepoPath stored);
}
