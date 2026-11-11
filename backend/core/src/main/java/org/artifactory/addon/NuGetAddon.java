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

package org.artifactory.addon;

import org.artifactory.activity.ActivityDetails;
import org.artifactory.async.Async;
import org.artifactory.async.WorkQueueConfig;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.addon.Addon;
import org.artifactory.common.repo.PackageType;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.common.Lock;
import org.artifactory.workqueue.WorkItem;


/**
 * Core NuGet functionality interface
 *
 * @author Noam Y. Tenne
 */
@ActivityDetails(PackageType.NUGET)
public interface NuGetAddon extends Addon {

    String API_KEY_HEADER = "X-NuGet-ApiKey";
    String NUGET_USER_AGENT = "nuget";
    String API_KEY_SEPARATOR = ":";
    String REPO_KEY_PARAM = "repoKey";
    String PATH_PARAM = "path";
    String PACKAGE_ID_PARAM = "packageId";
    String PACKAGE_VERSION_PARAM = "packageVersion";
    String PARTIAL_ID_PARAM = "partialId";
    String INCLUDE_PRERELEASE_PARAM = "includePrerelease";
    String NUGET_REINDEX_CALCULATION_WQ_NAME = "Nuget Reindex Calculation";
    String NUGET_SYMBOL_REINDEX_WQ_NAME = "Nuget Symbol Reindex Calculation";

    /**
     * Asynchronously extracts the spec from the nupkg and saves it as a binary property (along with ID, version and
     * digest)
     * for easier availability when searching
     *
     * @param fileInfo     NuGet package
     * @param statusHolder Status holder for logging
     * @param addToCache   Whether to add the package into the memory cache
     */
    @Async(transactional = true, delayUntilAfterCommit = true, authenticateAsSystem = true)
    void extractNuPkgInfo(FileInfo fileInfo, MutableStatusHolder statusHolder, boolean addToCache);

    default void extractNuPkgInfo(FileInfo fileInfo, boolean addToCache) {
    }

    @Async(workQueue = @WorkQueueConfig(name = NUGET_REINDEX_CALCULATION_WQ_NAME), delayUntilAfterCommit = true)
    default void extractNuPkgMetadata(WorkItem workItem) {
    }

    /**
     * Synchronously extracts the spec from the nupkg and saves it as a binary property (along with ID, version and
     * digest)
     * for easier availability when searching. Used mainly in batch extractions of multiple packages.
     *
     * <p><b>NOTE!</b> This method does NOT add the package into the memory cache, it is assumed
     * that the cache is getting populated in one go after extracting all of the repository packages.
     *
     * @param file         NuGet package
     * @param statusHolder Status holder for logging
     */
    @Lock
    default void extractNuPkgInfoSynchronously(FileInfo file, MutableStatusHolder statusHolder) {
    }

    /**
     * Adds a NuGet package to the NuGet packages memory cache (for local repositories only!).
     * <p><b>NOTE!</b> This method assumes the repository itself is already in the cache, see
     * {@link #afterRepoInit(String)}
     *
     * @param repoPath   The repo path of the NuGet package
     * @param properties The properties on the repo path, those with "nuget." prefix will get extracted
     */
    default void addNuPkgToRepoCache(RepoPath repoPath, Properties properties) {
    }

    @Async(delayUntilAfterCommit = true)
    void addNuPkgToRepoCacheAsync(RepoPath repoPath, Properties properties);

    /**
     * Removes a NuGet package from the local NuGet packages memory cache
     *
     * @param repoPath       The repository path
     * @param packageId      The NuGet package id
     * @param packageVersion The NuGet package version
     */
    default void removeNuPkgFromRepoCache(RepoPath repoPath, String packageId, String packageVersion) {
    }

    /**
     * Asynchronously adds a local NuGet repository into the NuGet packages memory cache if it's not there already
     *
     * @param repoKey The repository key to insert into the NuGet packages memory cache
     */
    @Async(authenticateAsSystem = true)
    void afterRepoInit(String repoKey);

    /**
     * Activate the NuGet packages indexing (re-extract packages nuspec and set as properties)
     * for the given local/cache/virtual repository. Each reindexing will be queued and processed asynchronously.
     *
     * @param repoKey The local/cache/virtual repository to activate NuGet packages reindex on
     */
    void requestAsyncReindexNuPkgs(String repoKey);

    //todo consider moving into another common interface of both ha-addon and nuget-addon
    default void internalAddNuPkgToRepoCache(RepoPath repoPath, Properties properties) {
    }

    default void internalRemoveNuPkgFromRepoCache(RepoPath repoPath, String packageId, String packageVersion) {
    }

    /**
     * Asynchronously extracts the symbol files (.pdb files) from the snupkg and saves them in a dedicated symbols dir
     *
     * @param fileInfo     SNuGet package
     * @param statusHolder Status holder for logging
     */
    @Async(transactional = true, delayUntilAfterCommit = true, authenticateAsSystem = true)
    void extractSNuPkgInfo(FileInfo fileInfo, MutableStatusHolder statusHolder);

    default void extractSNuPkgInfo(FileInfo info) {
    }

    @Async(workQueue = @WorkQueueConfig(name = NUGET_SYMBOL_REINDEX_WQ_NAME), delayUntilAfterCommit = true)
    default void extractSNuPkgMetadata(WorkItem workItem) {
    }

    /**
     * Activates the SNuGet packages indexing (re-extract and caching symbol files from snupkg package)
     * for the given local/cache/virtual repository. Each reindexing will be queued and processed asynchronously.
     *
     * @param repoKey The local/cache/virtual repository to activate NuGet packages reindex on
     */
    void requestAsyncReindexSNuPkgs(String repoKey);

    /**
     * Synchronously extracts the symbol files (.pdb files) from the snupkg and saves them in a dedicated symbols dir
     *
     * @param file         SNuGet package
     * @param statusHolder Status holder for logging
     */
    @Lock
    default void extractSNuPkgInfoSynchronously(FileInfo file, MutableStatusHolder statusHolder) {
    }


    default void removeSymbolsFromDirectory(FileInfo info) {
    }

    @Async(workQueue = @WorkQueueConfig(name = NUGET_SYMBOL_REINDEX_WQ_NAME), delayUntilAfterCommit = true)
    default void removeSymbolsFromDirectory(WorkItem workItem) {
    }
}
