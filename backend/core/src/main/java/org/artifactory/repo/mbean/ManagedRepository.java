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

package org.artifactory.repo.mbean;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.storage.fs.service.FileService;

/**
 * An MBean to expose repository data.
 *
 * @author Yossi Shaul
 */
public class ManagedRepository implements ManagedRepositoryMBean {
    private final String repoKey;
    private final FileService fileService;
    private final ArtifactoryContext context;

    public ManagedRepository(String repoKey) {
        this.repoKey = repoKey;
        // Get context during construction when it IS still bound to the execution thread
        this.context = ContextHelper.get();
        this.fileService = context.beanForType(FileService.class);
    }

    @Override
    public String getRepositoryKey() {
        return repoKey;
    }

    @Override
    public long getArtifactsCount() {
        return ArtifactoryContextUtils.supplyWithContext(
                () -> fileService.getFilesCount(new RepoPathImpl(repoKey, "")),
                context, "Failed to get artifacts count");
    }

    @Override
    public long getArtifactsTotalSize() {
        return ArtifactoryContextUtils.supplyWithContext(
                () -> fileService.getFilesTotalSize(new RepoPathImpl(repoKey, "")),
                context, "Failed to get artifacts total size");
    }
}
