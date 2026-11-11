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

package org.artifactory.addon.plugin.pkgtype;

import org.artifactory.repo.RepoPath;

import java.util.Objects;

/** The closed set of outcomes a {@link PluginUploadHandler} can return. */
public sealed interface UploadDecision {

    /** Store the upload at the requested path. */
    record Accept() implements UploadDecision {}

    /** Refuse the upload with an HTTP status (4xx or 5xx) and a message. */
    record Reject(int status, String message) implements UploadDecision {
        public Reject {
            if (status < 400 || status > 599) {
                throw new IllegalArgumentException("Reject status must be 4xx or 5xx, got " + status);
            }
            Objects.requireNonNull(message, "message");
        }
    }

    /** Store the upload at a different path, which must be in the same repository. */
    record Rewrite(RepoPath target) implements UploadDecision {
        public Rewrite {
            Objects.requireNonNull(target, "target");
        }
    }

    static UploadDecision accept() { return new Accept(); }
    static UploadDecision reject(int status, String message) { return new Reject(status, message); }
    static UploadDecision rewrite(RepoPath target) { return new Rewrite(target); }
}
