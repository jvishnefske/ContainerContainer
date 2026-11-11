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

package org.artifactory.storage.fs.service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.artifactory.api.repo.storage.FolderSummaryInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.hqc.enums.HeavyQueryCacheType;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.RepoStorageSummary;
import org.artifactory.storage.fs.VfsException;
import org.artifactory.storage.fs.VfsItemNotFoundException;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.jfrog.storage.hqc.model.HeavyQueryCache;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author Yossi Shaul
 */
@SuppressWarnings("DuplicateThrows")
public interface FileService {

    boolean exists(RepoPath repoPath) throws VfsException;

    boolean exists(long nodeId);

    /**
     * Checks whether a repository is empty, i.e. has no content (files or folders) beyond its root folder.
     * The root folder itself is not considered content.
     *
     * @param repoKey the repository key to check
     * @return true if the repository has no content beyond its root folder, false otherwise
     */
    boolean isEmpty(String repoKey);

    @NonNull
    List<RepoPath> filterExistingRepoPaths(@NonNull List<RepoPath> repoPaths);

    @NonNull
    Set<String> findExistingRepoRootPaths(@NonNull Set<String> repoPaths);

    boolean existsBySha1(String sha1) throws VfsException;

    @Nonnull
    ItemInfo loadItem(RepoPath repoPath) throws VfsItemNotFoundException, VfsException;

    ItemInfo loadItem(long id);

    int getFileCount(String repoKey, String fileName);

    @HeavyQueryCache(
            hqcType = HeavyQueryCacheType.TOTAL_ARTIFACT_COUNT,
            minCallableDurationMillis = 0,
            expiryFactor = 500,
            expiryUpperBound = 3_600_000, // 1 hour
            expiryLowerBound = 5000, // 5 seconds
            threadLockPeriod = 0
    )
    int getFilesCount() throws VfsException;

    int getFilesCount(RepoPath repoPath) throws VfsException;

    FolderSummaryInfo getFilesCountAndSize(@Nonnull RepoPath repoPath) throws VfsException;

    int getNodesCount(RepoPath repoPath) throws VfsException;

    /**
     * This is legacy call, returns all children from current repo path
     */
    List<ItemInfo> loadChildren(RepoPath repoPath) throws VfsException;

    /**
     * Returns children from current repo path with a limit
     */
    List<ItemInfo> loadChildren(RepoPath repoPath, long limit) throws VfsException;

    /**
     * Returns all children from current repo path as stream
     */
    @Nonnull
    Stream<ItemInfo> getChildrenStream(RepoPath repoPath) throws VfsException;

    /**
     * Returns all children under path as stream ignoring the repo key
     */
    @Nonnull
    Stream<ItemInfo> getNodesStreamIgnoringRepo(RepoPath repoPath) throws VfsException;

    /**
     * Returns all descendants from repo path
     */
    List<ItemInfo> loadDescendants(RepoPath repoPath) throws VfsException;

    VfsItem loadVfsItem(StoringRepo storingRepo, RepoPath repoPath) throws VfsItemNotFoundException, VfsException;

    @NonNull
    Map<RepoPath, Optional<VfsItem<ItemInfo>>> loadVfsItems(@Nullable StoringRepo storingRepo,
            @NonNull List<RepoPath> repoPaths) throws VfsException;

    long createFolder(FolderInfo folder) throws VfsException;

    int updateFolder(long id, FolderInfo folder);

    @NonNull
    List<Long> createFiles(@NonNull List<FileInfo> files);

    long createFile(FileInfo file);

    int updateFile(long id, FileInfo file);

    boolean deleteItem(long id);

    void deleteItems(@NonNull List<Long> ids);

    /**
     * @param repoPath Repo path to check for children
     * @return True if the repo path exists and has children. False otherwise.
     */
    boolean hasChildren(RepoPath repoPath);

    Boolean isFolder(RepoPath repoPath);

    /**
     * Prints the hierarchy of the given repo path, including the full nodes data to the log.
     * Use for dev debugging purposes only.
     *
     * @param repoPath Root of the sub tree to print
     */
    @SuppressWarnings("unused")
    void debugNodeStructure(RepoPath repoPath);

    /**
     * Prints the nodes table to the console. For dev use only.
     */
    @SuppressWarnings("unused")
    void printNodesTable();

    long getNodeId(RepoPath repoPath);

    @NonNull
    List<Long> getNodeIds(@NonNull List<RepoPath> repoPaths) throws SQLException;

    List<FileInfo> searchFilesByProperty(String repo, String propKey, String propValue);

    /**
     * Search for all files with bad checksums of the given checksum type (SHA-1 or MD5)
     * in the given repositories
     *
     * @param type  The checksum type to search for, we support SHA-1 or MD5
     * @param repos The repositories to search in
     */
    List<FileInfo> searchFilesWithBadChecksum(ChecksumType type, Set<String> repos);

    boolean isRepoContainsFiles(String repoKey);

    long getFilesTotalSize(RepoPath repoPath);

    Set<RepoStorageSummary> getRepositoriesStorageSummary();

    List<ItemInfo> getOrphanItems(RepoPath repoPath);

    Boolean isFile(RepoPath repoPath);

    @NonNull
    Map<RepoPath, Boolean> areFiles(@NonNull Collection<RepoPath> repoPaths);

    boolean isFolderContainsArtifacts(RepoPath folderRepoPath, String... filesNames);

    ResultSet lockNodeForWrite(long nodeId) throws SQLException;

    /**
     * Check if the repo path has more than the given limit of items
     *
     * @return true if the repo path has more than the given limit of items, false otherwise.
     */
    boolean hasMoreThanLimitItems(@NonNull RepoPath repoPath, long limit);

    /**
     * Returns repo paths for all nodes in the given repository, up to the specified limit.
     * Used for fetching orphaned nodes during repository deletion cleanup.
     */
    List<RepoPath> getNodeRepoPaths(String repoKey, int limit);
}