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

package org.artifactory.repo;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.remote.browse.RemoteItem;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.RequestContext;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.schedule.monitor.OnlineMonitorServiceImpl;

import java.io.IOException;
import java.util.List;

/**
 * @author Fred Simon
 */
public interface RemoteRepo<T extends RemoteRepoDescriptor, S extends RemoteRepoConfig>
        extends RealRepo<T, S> {
    long getRetrievalCachePeriodSecs();

    long getAssumedOfflinePeriodSecs();

    boolean isStoreArtifactsLocally();

    String getUrl();

    @Nullable
    LocalCacheRepo getLocalCacheRepo();

    /**
     * Downloads a resource from the remote repository
     *
     * @return A handle for the remote resource
     */
    ResourceStreamHandle downloadResource(String relPath) throws IOException;

    ResourceStreamHandle downloadResource(String relPath, RequestContext requestContext) throws IOException;

    /**
     * Retrieves a resource remotely if the remote resource was found and is newer or if forced
     */
    ResourceStreamHandle conditionalRetrieveResource(String relPath, boolean forceRemoteDownload) throws IOException;

    void clearCaches();

    /**
     * Removes a path from the repository caches (missed and failed)
     *
     * @param path           The path to remove from the cache. The path is relative path from the repository root.
     * @param removeSubPaths If true will also remove any sub paths from the caches.
     */
    void removeFromCaches(String path, boolean removeSubPaths);

    /**
     * Checks if the remote repository is currently offline.
     * <p>
     * This method determines if the repository is offline based on the following conditions:
     * 1. The repository configuration explicitly sets it to offline.
     * {@link org.artifactory.model.RemoteRepositoryRepoTypeConfig#isOffline()}
     * 2. The global configuration of the system is in offline mode. {@link CentralConfigDescriptorImpl#isOfflineMode()}
     * 3. The repository is assumed to be offline if the scheduler still thinks it is offline when trying to get it
     * back online. {@link RemoteRepoBase#isAssumedOffline()}
     *
     * @return true if the repository is offline, false otherwise.
     * @see OnlineMonitorServiceImpl
     */
    boolean isOffline();

    /**
     * Checks if the remote repository is configured to be offline.
     * <p>
     * This method determines if the repository is configured to be offline based on the following conditions:
     * 1. The repository configuration explicitly sets it to offline.
     * {@link org.artifactory.model.RemoteRepositoryRepoTypeConfig#isOffline()}
     * 2. The global configuration of the system is in offline mode.
     * {@link CentralConfigDescriptorImpl#isOfflineMode()}
     *
     * @return true if the repository is configured to be offline, false otherwise.
     */
    boolean isConfiguredOffline();

    /**
     * Performs the actual remote download of the artifact.
     * This method might creates new nodes and hence must be called within a transaction.
     *
     * @param requestContext The download request context
     * @param remoteResource A remote resource that has been returned by getInfo()
     * @throws IOException         On remote download failure
     * @throws RepoRejectException If the current repo is configured not to accept the artifact
     */
    ResourceStreamHandle downloadAndSave(InternalRequestContext requestContext, RepoResource remoteResource)
            throws IOException, RepoRejectException;

    /**
     * List remote resources from a remote path.
     *
     * @param directoryPath The path of the remote repository listing
     * @return A list of URLs that represent the remote hrefs of the remote resources. Empty if not found of failed
     * to parse the response.
     */
    @Nonnull
    List<RemoteItem> listRemoteResources(String directoryPath);

    /**
     * @return True if this repo supports listing remote directories AND it's not offline AND it's not blacklisted.
     */
    boolean isListRemoteFolderItems();

    boolean isSynchronizeProperties();

    /**
     * @return True if this repository is assumed to be offline due to download requests exceptions
     */
    boolean isAssumedOffline();

    /**
     * Manually reset the assumed offline flag to false (i.e., the repository is considered back online)
     */
    void resetAssumedOffline();

    /**
     * Manually reset the assumed offline flag to true (i.e., the repository is considered back offline)
     */
    void setAssumedOffline();

    /**
     * Cleanup any resources/threads this repository holds in order to be eligible for garbage collection
     */
    void cleanupResources();

    CloseableHttpClient getHttpClient();

    default RepoResource checkByCuration(RepoResource fileResource, RequestContext context) {
        return fileResource;
    }
}