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

package org.artifactory.signature;

import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.spring.ReloadableBean;
import org.jfrog.storage.binstore.exceptions.SignedUrlException;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Rotem Kfir
 */
public interface SignedUrlService extends ReloadableBean {

    /**
     * Generates a random signing key, and stores it encrypted in CONFIGS table
     */
    void createAndStoreSigningKey();

    /**
     * Sets the URL signing key
     *
     * @param signingKey the key, not encrypted
     */
    void setSigningKey(byte[] signingKey);

    /**
     * Gets the URL signing key
     *
     * @return the key, not encrypted
     */
    @Nullable
    byte[] getSigningKey();

    /**
     * Creates a Url with signed token (with the given path's checksum)
     *
     * @param serverUrl       base url
     * @param path            path to a file or to an API
     * @param validForSeconds the number of seconds until signature's expiration
     * @param isUiRequest     True will cause the principal to be only the username, without "signed:" as prefix
     *                        (see {@link #verifySignedToken}
     * @return A full url to the path together with JSON web signature (JWS) as path param
     * @throws SignedUrlException if the JWS object couldn't be signed
     */
    @Nonnull
    String createSignedUrl(String serverUrl, @Nonnull String path, Long validForSeconds, boolean isUiRequest);

    /**
     * Creates a Url with signed token (with the given path's checksum)
     *
     * @param serverUrl       base url
     * @param path            path to a file or to an API
     * @param internalRepoKey origin repoKey in case of virtual repo
     * @param validForSeconds the number of seconds until signature's expiration
     * @param useOriginPrincipal     True will cause the principal to be only the username, without "signed:" as prefix
     *                        (see {@link #verifySignedToken}
     * @return A full url to the path together with JSON web signature (JWS) as path param
     * @throws SignedUrlException if the JWS object couldn't be signed
     */
    @Nonnull
    String createSignedUrl(@Nonnull String serverUrl, @Nonnull String path, @Nullable String internalRepoKey,
            @Nullable Long validForSeconds, boolean useOriginPrincipal);

    /**
     * Verifies the signature
     *
     * @param path        path to the file or to an API
     * @param signedToken signed token to verify
     * @return the token's issuer with "signed:" prefix for REST API request, or only the principal for UI request.
     * i.e.: "sigend:gandalf" for REST API request, "gandalf" for UI request.
     * @throws SignedUrlException if the signed token is not valid
     */
    IssuerModel verifySignedToken(@Nonnull String path, @Nonnull String signedToken);

    /**
     * Performs export of signing key into a file under export folder
     */
    void exportTo(ExportSettings settings);

    /**
     * Performs import of signing key into DB's configs table
     */
    void importFrom(ImportSettings settings);
}
