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

package org.artifactory.storage.db.fs.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.FolderExpectedException;
import org.artifactory.api.repo.storage.FolderSummaryInfo;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.SQLIntegrityException;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.model.fs.FolderInfoImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.RepoStorageSummary;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.entity.FolderSummaryNodeInfo;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.fs.entity.NodeBuilder;
import org.artifactory.storage.db.fs.entity.NodePath;
import org.artifactory.storage.db.fs.model.DbFsFile;
import org.artifactory.storage.db.fs.model.DbFsFolder;
import org.artifactory.storage.fs.VfsException;
import org.artifactory.storage.fs.VfsItemNotFoundException;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.artifactory.storage.fs.service.ConditionalInsertContext;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.threadlocal.cache.ReposItemsThreadLocalCacheService;
import org.artifactory.util.PathValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.repoItemsCacheQueryLimit;

/**
 * @author Yossi Shaul
 */
@Slf4j
@SuppressWarnings("DuplicateThrows")
@Service
public class FileServiceImpl implements FileService {

    private static final String CACHE_KEY_PREFIX = "loadSingleNode:";
    @Autowired
    private InternalDbService dbService;
    @Setter
    @Autowired
    private NodesDao nodesDao;

    @Override
    public boolean exists(RepoPath repoPath) throws VfsException {
        try {
            NodePath nodePath = NodePath.fromRepoPath(repoPath);
            String key = CACHE_KEY_PREFIX + repoPath.getPath();
            return ReposItemsThreadLocalCacheService.exists(key, repoPath.getRepoKey(),
                    limit -> nodesDao.getNodesByPathAndName(nodePath, limit), getFscQueryEntriesLimit(),
                    () -> nodesDao.exists(nodePath));
        } catch (SQLException e) {
            log.debug("Failed existence check of path '{}", repoPath, e);
            throw new VfsException("Failed existence check of path '" + repoPath + "'", e);
        }
    }

    @Override
    public boolean exists(long nodeId) {
        try {
            return nodesDao.exists(nodeId);
        } catch (SQLException e) {
            log.debug("Failed to check existence of node '{}'", nodeId, e);
            throw new VfsException(String.format("Failed to check existence of node: %d", nodeId), e);
        }
    }

    @Override
    public boolean isEmpty(String repoKey) {
        try {
            return nodesDao.isEmptyRepo(repoKey);
        } catch (SQLException e) {
            throw new VfsException("Failed to check if repo: '%s' is empty".formatted(repoKey), e);
        }
    }

    @NonNull
    public List<RepoPath> filterExistingRepoPaths(@NonNull List<RepoPath> repoPaths) {
        try {
            return nodesDao.getExistingItems(NodePath.fromRepoPaths(repoPaths));
        } catch (SQLException e) {
            throw new VfsException("Failed existence check of multiple paths", e);
        }
    }

    @Override
    public boolean existsBySha1(String sha1) throws VfsException {
        try {
            return nodesDao.existsBySha1(sha1);
        } catch (SQLException e) {
            log.debug("Failed existence check by hash '{}", sha1, e);
            throw new VfsException("Failed existence check by hash '" + sha1 + "'", e);
        }
    }

    @Nonnull
    @Override
    public ItemInfo loadItem(RepoPath repoPath) throws VfsItemNotFoundException, VfsException {
        Node node = loadNode(repoPath);
        return itemInfoFromNode(node);
    }

    @Override
    public ItemInfo loadItem(long id) {
        Node node = loadNode(id);
        return itemInfoFromNode(node);
    }

    @Override
    public VfsItem loadVfsItem(StoringRepo storingRepo, RepoPath repoPath) throws VfsItemNotFoundException,
            VfsException {
        Node node = loadNode(repoPath);
        return fsItemFromNode(storingRepo, node);
    }

    @Override
    public @NonNull Map<RepoPath, Optional<VfsItem<ItemInfo>>> loadVfsItems(
            @Nullable StoringRepo storingRepo, @NonNull List<RepoPath> repoPaths) throws VfsException {

        Map<RepoPath, Node> nodes = loadNodes(repoPaths);
        return nodes.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            VfsItem<?> fsItemFromNode = fsItemFromNode(storingRepo, entry.getValue());
                            return Optional.ofNullable((VfsItem<ItemInfo>) fsItemFromNode);
                        })
                );
    }

    @Override
    public long createFolder(FolderInfo folder) throws VfsException {
        PathValidator.validate(folder.getRepoPath().toPath());
        //TODO: [by YS] verify parent exists?
        long nodeId = dbService.nextId();
        if (ConditionalInsertContext.isEnabled()) {
            return createNodeIfNotExists(folder.getRepoPath(), "folder",
                    id -> folderInfoToNode(id, folder));
        } else {
            return createFolderNode(folder, nodeId);
        }
    }

    private long createFolderNode(@NonNull FolderInfo folder, long nodeId) {
        Node node = folderInfoToNode(nodeId, folder);
        int updateCount;
        try {
            updateCount = nodesDao.create(node);
        } catch (SQLIntegrityConstraintViolationException e) {
            nodeId = getNodeId(folder.getRepoPath());
            if (DbService.NO_DB_ID == nodeId) {
                throw new SQLIntegrityException("Couldn't find folder after SQL integrity exception for path" +
                        folder.getRepoPath(), e);
            }
            return nodeId;
        } catch (SQLException e) {
            throw new VfsException(e);
        }
        if (updateCount != 1) {
            // Create new node should return with exactly 1 record updated
            throw new IllegalStateException("Unexpected update count when creating new node: '" +
                    node.getNodePath() + "'");
        }
        return nodeId;
    }

    private long createFileNode(@NonNull FileInfo file, long nodeId) {
        Node node = fileInfoToNode(nodeId, file);
        int updateCount;
        try {
            updateCount = nodesDao.create(node);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new SQLIntegrityException("Failed to create file, the file already exists", e);
        } catch (SQLException e) {
            throw new VfsException(e);
        }
        if (updateCount != 1) {
            // Create new node should return with exactly 1 record updated
            throw new IllegalStateException("Unexpected update count when creating new node: '" +
                    node.getNodePath() + "'");
        }
        return nodeId;
    }

    @Override
    public int updateFolder(long id, FolderInfo folder) {
        log.debug("Updating folder: {}", folder.getRepoPath());
        try {
            return nodesDao.update(folderInfoToNode(id, folder));
        } catch (SQLException e) {
            throw new VfsException("Failed to update folder: '" + folder.getRepoPath() + "' id: '" + id + "': " +
                    e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public List<Long> createFiles(@NonNull List<FileInfo> files) {
        List<Node> filesToCreate = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        files.forEach(file -> {
            PathValidator.validate(file.getRepoPath().toPath());
            long nodeId = dbService.nextId();
            Node node = fileInfoToNode(nodeId, file);

            filesToCreate.add(node);
            ids.add(nodeId);
        });

        try {
            nodesDao.createBulk(filesToCreate);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new SQLIntegrityException("Failed to create files, one of the files already exists", e);
        } catch (SQLException e) {
            throw new VfsException(e);
        }

        return ids;
    }

    @Override
    public long createFile(@NonNull FileInfo file) {
        PathValidator.validate(file.getRepoPath().toPath());
        //TODO: [by YS] verify parent exists?
        log.debug("Creating file {}", file.getRepoPath());
        long nodeId = dbService.nextId();
        if (ConditionalInsertContext.isEnabled()) {
            return createNodeIfNotExists(file.getRepoPath(), "file",
                    id -> fileInfoToNode(id, file));
        } else {
            return createFileNode(file, nodeId);
        }
    }

    private long createNodeIfNotExists(@NonNull RepoPath repoPath,
            String itemKind, LongFunction<Node> nodeForNewId) {
        PathValidator.validate(repoPath.toPath());
        log.debug("Creating {} (idempotent) {}", itemKind, repoPath);
        long nodeId = dbService.nextId();
        Node node = nodeForNewId.apply(nodeId);
        int updateCount;
        try {
            updateCount = nodesDao.createIfNotExists(node);
        } catch (SQLException e) {
            throw new VfsException(e);
        }
        if (updateCount == 0) {
            ConditionalInsertContext.setSkipped(true);
            return getExistingNodeId(repoPath);
        }
        if (updateCount != 1) {
            throw new IllegalStateException(
                    "Unexpected update count " + updateCount
                            + " when creating " + itemKind + ": '"
                            + repoPath + "'");
        }
        return nodeId;
    }

    @Override
    public int updateFile(long id, FileInfo file) {
        log.debug("Updating file: {}", file.getRepoPath());
        try {
            return nodesDao.update(fileInfoToNode(id, file));
        } catch (SQLException e) {
            throw new VfsException("Failed to update file: '" + file.getRepoPath() + "' id: '" + id + "': " +
                    e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteItem(long id) {
        try {
            return nodesDao.delete(id);
        } catch (SQLException e) {
            throw new VfsException("Failed to delete item with id '" + id + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteItems(@NonNull List<Long> ids) {
        try {
            nodesDao.deleteBulk(ids);
        } catch (SQLException e) {
            throw new VfsException("Failed to delete items:'" + ids + "': " + e.getMessage(), e);
        }
    }

    @Override
    public boolean hasChildren(RepoPath repoPath) {
        try {
            return nodesDao.hasChildren(NodePath.fromRepoPath(repoPath));
        } catch (SQLException e) {
            throw new VfsException("Failed to check for children of: '" + repoPath + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void printNodesTable() {
        if (!ConstantValues.dev.getBoolean()) {
            return;
        }
        try {
            List<? extends Node> nodes = nodesDao.getAllNodes();
            StringBuilder sb = new StringBuilder(String.format(
                    "%-4s%-4s %-16s%-44s%-16s%n", "ID", "Depth", "Repo", "Path", "Name"));
            for (Node n : nodes) {
                sb.append(String.format("%-4s%4s  %-16s%-44s%-16s%n",
                        n.getNodeId(), n.getRepo(), n.getPath(), n.getName(), n.getDepth()));

            }
            log.info("Dumping Checksum Paths Table ({} records):\n {}", nodes.size(), sb);
        } catch (SQLException e) {
            log.error("Failed to print nodes table", e);
        }
    }

    @Override
    public long getNodeId(RepoPath repoPath) {
        //TODO: [by YS] requires caching
        try {
            return nodesDao.getNodeId(NodePath.fromRepoPath(repoPath));
        } catch (SQLException e) {
            throw new VfsException("Couldn't get node id for: " + repoPath, e);
        }
    }

    /**
     * Called when {@code createIfNotExists} returns 0 (duplicate).
     * Looks up the existing node ID at the given path.
     */
    private long getExistingNodeId(@NonNull RepoPath repoPath) {
        long nodeId = getNodeId(repoPath);
        if (DbService.NO_DB_ID == nodeId) {
            throw new VfsException(
                    "Node not found after conditional insert returned 0 for: " + repoPath);
        }
        log.debug("Node already exists at {}, returning existing nodeId {}", repoPath, nodeId);
        return nodeId;
    }

    @Override
    @NonNull
    public List<Long> getNodeIds(@NonNull List<RepoPath> repoPaths) throws SQLException {
        Map<NodePath, Long> nodesIds = nodesDao.getNodesIds(NodePath.fromRepoPaths(repoPaths));
        return repoPaths.stream().map(path -> nodesIds.get(NodePath.fromRepoPath(path))).collect(Collectors.toList());
    }

    @NonNull
    private Node loadNode(RepoPath repoPath) throws VfsItemNotFoundException, VfsException {
        try {
            NodePath nodePath = NodePath.fromRepoPath(repoPath);
            String key = CACHE_KEY_PREFIX + repoPath.getPath();
            Node node = ReposItemsThreadLocalCacheService.get(key, repoPath.getRepoKey(),
                    limit -> nodesDao.getNodesByPathAndName(nodePath, limit), getFscQueryEntriesLimit(),
                    () -> Optional.ofNullable(nodesDao.get(nodePath)).map(Collections::singletonList)
                            .orElse(Collections.emptyList()));
            if (node == null) {
                throw new VfsItemNotFoundException("Item not found: '" + repoPath + "'");
            }
            return node;
        } catch (SQLException e) {
            throw new VfsException("Failed to load item '" + repoPath + "'", e);
        }
    }

    @NonNull
    private Map<RepoPath, Node> loadNodes(@NonNull List<RepoPath> repoPaths) throws VfsException {
        try {
            Map<RepoPath, Node> map = new HashMap<>();

            List<NodePath> nodePaths = repoPaths.stream()
                    .map(repoPath -> {
                        NodePath nodePath = NodePath.fromRepoPath(repoPath);
                        map.put(repoPath, null);
                        return nodePath;
                    })
                    .collect(Collectors.toList());

            Map<NodePath, Node> nodePathNodeMap = nodesDao.get(nodePaths);
            nodePathNodeMap.forEach((key, value) -> map.put(key.toRepoPath(), value));

            return map;

        } catch (SQLException e) {
            throw new VfsException("Failed to load items", e);
        }
    }

    private Node loadNode(long id) throws VfsItemNotFoundException, VfsException {
        try {
            Node node = nodesDao.get(id);
            if (node == null) {
                throw new VfsItemNotFoundException("Item not found for id '" + id + "'");
            }
            return node;
        } catch (SQLException e) {
            throw new VfsException("Failed to load item with id '" + id + "'", e);
        }
    }

    @Override
    @Nonnull
    public List<ItemInfo> loadChildren(RepoPath repoPath) throws VfsException {
        //TODO: [by YS] consider always sorting by name to help prevent locking errors later
        return loadChildren(repoPath, -1);
    }

    @Override
    @Nonnull
    public List<ItemInfo> loadChildren(RepoPath repoPath, long limit) throws VfsException {
        try {
            List<Node> childrenNode = nodesDao.getChildren(NodePath.fromRepoPath(repoPath), limit);
            List<ItemInfo> children = Lists.newArrayList();
            for (Node child : childrenNode) {
                children.add(itemInfoFromNode(child));
            }
            return children;
        } catch (SQLException e) {
            throw new VfsException("Failed to load children for node '" + repoPath + "'", e);
        }
    }

    @Override
    @Nonnull
    public Stream<ItemInfo> getChildrenStream(RepoPath repoPath) throws VfsException {
        try {
            return nodesDao.getChildrenStream(NodePath.fromRepoPath(repoPath)).map(this::itemInfoFromNode);
        } catch (Exception e) {
            throw new VfsException("Failed to load children for node '" + repoPath + "'", e);
        }
    }

    @Override
    @Nonnull
    public Stream<ItemInfo> getNodesStreamIgnoringRepo(RepoPath repoPath) throws VfsException {
        try {
            return nodesDao.getNodesStreamIgnoringRepo(NodePath.fromRepoPath(repoPath)).map(this::itemInfoFromNode);
        } catch (Exception e) {
            throw new VfsException("Failed to load children for node '" + repoPath.getPath() + "'", e);
        }
    }

    @Override
    public List<ItemInfo> loadDescendants(RepoPath repoPath) throws VfsException {
        try {
            List<Node> descendantNode = nodesDao.getDescendants(NodePath.fromRepoPath(repoPath));
            List<ItemInfo> descendants = Lists.newArrayList();
            for (Node descendant : descendantNode) {
                descendants.add(itemInfoFromNode(descendant));
            }
            return descendants;
        } catch (SQLException e) {
            throw new VfsException("Failed to load descendants for node '" + repoPath + "'", e);
        }
    }

    @Override
    public int getFileCount(String repoKey, String fileName) {
        try {
            return nodesDao.getFileCount(repoKey, fileName);
        } catch (SQLException e) {
            throw new VfsException(e);
        }
    }

    @Override
    public int getFilesCount() throws VfsException {
        try {
            return nodesDao.getFilesCount();
        } catch (SQLException e) {
            throw new VfsException(e);
        }
    }

    @Override
    public FolderSummaryInfo getFilesCountAndSize(@Nonnull RepoPath repoPath) throws VfsException {
        FolderSummaryNodeInfo filesCountAndSize;
        try {
            filesCountAndSize = nodesDao.getFilesCountAndSize(NodePath.fromRepoPath(repoPath));
            return new FolderSummaryInfoImpl(filesCountAndSize.getFileCount(),
                    filesCountAndSize.getFolderSize());
        } catch (SQLException e) {
            throw new VfsException(e);
        }
    }

    @Override
    public int getFilesCount(RepoPath repoPath) throws VfsException {
        try {
            if (repoPath.isRoot()) {
                return nodesDao.getFilesCount(repoPath.getRepoKey());
            } else {
                return nodesDao.getFilesCount(NodePath.fromRepoPath(repoPath));
            }
        } catch (SQLException e) {
            throw new VfsException(e);
        }
    }

    @Override
    public long getFilesTotalSize(RepoPath repoPath) {
        try {
            if (repoPath.isRoot()) {
                return nodesDao.getFilesTotalSize(repoPath.getRepoKey());
            } else {
                return nodesDao.getFilesTotalSize(NodePath.fromRepoPath(repoPath));
            }
        } catch (SQLException e) {
            throw new VfsException(e);
        }
    }

    @Override
    public int getNodesCount(RepoPath repoPath) throws VfsException {
        try {
            if (repoPath.isRoot()) {
                return nodesDao.getNodesCount(repoPath.getRepoKey());
            } else {
                return nodesDao.getNodesCount(NodePath.fromRepoPath(repoPath));
            }
        } catch (SQLException e) {
            throw new VfsException(e);
        }
    }

    @Override
    public boolean isRepoContainsFiles(String repoKey) throws VfsException {
        try {
            return nodesDao.isRepoContainsFiles(repoKey);
        } catch (SQLException e) {
            throw new VfsException(e);
        }
    }

    @Override
    public List<FileInfo> searchFilesByProperty(String repo, String propKey, String propValue) {
        try {
            List<Node> childrenNode = nodesDao.searchNodesByProperty(repo, propKey);
            List<FileInfo> children = Lists.newArrayList();
            for (Node child : childrenNode) {
                if (child.isFile()) {
                    children.add(fileInfoFromNode(child));
                }
            }
            return children;
        } catch (SQLException e) {
            throw new VfsException("Search by properties failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<FileInfo> searchFilesWithBadChecksum(ChecksumType type, Set<String> repos) {
        List<Node> childrenNode = nodesDao.searchBadChecksums(type, repos);
        return childrenNode.stream()
                .map(this::fileInfoFromNode)
                .toList();
    }

    @Override
    public Set<RepoStorageSummary> getRepositoriesStorageSummary() {
        try {
            return nodesDao.getRepositoriesStorageSummary();
        } catch (SQLException e) {
            throw new VfsException("Repository storage summary failed with exception: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ItemInfo> getOrphanItems(RepoPath repoPath) {
        try {
            List<Node> orphanNodes = nodesDao.getOrphanNodes(NodePath.fromRepoPath(repoPath));
            List<ItemInfo> orphanItems = Lists.newArrayList();
            for (Node orphanNode : orphanNodes) {
                orphanItems.add(itemInfoFromNode(orphanNode));
            }
            return orphanItems;
        } catch (SQLException e) {
            throw new VfsException("Failed to find orphan nodes under: '" + repoPath + "': " + e.getMessage(), e);
        }
    }

    @Override
    public Boolean isFile(RepoPath repoPath) {
        try {
            Integer itemType = nodesDao.getItemType(NodePath.fromRepoPath(repoPath));
            if (itemType != null) {
                return itemType == 1;
            }
        } catch (SQLException e) {
            throw new VfsException("Failed to check item type for node: '" + repoPath + "': " + e + e.getMessage(), e);
        }
        return null;
    }

    @Override
    @NonNull
    public Set<String> findExistingRepoRootPaths(@NonNull Set<String> repoPaths) {
        try {
            return nodesDao.findExistsRepoRootPaths(repoPaths);
        } catch (SQLException e) {
            log.debug("Failed existence check of paths", e);
            throw new VfsException("Failed existence check of paths", e);
        }
    }

    @Override
    @NonNull
    public Map<RepoPath, Boolean> areFiles(@NonNull Collection<RepoPath> repoPaths) {
        try {
            return nodesDao.areFiles(repoPaths);
        } catch (SQLException e) {
            throw new VfsException("Failed to check item type for multiple nodes " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isFolderContainsArtifacts(RepoPath folderRepoPath, String... params) {
        try {
            return nodesDao.getFolderArtifactsCount(folderRepoPath, List.of(params)) > 0;
        } catch (SQLException e) {
            throw new VfsException(
                    "Failed to check if the folder: " + folderRepoPath.toPath() + " is docker manifest folder." +
                            e.getMessage(), e);
        }
    }

    @Override
    public ResultSet lockNodeForWrite(long nodeId) throws SQLException {
        return nodesDao.lockNodeForWrite(nodeId);
    }

    @Override
    public boolean hasMoreThanLimitItems(@NonNull RepoPath repoPath, long limit) {
        try {
            NodePath nodePath = NodePath.fromRepoPath(repoPath);
            return nodesDao.getNumberOfItemsForPathWithLimit(nodePath, limit + 1) > limit;
        } catch (SQLException e) {
            throw new VfsException(
                    "Failed to check if the folder: " + repoPath.toPath() + " is docker manifest folder." +
                            e.getMessage(), e);
        }
    }

    @Override
    public Boolean isFolder(RepoPath repoPath) {
        try {
            Integer itemType = nodesDao.getItemType(NodePath.fromRepoPath(repoPath));
            if (itemType != null) {
                return itemType == 0;
            }
        } catch (SQLException e) {
            throw new VfsException("Failed to check item type for node: '" + repoPath + "': " + e + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public void debugNodeStructure(RepoPath repoPath) {
        log.info("************************ Tree structure dump ************************");
        debugNodeStructure(loadItem(repoPath), 0);
        log.info("*********************************************************************");
    }


    private void debugNodeStructure(ItemInfo item, int depth) {
        StringBuilder itemString = new StringBuilder(depth * 2);
        itemString.append("  ".repeat(Math.max(0, depth)));
        itemString.append(item.getRepoKey()).append("/").append(item.getRelPath())
                .append(" (").append(item).append(") ");
        log.info("{}", itemString);
        if (item.isFolder()) {
            for (ItemInfo child : loadChildren(item.getRepoPath())) {
                debugNodeStructure(child, depth + 1);
            }
        }
    }

    private Node folderInfoToNode(long nodeId, FolderInfo folder) {
        return itemNodeBuilder(nodeId, folder).file(false).build();
    }

    private Node fileInfoToNode(long nodeId, FileInfo file) {
        NodeBuilder builder = itemNodeBuilder(nodeId, file).file(true);

        builder.length(file.getSize());

        ChecksumsInfo checksums = file.getChecksumsInfo();
        ChecksumInfo sha1Checksums = checksums.getChecksumInfo(ChecksumType.sha1);
        if (sha1Checksums == null || StringUtils.isBlank(sha1Checksums.getActual())) {
            throw new VfsException("Cannot persist file '" + file.getRepoPath() + "' without sha1 checksum");
        }
        builder.sha1Actual(sha1Checksums.getActual()).sha1Original(sha1Checksums.getOriginalOrNoOrig());
        ChecksumInfo md5Checksums = checksums.getChecksumInfo(ChecksumType.md5);
        if (md5Checksums != null) {
            builder.md5Actual(md5Checksums.getActual()).md5Original(md5Checksums.getOriginalOrNoOrig());
        }
        ChecksumInfo sha2Checksums = checksums.getChecksumInfo(ChecksumType.sha256);
        if (sha2Checksums != null) {
            // sha256 original (client-provided) is intentionally NOT persisted to the database — the nodes table
            // has no sha256_original column. Unlike sha1/md5, sha256 is always reloaded from DB with
            // TRUSTED_FILE_MARKER as the original, meaning it is always considered trusted after persistence.
            // The long-term direction is to remove sha1_original/md5_original too, not add sha256_original.
            builder.sha2(sha2Checksums.getActual());
        }
        return builder.build();
    }

    private NodeBuilder itemNodeBuilder(long nodeId, ItemInfo item) {
        return new NodeBuilder().nodeId(nodeId).nodePath(item.getRepoPath())
                .created(item.getCreated()).createdBy(item.getCreatedBy())
                .modified(item.getLastModified()).modifiedBy(item.getModifiedBy())
                .updated(item.getLastUpdated());
    }

    private ItemInfo itemInfoFromNode(Node node) {
        if (node.isFile()) {
            return fileInfoFromNode(node);
        } else {
            return folderInfoFromNode(node);
        }
    }

    private FolderInfo folderInfoFromNode(Node node) {
        if (node.isFile()) {
            throw new FolderExpectedException(node.getNodePath().toRepoPath());
        }
        FolderInfoImpl folderInfo = new FolderInfoImpl(node.getNodePath().toRepoPath(), node.getNodeId());
        folderInfo.setCreated(node.getCreated());
        folderInfo.setCreatedBy(node.getCreatedBy());
        folderInfo.setLastModified(node.getModified());
        folderInfo.setModifiedBy(node.getModifiedBy());
        folderInfo.setLastUpdated(node.getUpdated());
        return folderInfo;
    }

    private FileInfo fileInfoFromNode(Node node) {
        if (!node.isFile()) {
            throw new FileExpectedException(node.getNodePath().toRepoPath());
        }
        FileInfoImpl fileInfo = new FileInfoImpl(node.getNodePath().toRepoPath(), node.getNodeId());
        fileInfo.setCreated(node.getCreated());
        fileInfo.setCreatedBy(node.getCreatedBy());
        fileInfo.setLastModified(node.getModified());
        fileInfo.setModifiedBy(node.getModifiedBy());
        fileInfo.setLastUpdated(node.getUpdated());
        Set<ChecksumInfo> checksums = Sets.newHashSet(
                new ChecksumInfo(ChecksumType.sha1, node.getSha1Original(), node.getSha1Actual(), false),
                new ChecksumInfo(ChecksumType.sha256, ChecksumInfo.TRUSTED_FILE_MARKER, node.getSha2(), false),
                new ChecksumInfo(ChecksumType.md5, node.getMd5Original(), node.getMd5Actual(), false)
        );
        fileInfo.setChecksums(checksums);
        fileInfo.setSize(node.getLength());
        return fileInfo;
    }

    private VfsItem<?> fsItemFromNode(StoringRepo storingRepo, Node node) {
        if (node == null) {
            return null;
        }
        if (node.isFile()) {
            FileInfo fileInfo = fileInfoFromNode(node);
            return new DbFsFile(storingRepo, node.getNodeId(), fileInfo);
        } else {
            FolderInfo folderInfo = folderInfoFromNode(node);
            return new DbFsFolder(storingRepo, node.getNodeId(), folderInfo);
        }
    }

    @Override
    public List<RepoPath> getNodeRepoPaths(String repoKey, int limit) {
        try {
            return nodesDao.getNodesByRepo(repoKey, limit).stream()
                    .map(node -> node.getNodePath().toRepoPath())
                    .toList();
        } catch (SQLException e) {
            throw new VfsException(
                    "Failed to get node paths for repo '" + repoKey
                            + "': " + e.getMessage(), e);
        }
    }

    private long getFscQueryEntriesLimit() {
        try {
            return repoItemsCacheQueryLimit.getLong();
        } catch (IllegalStateException e) {
            log.debug("Failed getting query limit: {}", e.getMessage(), e);
            return 500;
        }
    }
}