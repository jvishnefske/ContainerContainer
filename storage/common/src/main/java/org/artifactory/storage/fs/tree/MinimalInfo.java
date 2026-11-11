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

package org.artifactory.storage.fs.tree;

import lombok.NonNull;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.fs.service.FileService;

import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author gidis
 */
public enum MinimalInfo {
    file, folder, notExist;

    public static MinimalInfo resolve(RepoPath repoPath) {
        FileService fileService = ContextHelper.get().beanForType(FileService.class);
        Boolean result = fileService.isFile(repoPath);
        return getInfo(result);
    }

    @NonNull
    public static Map<RepoPath, MinimalInfo> resolve(@NonNull Collection<RepoPath> repoPaths) {
        Map<RepoPath, MinimalInfo> infos = new HashMap<>();

        FileService fileService = ContextHelper.get().beanForType(FileService.class);
        Map<RepoPath, Boolean> repoPathBooleanMap = fileService.areFiles(repoPaths);
        repoPathBooleanMap.forEach(
                (repoPath, isFile) -> infos.put(repoPath, getInfo(isFile))
        );

        return infos;
    }

    @NonNull
    private static MinimalInfo getInfo(@Nullable Boolean isFile) {
        if (isFile == null) {
            return notExist;
        } else if (isFile) {
            return file;
        } else {
            return folder;
        }
    }


    public boolean isExist() {
        return this != notExist;
    }

    public boolean isFile() {
        return this == file;
    }

    public boolean isFolder() {
        return this == folder;
    }
}
