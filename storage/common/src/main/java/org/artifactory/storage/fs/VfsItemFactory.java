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

package org.artifactory.storage.fs;

import lombok.NonNull;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.io.checksum.policy.ChecksumPolicy;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author freds
 */
//TORE: [by YS] move some methods to other interfaces and rename this interface
public interface VfsItemFactory {
    String getKey();

    boolean isLocal();

    boolean isReal();

    boolean isCache();

    ModuleInfo getItemModuleInfo(String relativePath);

    ChecksumPolicy getChecksumPolicy();

    boolean itemExists(String subPath);

    @NonNull
    MutableVfsFile createOrGetFile(RepoPath repoPath);

    @Nullable
    VfsItem getImmutableFsItem(RepoPath repoPath);

    @NonNull
    Map<RepoPath, Optional<VfsItem<ItemInfo>>> getImmutableFsItems(@NonNull List<RepoPath> repoPaths);

    @Nullable
    MutableVfsFile getMutableFile(RepoPath repoPath);

    @Nullable
    MutableVfsItem getMutableFsItem(RepoPath repoPath);

    @Nullable
    MutableVfsItem<MutableItemInfo> getMutableFsItem(VfsItem<ItemInfo> vfsItem, ItemInfo originalItemInfo,
            Properties originalProperties);

    @Nullable
    VfsFile getImmutableFile(RepoPath repoPath);

    @NonNull
    MutableVfsFolder createOrGetFolder(RepoPath repoPath);

    @Nullable
    VfsFolder getImmutableFolder(RepoPath repoPath);

    @Nullable
    MutableVfsFolder getMutableFolder(RepoPath repoPath);
}
