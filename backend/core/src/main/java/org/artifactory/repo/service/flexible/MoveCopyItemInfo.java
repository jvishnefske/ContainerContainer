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

package org.artifactory.repo.service.flexible;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.fs.MutableVfsItem;

/**
 * @author gidis
 */
@Data
@AllArgsConstructor
public class MoveCopyItemInfo {
    private RepoPath sourceRepoPath;
    private RepoPath targetRepoPath;
    private VfsItem<ItemInfo> sourceItem;
    private VfsItem<ItemInfo> targetItem;
    private RepoRepoPath<LocalRepo<?, ?>> sourceRrp;
    private RepoRepoPath<LocalRepo<?, ?>> targetRrp;
    private MutableVfsItem<?> mutableTargetItem;
}
