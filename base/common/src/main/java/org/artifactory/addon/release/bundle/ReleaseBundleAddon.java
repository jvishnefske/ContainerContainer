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

package org.artifactory.addon.release.bundle;

import lombok.NonNull;
import org.artifactory.common.addon.Addon;
import org.artifactory.addon.license.EnterprisePlusAddon;
import org.artifactory.addon.release.bundle.airgap.AirGapReleaseBundleException;
import org.artifactory.addon.release.bundle.airgap.AirGapReleaseBundleValidationException;
import org.artifactory.addon.release.bundle.exception.ReleaseBundleArtifactValidationException;
import org.artifactory.addon.release.bundle.exception.ReleaseBundleSignatureValidationException;
import org.artifactory.api.release.bundle.ReleaseBundleSearchFilter;
import org.artifactory.api.rest.distribution.bundle.models.BundleTransactionResponse;
import org.artifactory.api.rest.distribution.bundle.models.BundleVersionsResponse;
import org.artifactory.api.rest.distribution.bundle.models.BundlesNamesResponse;
import org.artifactory.api.rest.distribution.bundle.models.BundlesResponse;
import org.artifactory.api.rest.distribution.bundle.models.CloseTransactionStatusResponse;
import org.artifactory.api.rest.distribution.bundle.models.ReleaseBundleModel;
import org.artifactory.api.rest.release.ReleaseBundleArtifactsModel;
import org.artifactory.api.rest.release.ReleaseBundleRequest;
import org.artifactory.api.rest.release.ReleaseBundleResult;
import org.artifactory.api.rest.release.ReleaseBundlesConfigModel;
import org.artifactory.api.rest.release.SourceReleaseBundleRequest;
import org.artifactory.api.rest.release.airgap.AirGapReleaseBundleExportStatusModel;
import org.artifactory.api.rest.release.airgap.ArtifactoryAirGapV2RequestModel;
import org.artifactory.bundle.BundleType;
import org.artifactory.entitlement.Entitlement;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.sapi.common.Lock;
import org.artifactory.util.UnsupportedByLicenseException;
import org.jfrog.model.api.releasebundle.ReleaseBundleOpenTransactionRequest;
import org.jfrog.security.util.Pair;

import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;

/**
 * @author Shay Bagants
 */
@EnterprisePlusAddon
@Entitlement(EntitlementType.RELEASE_BUNDLES_READ)
public interface ReleaseBundleAddon extends Addon {

    String ENTERPRISE_PLUS_MSG = "is only available on Enterprise Plus licensed Artifactory instances.";
    UnsupportedByLicenseException BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION = new UnsupportedByLicenseException(
            "Release bundle " + ENTERPRISE_PLUS_MSG);

    @NonNull
    default ReleaseBundleResult executeReleaseBundleRequest(@Nullable ReleaseBundleRequest bundleRequest,
            boolean includeMetaData, @NonNull String projectKey) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Verifying the Release Bundle jws signature
     *
     * @throws ReleaseBundleSignatureValidationException if no trusted key which can verify the signature is found
     */
    default void verifyReleaseBundleSignature(@NonNull String signature, @NonNull String keyID)
            throws ReleaseBundleSignatureValidationException {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default BundleTransactionResponse createBundleTransaction(
            @NonNull ReleaseBundleOpenTransactionRequest openTransactionRequest)
            throws IOException, ReleaseBundleSignatureValidationException, ParseException {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Lock
    default void closeBundleTransaction(String transactionId) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @NonNull
    default CloseTransactionStatusResponse closeBundleTransactionAsync(
            @NonNull String transactionPath,
            @Nullable Integer syncWaitTime,
            @NonNull List<Runnable> onSuccessListeners,
            boolean withRetries) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @NonNull
    default CloseTransactionStatusResponse checkCloseTransactionStatus(@NonNull String name, @NonNull String version) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @NonNull
    default CloseTransactionStatusResponse checkCloseTransactionStatus(@NonNull String transactionPath) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default BundlesResponse getAllBundles(BundleType bundleType) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default BundlesResponse getCompletedBundlesLastVersion(ReleaseBundleSearchFilter filter) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default BundlesNamesResponse getBundlesByReposAndPatterns(List<String> repos, List<String> includePatterns,
            List<String> excludePatterns, @Nullable BundleType bundleType) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default BundleVersionsResponse getBundleVersions(String bundleName, BundleType bundleType) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default BundleVersionsResponse getBundleVersions(ReleaseBundleSearchFilter filter) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default String getBundleJson(String bundleName, String bundleVersion, BundleType bundleType) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default String getBundleSignedJws(String bundleName, String bundleVersion, BundleType bundleType) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    void exportTo(ExportSettings exportSettings);

    @Lock
    void importFrom(ImportSettings importSettings);

    @Lock
    default void deleteAllBundles() {
    }

    @Lock
    default void deleteReleaseBundle(@NonNull String bundleName, @NonNull String bundleVersion,
            @NonNull BundleType type) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default ReleaseBundleModel getBundleModel(String bundleName, String bundleVersion, BundleType bundleType) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default boolean getTargetBundleKeep(@NonNull String bundleName, @NonNull String bundleVersion) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Sets the target release bundle as keep forever, this will protect the bundle from deletion and retention
     * operations
     *
     * @return true if the bundle was updated, false otherwise
     */
    default boolean setTargetBundleKeep(@NonNull String bundleName, @NonNull String bundleVersion, boolean keep) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * @return the source service id of a release bundle {bundleName}/{bundleVersion} and bundle type {bundleType}
     */
    @Nullable
    default String getBundleSourceServiceId(@NonNull String bundleName, @NonNull String bundleVersion,
            @NonNull BundleType bundleType) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default ReleaseBundlesConfigModel getReleaseBundlesConfig() {
        return null;
    }

    default void setReleaseBundlesConfig(ReleaseBundlesConfigModel releaseBundlesConfig) {
    }

    default ReleaseBundleArtifactsModel getListOfBundleNodes(@NonNull String bundleName,
            @NonNull String bundleVersion) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Stores a release bundle on source Artifactory for later distribution(s)
     *
     * @param sourceReleaseBundleRequest the requested bundle to store
     * @return the bundle prefix (including the release bundles repo key) and the {@link org.apache.http.HttpStatus}
     * returned status
     */
    default Pair<String, Integer> storeBundle(@NonNull SourceReleaseBundleRequest sourceReleaseBundleRequest,
            @NonNull String projectKey)
            throws IOException, ParseException, SQLException, ReleaseBundleSignatureValidationException {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default String getStoringRepo(String name, String version, BundleType bundleType) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default String getBundleStatus(String bundleName, String bundleVersion, BundleType bundleType) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default List<ReleaseArtifactInternalModel> getReleaseArtifactsUsingAql(String bundleName, String bundleVersion,
            BundleType bundleType) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Triggers the air gap export process for the provided bundle name+version
     *
     * @throws AirGapReleaseBundleException - in case there is some issue with the export (for example: bundle doesnt
     *                                      exits)
     */
    default void triggerAirGapReleaseBundleExport(@NonNull String bundleName, @NonNull String bundleVersion)
            throws AirGapReleaseBundleException {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Triggers the air gap export process for the release bundle v2
     *
     * @throws AirGapReleaseBundleException - in case there is some issue with the export (for example: bundle doesnt
     *                                      exits)
     */
    default void triggerAirGapReleaseBundleV2Export(
            @NonNull String rbV2Name,
            @NonNull String rbV2Version,
            @NonNull String storingRepository,
            @NonNull String projectKey,
            @NonNull ArtifactoryAirGapV2RequestModel airGapV2RequestModel)
            throws AirGapReleaseBundleException {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Runs the import process of an exported release bundle version.
     * The validation of the zip file and the archive processing is done synchronous
     *
     * @throws AirGapReleaseBundleException           - in case there is some issue with the import (for example: I/O
     *                                                issue or any repo related issues.)
     * @throws AirGapReleaseBundleValidationException - in case the validation of the metadata failed (for example:
     *                                                target repo is missing/ zip file is incorrect)
     */
    default void airGapReleaseBundleImport(@NonNull InputStream inputStream, boolean dryRun)
            throws AirGapReleaseBundleException, AirGapReleaseBundleValidationException {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Runs the import process of an exported release bundle version.
     * The validation of the zip file is done synchronous but the entries processing is done asynchronous
     *
     * @throws AirGapReleaseBundleException           - in case there is some issue with the import (for example: I/O
     *                                                issue or any repo related issues.)
     * @throws AirGapReleaseBundleValidationException - in case the validation of the metadata failed (for example:
     *                                                target repo is missing/ zip file is incorrect)
     */
    default void airGapReleaseBundleFileSystemImport(@NonNull File zipFile, boolean dryRun)
            throws AirGapReleaseBundleException, AirGapReleaseBundleValidationException {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Deletes the exported file that was created when the export release bundle was triggered.
     *
     * @throws AirGapReleaseBundleException - in case something went wrong
     */
    default boolean deleteReleaseBundleExportedFile(@NonNull String bundleName, @NonNull String bundleVersion)
            throws AirGapReleaseBundleException {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Deletes the exported file that was created when the export release bundle v2 was triggered.
     *
     * @throws AirGapReleaseBundleException - in case something went wrong
     */
    default boolean deleteReleaseBundleV2ExportedFile(
            @NonNull String rbV2Name,
            @NonNull String rbV2Version,
            @NonNull String storingRepository,
            @NonNull String projectKey) throws AirGapReleaseBundleException {

        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Checks the status of the export process.
     *
     * @return the status of the export process and the download url in case the export was completed successfully
     * @throws AirGapReleaseBundleException - in case something went wrong
     */
    @NonNull
    default AirGapReleaseBundleExportStatusModel getAirGapExportStatus(
            @NonNull String bundleName,
            @NonNull String bundleVersion,
            @NonNull String artifactoryUrl) throws AirGapReleaseBundleException {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Checks the status of the export v2 process.
     *
     * @return the status of the export process and the download url in case the export was completed successfully
     * @throws AirGapReleaseBundleException - in case something went wrong
     */
    @NonNull
    default AirGapReleaseBundleExportStatusModel getAirGapV2ExportStatus(
            @NonNull String rbV2Name,
            @NonNull String rbV2Version,
            @NonNull String storingRepository,
            @NonNull String projectKey,
            @NonNull String artifactoryUrl) throws AirGapReleaseBundleException {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    default boolean isArtifactPartOfTargetBundle(
            @NonNull String bundleName,
            @NonNull String bundleVersion,
            @NonNull String artifactPath) throws ReleaseBundleArtifactValidationException {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @NonNull
    default String getFatManifestContent(@NonNull String path, @NonNull String projectKey) {
        throw BUNDLE_UNSUPPORTED_OPERATION_EXCEPTION;
    }

}
