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

package org.artifactory.support.core.bundle;

import org.artifactory.api.download.FolderDownloadResult;
import org.artifactory.common.status.StatusHolder;
import org.jfrog.support.rest.model.SupportBundleConfig;
import org.jfrog.support.rest.model.manifest.NodeManifest;

import java.io.IOException;

/**
 * Defines bundle generator behaviour
 *
 * @author Michael Pasternak
 */
public interface SupportBundleService {
    String SUPPORT_BUNDLE_TIMESTAMP_PATTERN = "yyyyMMdd-HHmmss-SSS";

    /**
     * Generates Support bundle and manages the service manifest.
     * Propagates to other cluster nodes (mandated by {@param currentNodeOnly}
     *
     * @param artifactoryBaseUrl is required for setting a download url on the generated service manifest.
     * @return String since Artifactory's Jersey implementation has to be 2.x combined with Jackson 2.x, which it is not
     * so until we do and in order to deal with the common model we have to do it like this.
     */
    String generateSupportBundle(SupportBundleConfig bundleInfo, String artifactoryBaseUrl) throws IOException;

    /**
     * Generates Support bundle as part of a larger cluster request, will not change the service manifest and will
     * not propagate
     *
     * @return String since Artifactory's Jersey implementation has to be 2.x combined with Jackson 2.x, which it is not
     * so until we do and in order to deal with the common model we have to do it like this.
     */
    String generateClusterNodeSupportBundle(SupportBundleConfig bundleInfo) throws IOException;


    /**
     * @param artifactoryBaseUrl -
     */
    String getBundleInfo(NodeManifest nodeManifest, String artifactoryBaseUrl);

    /**
     * The ID returned here is the full path to the bundle (i.e. bundle-1562659975507/jfrt/jfrt@13373313371337)
     * (while on {@link #listBundles()} the id is the bundle id (i.e. bundle-1562659975507)
     *
     * @return list of bundles information with the relevant format for the ui
     */
    String uiListBundles();

    /**
     * @return list of bundles information
     */
    String listBundles();

    /**
     * Download the bundle
     *
     * @param bundleId    the bundle name
     * @param isUiRequest if True, bundle id is the full path to the bundle
     *                    (i.e. bundle-1562659975507/jfrt/jfrt@13373313371337)
     *                    Otherwise, its only the bundle id (i.e. bundle-1562659975507)
     * @return folder download od the build id
     */
    FolderDownloadResult downloadBundleArchive(String bundleId, boolean isUiRequest);

    /**
     * @param id          bundle id
     * @param isUiRequest if True, bundle id is the full path to the bundle
     *                    (i.e. bundle-1562659975507/jfrt/jfrt@13373313371337)
     *                    Otherwise, its only the bundle id (i.e. bundle-1562659975507)
     * @return deletes the entire bundles related to artifactory by id
     */
    StatusHolder deleteBundle(String id, boolean isUiRequest);

    /**
     * Deletes single bundle zip file (used for supporting old support bundle api)
     *
     * @param bundleName bundle zip file name
     */
    boolean deleteArchive(String bundleName);

    /**
     * Download the entire folder related to the zip file, by finding the relevant parent folder
     * Used be legacy resource.
     *
     * @param bundleName - single file name
     */
    FolderDownloadResult downloadByBundleName(String bundleName);

    /**
     * @param repoKey The repo key to check
     * @return True if the repo key represents the repo key of the support bundles repo
     */
    boolean isSupportBundlesRepo(String repoKey);

    /**
     * Cleanup old support bundles, retention count is defined by system property supportBundlesRetentionCount
     */
    void cleanup();

    /**
     * Delete all repo content
     */
    void deleteAll();
}
