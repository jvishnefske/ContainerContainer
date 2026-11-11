/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.addon.signed.url;

import org.artifactory.common.addon.Addon;
import org.artifactory.util.UnsupportedByLicenseException;
import org.jfrog.storage.binstore.exceptions.SignedUrlException;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Rotem Kfir
 */
public interface SignedUrlAddon extends Addon {
    UnsupportedByLicenseException SIGNED_URL_UNSUPPORTED_OPERATION_EXCEPTION = new UnsupportedByLicenseException(
            "Signed URL is only available on Enterprise licensed Artifactory instances.");

    /**
     * Generates a key for signing URLs
     */
    default void generateSigningKey() {
        throw SIGNED_URL_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Creates a signed URL for downloading the given repoPath
     *
     * @param repoPath        path to the file
     * @param validForSeconds the number of seconds until URL's expiration
     * @return URL for downloading the given repoPath with a parameter of JSON web signature (JWS)
     * @throws SignedUrlException if the URL couldn't be signed
     */
    default String createSignedUrl(@Nonnull String serverUrl, @Nonnull String repoPath,
            @Nullable Long validForSeconds) {
        throw SIGNED_URL_UNSUPPORTED_OPERATION_EXCEPTION;
    }

}
