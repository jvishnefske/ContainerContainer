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
import java.util.Set;

/**
 * The identity of whoever asks to run a plugin execution, captured from the security context by the shell.
 *
 * @param username user name, {@code anonymous} for unauthenticated callers
 * @param admin    whether the caller is an administrator
 * @param groups   names of the groups the caller belongs to
 */
public record Caller(String username, boolean admin, Set<String> groups) {

    public Caller {
        Objects.requireNonNull(username, "username");
        groups = Set.copyOf(groups);
    }
}
