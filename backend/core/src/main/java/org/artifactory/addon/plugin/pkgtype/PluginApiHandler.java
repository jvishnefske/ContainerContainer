/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype;

/**
 * Serves the HTTP API of a plugin package type: every request to {@code /api/docker/{repoKey}/...} or
 * {@code /api/pkg/{repoKey}/...} for a local repository of the type, with any method among GET, HEAD, PUT, POST,
 * PATCH and DELETE.
 *
 * <p>The handler runs only after the request shell has resolved the repository and checked the caller's permission
 * on it (read for GET/HEAD, deploy for PUT/POST/PATCH, delete for DELETE) and that it is not blacked out. It never
 * sees a request the caller is not allowed to make on the repository as a whole; finer, path-level policy is the
 * handler's own business.
 *
 * <p>The handler returns a response value rather than writing to the client. It must not return {@code null} and
 * should not throw: the shell answers either with a generic 500 and logs the failure with the plugin and type
 * name. A streamed response body is closed by the shell whether or not it is fully sent.
 */
@FunctionalInterface
public interface PluginApiHandler {
    PluginApiResponse handle(PluginApiRequest request);
}
