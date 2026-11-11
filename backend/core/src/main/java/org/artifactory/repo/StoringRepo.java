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

import lombok.NonNull;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.io.checksum.policy.ChecksumPolicy;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.local.PathDeletionContext;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.storage.fs.VfsItemFactory;

import java.io.IOException;
import java.util.List;

/**
 * @author Fred Simon
 */
public interface StoringRepo<T extends RepoDescriptor, S extends RepoConfig>
        extends Repo<T, S>, VfsItemFactory {

    @Override
    boolean itemExists(String relPath);

    @NonNull
    List<RepoPath> getExistingItems(@NonNull List<RepoPath> repoPaths);

    /**
     * Save the resource in the repository.
     */
    RepoResource saveResource(SaveResourceContext context) throws IOException, RepoRejectException;

    /**
     * Undeploy (delete) the path specified in the context
     *
     * @param ctx Context for deletion see {@link DeleteContext}
     */
    void undeploy(DeleteContext ctx);

    void undeploy(DeleteContext ctx, BasicStatusHolder statusHolder);

    boolean shouldProtectPathDeletion(PathDeletionContext pathDeletionContext);

    @Override
    ChecksumPolicy getChecksumPolicy();

    boolean isWriteLocked(RepoPath repoPath);

    @NonNull
    VfsItem<ItemInfo> getImmutableFsItem(@NonNull ItemInfo itemInfo);
}