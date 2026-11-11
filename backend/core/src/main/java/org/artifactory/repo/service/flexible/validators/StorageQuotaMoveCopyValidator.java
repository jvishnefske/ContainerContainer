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

package org.artifactory.repo.service.flexible.validators;

import org.apache.http.HttpStatus;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.service.flexible.MoveCopyItemInfo;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;

import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;

/**
 * @author Asaf Zalcman
 */
public class StorageQuotaMoveCopyValidator implements MoveCopyValidator {
    private final RepositoryService repositoryService;
    private final ProjectsService projectsService;

    public StorageQuotaMoveCopyValidator(RepositoryService repositoryService, ProjectsService projectsService) {
        this.repositoryService = repositoryService;
        this.projectsService = projectsService;
    }

    @Override
    public boolean validate(MoveCopyItemInfo element, MoveMultiStatusHolder status, MoveCopyContext context) {
        return !projectStorageQuotaDenied(status, element);
    }

    @Override
    public boolean isInterested(MoveCopyItemInfo itemInfo, MoveCopyContext context) {
        return true;
    }

    private boolean projectStorageQuotaDenied(MoveMultiStatusHolder status, MoveCopyItemInfo element) {
        if (shouldSkipStorageQuotaCheck(element)) {
            return false;
        }
        return willProjectQuotaExceed(status, element);
    }

    /**
     * Skip storage quota validation in the following cases:
     * 1. targetRepoKey is auto-trashcan
     * 2. the target project using unlimited storage
     * 3. target and source repo are identical (so we are in the same project)
     *
     * @param element - copy/move element
     * @return if storage quota validation should be skipped
     */
    private boolean shouldSkipStorageQuotaCheck(MoveCopyItemInfo element) {
        String targetRepoKey = element.getTargetRepoPath().getRepoKey();
        return TRASH_REPO_KEY.equals(targetRepoKey) ||
                projectsService.getProjectQuotaSizeByRepository(targetRepoKey) == ProjectsService.UNLIMITED_STORAGE ||
                targetRepoKey.equals(element.getSourceRepoPath().getRepoKey());
    }

    /**
     * Takes care of files only due to performance considerations. Folders will be considered with 0 size but will fail
     * move/copy by {@link ProjectsService#assertProjectStorageQuota(String, MutableStatusHolder, long)} method once
     * the quota is exceeded as the addition of 0 to the already exceeded size will met fail quota assertion.
     *
     * @return true if the calculated file/folder will exceed the quota limit
     */
    private boolean willProjectQuotaExceed(MoveMultiStatusHolder status, MoveCopyItemInfo element) {
        ItemInfo itemInfo = repositoryService.getItemInfo(element.getSourceRepoPath());
        long size = itemInfo.isFolder() ? 0 : ((FileInfo) itemInfo).getSize();
        projectsService.assertProjectStorageQuota(element.getTargetRepoPath().getRepoKey(), status, size);
        return status.hasErrors() && status.getLastError().getStatusCode() == HttpStatus.SC_REQUEST_TOO_LONG;
    }
}
