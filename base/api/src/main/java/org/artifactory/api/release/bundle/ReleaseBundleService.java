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

package org.artifactory.api.release.bundle;

import lombok.NonNull;
import org.artifactory.api.component.ComponentDetails;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.api.rest.distribution.bundle.models.ReleaseBundleModel;
import org.artifactory.async.Async;
import org.artifactory.bundle.BundleNameAndRepo;
import org.artifactory.bundle.BundleTransactionStatus;
import org.artifactory.bundle.BundleType;
import org.artifactory.bundle.LightArtifactsBundle;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.common.Lock;
import org.jfrog.storage.StorageException;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * @author Rotem Kfir
 */
public interface ReleaseBundleService {
    /**
     * Find all Release Bundles with incomplete transaction that has been created over a predefined time period ago
     *
     * @return a list of Release Bundles ids
     * @throws StorageException In case a storage error occurred during the operation
     */
    @Nonnull
    List<Long> findExpiredBundlesIds(Long cleanupPeriod) throws StorageException;

    /**
     * Deletes a Release Bundle either with a closed or incomplete transaction by its id, along with its files, blob
     * and all its artifacts.
     * No permission check is being done before deletion.
     *
     * @param id the id by which to delete the Release Bundle
     * @return true if a Release Bundle was deleted, false if the Release Bundle was not found
     * @throws StorageException In case a storage error occurred during the operation
     */
    @Lock
    boolean deleteBundle(Long id) throws StorageException;

    /**
     * Deletes a Release Bundle either with a closed or incomplete transaction by its id, along with its files and blob
     *
     * @param artifactsBundleModel release-bundle to delete
     * @return true if a Release Bundle was deleted, false if the Release Bundle was not found
     * @throws StorageException In case a storage error occurred during the operation
     */
    boolean deleteBundle(ArtifactsBundleModel artifactsBundleModel) throws StorageException;

    /**
     * Deletes a Release Bundle either with a closed or incomplete transaction by its id, along with its files and blob
     *
     * @param bundleName release bundle name to delete
     * @param bundleVersion release bundle version to delete
     * @param type is the release bundle version type - either TARGET or SOURCE
     * throws NotFoundException In case release bundle version was not found
     * throws ForbiddenWebAppException In case it is forbidden to delete this release bundle version
     * throws StorageException In case a storage error occurred during the operation
     */
    void deleteReleaseBundle(@NonNull String bundleName, @NonNull String bundleVersion, @NonNull BundleType type);

    /**
     * Redistributes a Release Bundle with an incomplete transaction by its id
     *
     * @param id the id by which to find the Release Bundle
     * @return true if a Release Bundle was updated, false if the Release Bundle was not found or is not incomplete
     * @throws StorageException In case a storage error occurred during the operation
     */
    boolean redistributeBundle(Long id) throws StorageException;

    /**
     * Fetch top 5 Release Bundle details for the given repo path.
     * Results ordered by version to prevent getting Release Bundles with equal names.
     *
     * @param path the repo path to check (file or directory)
     * @return Collection of related Release Bundles
     * @throws StorageException In case a storage error occurred during the operation
     */
    List<LightArtifactsBundle> getRelatedBundleDetails(RepoPath path) throws StorageException;

    boolean isReleaseBundleExistByName(@NonNull String releaseBundleName);

    void deleteAllBundles();

    @Async(delayUntilAfterCommit = true, authenticateAsSystem = true, transactional = true)
    void copyBundleArtifactsAsync(ReleaseBundleModel releaseBundleModel, String storingRepo,
            Map<String, String> artifactMapping)
            throws SQLException;

    void createBundleNode(long artifactsBundleId, RepoPath targetRepoPath, ComponentDetails componentDetails)
            throws SQLException;

    void createBundleNodes(long artifactsBundleId, @NonNull List<RepoPath> repoPaths) throws SQLException;

    /**
     * Get previously calculated component details of a (possibly deleted) artifact
     *
     * @param repoPath - path of the (possibly non-existent) node
     * @return object containing details required for smart replication
     */
    ComponentDetails getOriginalComponentDetails(@NonNull RepoPath repoPath) throws SQLException;

    /**
     * @param bundleType - type of bundles to fetch, or `null` for all types
     * @return all release-bundles with {@link BundleTransactionStatus#COMPLETE} status.
     */
    List<BundleNameAndRepo> getAllCompletedBundles(@Nullable BundleType bundleType);

    String getDefaultStoringRepo();

    void setBundleStatusWithRetries(@NonNull String bundleName, @NonNull String bundleVersion,
            @NonNull BundleTransactionStatus status,
            @NonNull BundleType type);

    void cleanOrphanAndUnidentifiedArtifacts();

    boolean getTargetBundleKeep(@NonNull String bundleName, @NonNull String bundleVersion);

    boolean setTargetBundleKeep(@NonNull String bundleName, @NonNull String bundleVersion, boolean keep);

    @Nullable
    String getBundleSourceServiceId(@NonNull String bundleName, @NonNull String bundleVersion);

    void reportBundleLocallyDeletedFromTarget(@NonNull String storingRepo, @NonNull String releaseBundleName,
            @NonNull String releaseBundleVersion, @Nullable String sourceServiceId, @NonNull String signature);
}
