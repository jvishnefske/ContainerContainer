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

package org.artifactory.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.util.Optional;

/**
 * Lets {@link PasswordDecryptingManager} offer token credentials to plugin realms before its own Access token
 * handling, which would otherwise claim every JWT. Implemented by the Groovy plugin engine's realm provider.
 */
@FunctionalInterface
public interface PluginRealmGateway {

    /** A gateway with no realms: claims nothing, so authentication behaves as stock OSS. */
    PluginRealmGateway NONE = authentication -> Optional.empty();

    /**
     * Offers {@code authentication} (a Bearer token or a user name and password) to plugin realms when its
     * credential is a JWT that Access did not issue.
     *
     * @return the authentication a realm granted, or empty when no realm claimed the credential and the stock
     *         path should handle it
     * @throws AuthenticationException when a realm claimed the credential and refused it
     */
    Optional<Authentication> claim(Authentication authentication);
}
