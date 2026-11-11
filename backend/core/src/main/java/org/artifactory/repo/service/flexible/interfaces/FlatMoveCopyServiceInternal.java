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

package org.artifactory.repo.service.flexible.interfaces;

import lombok.NonNull;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.service.flexible.BulkProvider;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.sapi.common.Lock;

/**
 * @author gidis
 */
public interface FlatMoveCopyServiceInternal {

    @Lock
    void executeDeleteRootDir(@NonNull RepoRepoPath<LocalRepo<LocalRepoDescriptor, LocalRepoConfig>> rrp,
            MoveCopyContext context,
            MoveMultiStatusHolder status);

    @Lock
    MoveMultiStatusHolder executeMoveCopyOnBulk(BulkProvider bulkProvider, MoveCopyContext context);
}
