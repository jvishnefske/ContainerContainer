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

package org.artifactory.api.repo;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.artifactory.api.artifact.UnitInfo;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.descriptor.repo.RealRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.md.Properties;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.sapi.common.Lock;

import java.io.File;
import java.io.IOException;

/**
 * Provides artifacts deploy services.
 *
 * @author Yossi Shaul
 */
public interface DeployService {

    @Request
    void deploy(RepoDescriptor targetRepo, UnitInfo artifactInfo, File fileToDeploy, @Nullable Properties properties)
            throws RepoRejectException;

    // TODO[t.r] - delete method once usages are removed
    @Deprecated
    @Request
    void deploy(RepoDescriptor targetRepo, UnitInfo artifactInfo, File fileToDeploy, String pomString,
            boolean forceDeployPom, boolean partOfBundleDeploy, Properties properties) throws RepoRejectException;

    @Request
    void deploy(RepoConfig targetRepo, UnitInfo artifactInfo, File fileToDeploy, String pomString,
            boolean forceDeployPom, boolean partOfBundleDeploy, Properties properties) throws RepoRejectException;

    @Request
    void deployBundle(File bundle, RealRepoDescriptor targetRepo, BasicStatusHolder status, boolean failFast);

    @Request
    @Deprecated
    void deployBundle(File bundle, RealRepoDescriptor targetRepo, BasicStatusHolder status, boolean failFast,
            @Nonnull String prefix, Properties properties);

    @Request
    void deployBundle(File bundle, RealRepoConfig targetRepo, BasicStatusHolder status, boolean failFast,
            @Nonnull String prefix, Properties properties);

    /**
     * Wraps all bundle artifact deployments in a single transaction so they all become available at the same time.
     * This method is deprecated. Please use
     * {@link DeployService#deployBundle(File, RealRepoConfig, BasicStatusHolder, boolean, String, Properties)} instead
     */
    @Request
    @Lock
    @Deprecated
    void deployBundleAtomic(File bundle, RealRepoDescriptor targetRepo, BasicStatusHolder status, boolean failFast,
            @Nonnull String prefix, Properties properties);

    /**
     * Wraps all bundle artifact deployments in a single transaction so they all become available at the same time.
     * This method is deprecated. Please use
     * * {@link DeployService#deployBundleAtomic(File, RealRepoConfig, BasicStatusHolder, boolean, String, Properties)}
     * instead
     */
    @Request
    @Lock
    void deployBundleAtomic(File bundle, RealRepoConfig repoConfig, BasicStatusHolder status, boolean failFast,
            @Nonnull String prefix, Properties properties);

    void handleBuildUploadRedirect(
            @Nonnull ArtifactoryRequest request,
            @Nonnull ArtifactoryResponse response,
            @Nonnull String targetRepoKey) throws IOException;
}
