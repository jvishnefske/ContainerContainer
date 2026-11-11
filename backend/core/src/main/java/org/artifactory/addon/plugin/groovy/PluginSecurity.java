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

import org.artifactory.api.security.SecurityService;

import java.util.Objects;

/**
 * The {@code security} binding of plugin scripts: read-only facts about the user on whose behalf the
 * current hook runs. Groovy scripts can use it as {@code security.currentUsername} and {@code security.admin}.
 */
public final class PluginSecurity {

    private final SecurityService securityService;

    public PluginSecurity(SecurityService securityService) {
        this.securityService = Objects.requireNonNull(securityService, "securityService");
    }

    /** @return the current user's name */
    public String getCurrentUsername() {
        return securityService.currentUsername();
    }

    /** @return whether the current user is an administrator */
    public boolean isAdmin() {
        return securityService.isAdmin();
    }

    /** @return whether the current user is anonymous */
    public boolean isAnonymous() {
        return securityService.isAnonymous();
    }
}
