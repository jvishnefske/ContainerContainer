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

package org.artifactory.util;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.trash.TrashService;
import org.jfrog.client.util.PathUtils;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

import static org.artifactory.descriptor.repo.SupportBundleRepoDescriptor.SUPPORT_BUNDLE_REPO_NAME;

/**
 * Utility class for {@link org.artifactory.repo.RepoPath}.
 *
 * @author Yossi Shaul
 */
public class RepoPathUtils {

    /**
     * @param degree The degree of the ancestor (1 - parent, 2 - grandparent, etc)
     * @return Returns the n-th ancestor of this repo path. Null if doesn't exist.
     */
    @Nullable
    public static RepoPath getAncestor(RepoPath repoPath, int degree) {
        RepoPath result = repoPath.getParent();   // first ancestor
        for (int i = degree - 1; i > 0 && result != null; i--) {
            result = result.getParent();
        }
        return result;
    }

    public static boolean isAncestorOf(@Nonnull RepoPath ancestorRepoPath, @Nonnull RepoPath otherRepoPath) {
        if (otherRepoPath.getRepoKey().equals(ancestorRepoPath.getRepoKey())) {
            RepoPath otherPathParent = otherRepoPath.getParent();
            if (otherPathParent != null) {
                String ancestorPath = ancestorRepoPath.getPath();
                String parentPath = otherPathParent.getPath();
                if (StringUtils.isBlank(ancestorPath)) {
                    return true;
                }
                if (StringUtils.isNotBlank(parentPath)) {
                    parentPath = PathUtils.trimTrailingSlashes(parentPath);
                    ancestorPath = PathUtils.trimTrailingSlashes(ancestorPath);
                    if (StringUtils.isNotBlank(parentPath) && StringUtils.isNotBlank(ancestorPath) &&
                            parentPath.equals(ancestorPath)) {
                        return true;
                    } else {
                        return isAncestorOf(ancestorRepoPath, otherPathParent);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Creates repo path representing the root repository path (i.e. path is empty).
     *
     * @param repoKey The repository key
     * @return Repository root repo path
     */
    public static RepoPath repoRootPath(String repoKey) {
        return RepoPathFactory.create(repoKey, "");
    }

    /**
     * @param repoPath The repo path to check
     * @return True if the repo path represents a path in the trash can
     */
    public static boolean isTrash(RepoPath repoPath) {
        return repoPath != null && isTrash(repoPath.getRepoKey());
    }

    /**
     * @param repoKey The repo key to check
     * @return True if the repo key represents the repo key of the trash can
     */
    public static boolean isTrash(String repoKey) {
        return TrashService.TRASH_KEY.equals(repoKey);
    }

    /**
     * @param repoKey The repo key to check
     * @return True if the repo key represents the key of the support bundle repo
     */
    public static boolean isSupportBundle(String repoKey) {
        return SUPPORT_BUNDLE_REPO_NAME.equals(repoKey);
    }

    private RepoPathUtils() {
    }

    /**
     * @param repoKey The repo key to check
     * @return True if the repo key represents a remote-cache repo.
     */
    public static boolean isCache(String repoKey) {
        return StringUtils.endsWith(repoKey, LocalCacheRepoDescriptor.PATH_SUFFIX);
    }

    /**
     * @return a new repo path pointing to the same path of the input path that represents a folder path
     * (name ends with slash). Same repo path is returned if already a folder repo path.
     */
    public static RepoPath toFolderRepoPath(@Nonnull RepoPath repoPath) {
        Objects.requireNonNull(repoPath, "RepoPath cannot be null");
        if (repoPath.isFolder()) {
            return repoPath;
        }
        return RepoPathFactory.create(repoPath.getRepoKey(), repoPath.getPath() + "/");
    }

    /**
     * @return a new repo path pointing to the same path of the input path that represents a file path
     * (name doesn't end with slash). Same repo path is returned if already a file repo path.
     */
    public static RepoPath toFileRepoPath(@Nonnull RepoPath repoPath) {
        Objects.requireNonNull(repoPath, "RepoPath cannot be null");
        if (repoPath.isFile()) {
            return repoPath;
        }
        return RepoPathFactory.create(repoPath.getRepoKey(), repoPath.getPath());
    }

    /**
     * @return whether the child repo path is a subset of parent repo path
     */
    public static boolean isChild(@Nonnull RepoPath child, @Nonnull RepoPath parent) {
        return Paths.get(child.getPath()).startsWith(Paths.get(parent.getPath()));
    }

    public static String removeDashCache(String repoKey) {
        return StringUtils.removeEnd(repoKey, RepoPath.REMOTE_CACHE_SUFFIX);
    }

    public static String removeDashCacheFromPath(@Nonnull RepoPath repoPath) {
        String repoKey = repoPath.getRepoKey();
        String noDashCacheRepoKey = removeDashCache(repoKey);
        return noDashCacheRepoKey + "/" + repoPath.getPath();
    }

    public static String addDashCache(String repoKey) {
        return repoKey.concat(RepoPath.REMOTE_CACHE_SUFFIX);
    }

    /**
     * Restore an origin path from which a current file/folder was moved to trash. This is based on a rule
     * that for every path inside the Trashcan the first folder's name equals to the key of the "origin" repo.
     *
     * @param trashRepoPath a path to file/folder inside the Trashcan.
     * @return an origin path, <code>null</code> if Trashcan root repo path is specified.
     * @throws IllegalArgumentException if specified path does not belong to the Trashcan.
     */
    public static RepoPath getOriginRepoPathForTrash(@Nonnull RepoPath trashRepoPath) {
        if (!isTrash(trashRepoPath)) {
            throw new IllegalArgumentException("Specified path is not a Trashcan path");
        }
        String[] pathElements = PathUtils.getPathElements(trashRepoPath.toPath());
        if (pathElements.length >= 2) {
            return InternalRepoPathFactory.create(pathElements[1],
                    String.join("/", Arrays.stream(pathElements).toList().subList(2, pathElements.length)),
                    trashRepoPath.isFolder());
        }
        return null;
    }

    public static int getPathDeeps(@Nonnull RepoPath repoPath) {
        return PathUtils.getPathElements(repoPath.toPath()).length;
    }

    @Nonnull
    public static String addTrailingSlashIfNeeded(String path, String url) {
        StringBuilder targetArtifact = new StringBuilder(url);
        if (!url.endsWith("/")) {
            targetArtifact.append("/");
        }
        targetArtifact.append(path);
        return targetArtifact.toString();
    }
}
