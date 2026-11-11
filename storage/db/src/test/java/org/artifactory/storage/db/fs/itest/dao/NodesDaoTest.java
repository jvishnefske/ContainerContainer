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

package org.artifactory.storage.db.fs.itest.dao;

import aQute.bnd.unmodifiable.Sets;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.RepoStorageSummary;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.jfrog.rtfs.dto.sync.FileListEntry;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.fs.entity.NodeBuilder;
import org.artifactory.storage.db.fs.entity.NodePath;
import org.artifactory.storage.db.fs.entity.NodeProperty;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.storage.DbType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Low level tests of the {@link org.artifactory.storage.db.fs.dao.NodesDao}.
 *
 * @author Yossi Shaul
 */
@Test
public class NodesDaoTest extends DbBaseTest {

    private final NodePath fileNodePath = new NodePath("repo1", "ant/ant/1.5", "ant-1.5.jar", true);
    private final Set<String> repos = Set.of("repo1", "repo-copy", "repo2", "repo3");

    @Autowired
    private NodesDao nodesDao;

    @BeforeClass
    public void setup() throws InvocationTargetException, IllegalAccessException {
        setArtifactoryProperty(ConstantValues.dbOperationsBulkSize, "2");
        importSql("/sql/nodes.sql");
    }

    public void createDirectoryNode() throws SQLException {
        NodeBuilder b = new NodeBuilder().nodeId(800).file(false).repo("repo").path("path/to/dir").name("name")
                .createdBy("yossis").modifiedBy("yossis");

        nodesDao.create(b.build());
    }

    @Test
    public void testIsEmptyRepo() throws SQLException {
        assertFalse(nodesDao.isEmptyRepo("nonEmptyRepo"));
        assertTrue(nodesDao.isEmptyRepo("emptyRepo"));
    }

    @Test(dependsOnMethods = "createDirectoryNode")
    public void loadDirectoryNodeByPath() throws SQLException {
        Node node = nodesDao.get(new NodePath("repo", "path/to/dir", "name", false));
        assertNotNull(node);
        assertEquals(node.getNodeId(), 800);
        assertFalse(node.isFile());
        assertEquals(node.getDepth(), 4);
        assertEquals(node.getRepo(), "repo");
        assertEquals(node.getPath(), "path/to/dir");
        assertEquals(node.getName(), "name");
        assertEquals(node.getCreatedBy(), "yossis");
        assertEquals(node.getModifiedBy(), "yossis");
    }

    @Test
    public void testGetFileCount() throws SQLException {
        int fileCount = nodesDao.getFileCount("repo1", "test.bin");
        Assert.assertEquals(fileCount, 1);
    }

    @Test(dependsOnMethods = "loadDirectoryNodeByPath")
    public void loadDirectoryNodeById() throws SQLException {
        Node node = nodesDao.get(800);
        EqualsBuilder.reflectionEquals(node, nodesDao.get(new NodePath("repo", "path/to/dir", "name", false)));
    }

    public void itemExists() throws SQLException {
        assertTrue(nodesDao.exists(new NodePath("repo1", "", "org", false)));
    }

    public void itemNotExists() throws SQLException {
        assertFalse(nodesDao.exists(new NodePath("repo1", "", "nosuchfile", false)));
    }

    public void nodeExists() throws SQLException {
        long nodeId = 112233;
        NodeBuilder b =
                new NodeBuilder().nodeId(nodeId).file(false).repo("repo").path("path/to/dir")
                        .name("file_name").createdBy("dorso").modifiedBy("dorso");
        assertFalse(nodesDao.exists(nodeId));
        nodesDao.create(b.build());
        assertTrue(nodesDao.exists(nodeId));
        nodesDao.delete(nodeId);
        assertFalse(nodesDao.exists(nodeId));
    }

    public void deleteDirectoryNode() throws SQLException {
        NodeBuilder b = new NodeBuilder().nodeId(801).file(false).repo("repo").path("path/to/dir").name("todelete")
                .createdBy("yossis").modifiedBy("yossis");

        Node inserted = b.build();
        nodesDao.create(inserted);
        Node loaded = nodesDao.get(inserted.getNodePath());
        assertNotNull(loaded);
        boolean deleted = nodesDao.delete(loaded.getNodeId());
        assertTrue(deleted);
        assertFalse(nodesDao.exists(inserted.getNodePath()));
    }

    public void deleteNonExistent() throws SQLException {
        boolean deleted = nodesDao.delete(990);
        assertFalse(deleted);
    }

    public void lockNodeForWriteExists() throws SQLException {
        try (ResultSet resultSet = nodesDao.lockNodeForWrite(10001)) {
            assertTrue(resultSet.next());
        }
    }

    public void lockNodeForWriteNotExists() throws SQLException {
        try (ResultSet resultSet = nodesDao.lockNodeForWrite(28298282)) {
            assertFalse(resultSet.next());
        }
    }

    public void getChildrenOfRoot() throws SQLException {
        NodePath path = new NodePath("repo1", "", "", false);
        List<? extends Node> children = nodesDao.getChildren(path);
        assertEquals(children.size(), 5);

        assertTrue(nodesDao.hasChildren(path));
    }

    public void testGetStreamedChildren() throws SQLException {
        NodePath path = new NodePath("repo1", "", "", false);
        Stream<Node> children = nodesDao.getChildrenStream(path);
        assertEquals(children.count(), 5);
        assertTrue(nodesDao.hasChildren(path));
    }

    public void testGetStreamedChildrenByPath() throws Exception {
        NodePath path = new NodePath("repo1", "org/yossis/", "tools", false);
        Stream<Node> children = nodesDao.getNodesStreamIgnoringRepo(path);
        assertEquals(children.count(), 4);
    }

    public void getChildrenOfNodeDirectlyUnderRoot() throws SQLException {
        // nodes directly under root has name but no path - hence special test case
        NodePath path = new NodePath("repo1", "", "org", false);
        List<? extends Node> children = nodesDao.getChildren(path);
        assertEquals(children.size(), 1);

        assertTrue(nodesDao.hasChildren(path));
    }

    public void getChildrenOfNodeDirectlyUnderRootWithCousinStartingWithSamePrefix() throws SQLException {
        NodePath path = new NodePath("repo1", "", "ant", false);
        List<? extends Node> children = nodesDao.getChildren(path);
        assertEquals(children.size(), 1);

        assertTrue(nodesDao.hasChildren(path));
    }

    public void getChildrenOfLeafFolderNode() throws SQLException {
        NodePath leaf = new NodePath("repo1", "org/yossis/tools", "test.bin", true);
        assertTrue(nodesDao.exists(leaf));
        assertEquals(nodesDao.getChildren(leaf).size(), 0);
        assertFalse(nodesDao.hasChildren(leaf));
    }

    public void getChildrenOfLeafFileNode() throws SQLException {
        assertTrue(nodesDao.exists(fileNodePath));
        assertEquals(nodesDao.getChildren(fileNodePath).size(), 0);
        assertFalse(nodesDao.hasChildren(fileNodePath));
    }

    public void getChildrenOfFolderWithUnderscore() throws SQLException {
        NodePath path1 = new NodePath("repo1", "", "a_1.2", false);
        NodePath path2 = new NodePath("repo1", "", "ab1.2", false);
        NodePath file1 = new NodePath("repo1", "a_1.2", "tt.txt", true);
        NodePath file2 = new NodePath("repo1", "ab1.2", "tt.txt", true);
        assertTrue(nodesDao.exists(path1));
        assertTrue(nodesDao.exists(path2));
        assertFalse(nodesDao.exists(file1));
        assertTrue(nodesDao.exists(file2));
        assertEquals(nodesDao.getChildren(path1).size(), 0);
        assertFalse(nodesDao.hasChildren(path1));
        assertEquals(nodesDao.getChildren(path2).size(), 1);
        assertTrue(nodesDao.hasChildren(path2));
    }

    public void getChildrenWithPositiveLimit() throws SQLException {
        NodePath path = new NodePath("repo1", "", "", false);

        List<Node> limitedChildren = nodesDao.getChildren(path, 2);
        assertEquals(limitedChildren.size(), 2, "Expected 2 children with limit 2");

        List<Node> singleChild = nodesDao.getChildren(path, 1);
        assertEquals(singleChild.size(), 1, "Expected 1 child with limit 1");

        List<Node> equalLimitChildren = nodesDao.getChildren(path, 5);
        assertEquals(equalLimitChildren.size(), 5, "Expected 5 children with limit 5");

        List<Node> largeLimitChildren = nodesDao.getChildren(path, 10);
        assertEquals(largeLimitChildren.size(), 5,
                "Expected 5 children with limit 10 (should not exceed actual count)");
    }

    public void getChildrenWithLimitOnLeafNode() throws SQLException {
        List<Node> leafChildren = nodesDao.getChildren(fileNodePath, 5);
        assertEquals(leafChildren.size(), 0, "Expected 0 children for leaf node even with limit");
    }

    public void countRepositoryFiles() throws SQLException {
        assertEquals(nodesDao.getFilesCount("repo1"), 4);
    }

    @Test(dataProvider = "booleanDataProvider")
    void countAllRepositoryFiles(boolean totalSizeUseChunks) throws SQLException {
        final int expectedValue = 23;
        // We have special chunked query for Postgresql DB. Check that this query returns the same result.
        if (ArtifactoryHome.get().getDBProperties().getDbType() == DbType.POSTGRESQL) {
            // Set a small number of chunks to make the query to run several times
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.totalSizeUseChunks.getPropertyName(),
                            Boolean.toString(totalSizeUseChunks));
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.totalSizeQueryChunkSize.getPropertyName(), "2");
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.totalSizeDelayMillis.getPropertyName(), "1");
            assertEquals(nodesDao.getFilesCount(), expectedValue); // Run the query

            // Disable query chunking by setting the DB to any non-Postgresql type
            ArtifactoryDbProperties artifactoryDbPropertiesOrig = storeAndReplaceArtifactoryDbProperties();
            try {
                assertEquals(nodesDao.getFilesCount(), expectedValue); // Run the query
            } finally {
                // Since the same instance of 'nodesDao' is used by other tests in this suit,
                // I have to restore its original state.
                restoreArtifactoryDbProperties(artifactoryDbPropertiesOrig);
            }
        } else if (ArtifactoryHome.get().getDBProperties().getDbType() == DbType.MYSQL) {
            // Check query use with and without index
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.totalSizeMySqlNoIndex.getPropertyName(), "true");
            assertEquals(nodesDao.getFilesCount(), expectedValue);
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.totalSizeMySqlNoIndex.getPropertyName(), "false");
            assertEquals(nodesDao.getFilesCount(), expectedValue);
        } else { // for any DB other than Postgresql and MySQL just run the query "as-is"
            assertEquals(nodesDao.getFilesCount(), expectedValue);
        }
    }

    @DataProvider(name = "booleanDataProvider")
    private Object[][] provideProjectKey() {
        return new Object[][] {{true}, {false}};
    }

    @Test(dependsOnMethods = "createDirectoryNode")
    public void getRepositoriesStorageSummary() throws SQLException {
        final Set<RepoStorageSummary> expected = Set.of(
                new RepoStorageSummary("auto-trashcan", -1, 7, 5012973L),
                new RepoStorageSummary("repo", 0, 0, 0L),
                new RepoStorageSummary("repo2", 4, 4, 2864556L),
                new RepoStorageSummary("repo-foo", 0, 0, 716139L),
                new RepoStorageSummary("repo3", 4, 3, 5729112L),
                new RepoStorageSummary("repo1", 14, 4, 2994858L),
                new RepoStorageSummary("repo-copy", -1, 3, 130302L)
        );
        // We have special chunked query for Postgresql DB. Check that this query returns the same result.
        if (ArtifactoryHome.get().getDBProperties().getDbType() == DbType.POSTGRESQL) {
            // Set a small number of chunks to make the query to run several times
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.totalSizeUseChunks.getPropertyName(), "true");
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.totalSizeQueryChunkSize.getPropertyName(), "2");
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.totalSizeDelayMillis.getPropertyName(), "1");
            checkRepositoriesStorageSummary(expected); // Run the chunked query

            // Disable query chunking by setting the DB to any non-Postgresql type
            ArtifactoryDbProperties artifactoryDbPropertiesOrig = storeAndReplaceArtifactoryDbProperties();
            try {
                checkRepositoriesStorageSummary(expected); // Run the original query
            } finally {
                // Since the same instance of 'nodesDao' is used by other tests in this suit,
                // I have to restore its original state.
                restoreArtifactoryDbProperties(artifactoryDbPropertiesOrig);
            }
        } else if (ArtifactoryHome.get().getDBProperties().getDbType() == DbType.MYSQL) {
            //check for both cases of using index and not using index
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.totalSizeMySqlNoIndex.getPropertyName(), "false");
            checkRepositoriesStorageSummary(expected);
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.totalSizeMySqlNoIndex.getPropertyName(), "true");
            checkRepositoriesStorageSummary(expected);
        } else { // for any DB other than Postgresql or Mysql just run the query "as-is"
            checkRepositoriesStorageSummary(expected);
        }
    }

    private void checkRepositoriesStorageSummary(Set<RepoStorageSummary> expected) throws SQLException {
        Set<RepoStorageSummary> result = nodesDao.getRepositoriesStorageSummary();
        assertEquals(result.size(), 10);
        expected.forEach(x -> assertTrue(result.contains(x),
                "Result should contain summary of repo " + x.getRepoKey()));
    }

    private ArtifactoryDbProperties storeAndReplaceArtifactoryDbProperties() {
        ArtifactoryDbProperties artifactoryDbPropertiesOrig =
                (ArtifactoryDbProperties) ReflectionTestUtils.getField(nodesDao, "dbProperties");
        ArtifactoryDbProperties artifactoryDbProperties =
                (ArtifactoryDbProperties) spy(
                        Objects.requireNonNull(ReflectionTestUtils.getField(nodesDao, "dbProperties")));
        ReflectionTestUtils.setField(nodesDao, "dbProperties", artifactoryDbProperties);
        when(artifactoryDbProperties.getDbType()).thenReturn(DbType.DERBY);
        return artifactoryDbPropertiesOrig;
    }

    private void restoreArtifactoryDbProperties(ArtifactoryDbProperties artifactoryDbPropertiesOrig) {
        ReflectionTestUtils.setField(nodesDao, "dbProperties", artifactoryDbPropertiesOrig);
    }

    public void countAndSizeRepositoryFiles() throws SQLException {
        assertEquals(nodesDao.getFilesCountAndSize(new NodePath("repo1", "", "", false)).getFileCount(), 4);
        assertEquals(nodesDao.getFilesCountAndSize(new NodePath("repo1", "", "", false)).getFolderSize(), 846441);
    }

    public void countFilesUnderFolder() throws SQLException {
        assertEquals(nodesDao.getFilesCount(new NodePath("repo1", "", "ant", false)), 1);
    }


    public void countAndSizeFilesUnderFolder() throws SQLException {
        assertEquals(nodesDao.getFilesCountAndSize(new NodePath("repo1", "", "ant", false)).getFileCount(), 1);
        assertEquals(nodesDao.getFilesCountAndSize(new NodePath("repo1", "", "ant", false)).getFolderSize(), 716139);
    }

    public void countFilesUnderFolderWithDirectChildren() throws SQLException {
        assertEquals(nodesDao.getFilesCount(new NodePath("repo1", "ant/ant", "1.5", false)), 1);
    }

    public void countAndSizeFilesUnderFolderWithDirectChildren() throws SQLException {
        assertEquals(nodesDao.getFilesCountAndSize(new NodePath("repo1", "ant/ant", "1.5", false)).getFileCount(), 1);
        assertEquals(nodesDao.getFilesCountAndSize(new NodePath("repo1", "ant/ant", "1.5", false)).getFolderSize(),
                716139);
    }

    public void countFilesUnderNonExistentFolder() throws SQLException {
        assertEquals(nodesDao.getFilesCount(new NodePath("repo1", "xxx", "boo", false)), 0);
    }

    public void countAndSizeFilesUnderNonExistentFolder() throws SQLException {
        assertEquals(nodesDao.getFilesCountAndSize(new NodePath("repo1", "xxx", "boo", false)).getFileCount(), 0);
        assertEquals(nodesDao.getFilesCountAndSize(new NodePath("repo1", "xxx", "boo", false)).getFolderSize(), 0);
    }

    public void countFilesUnderFile() throws SQLException {
        assertEquals(nodesDao.getFilesCount(fileNodePath), 0);
    }

    public void getFilesTotalSize() throws SQLException {
        //hardcoded in nodes.sql
        final int antExpectedSize = 716139;
        final int totalExpectedSize = 846441;
        final int toolsExpectedSize = 130302;

        assertEquals(nodesDao.getFilesTotalSize(new NodePath("repo1", "", "ant", false)), antExpectedSize,
                "single file size should be " + antExpectedSize);

        assertEquals(nodesDao.getFilesTotalSize("repo1"), totalExpectedSize,
                "total size of repo1 should be " + totalExpectedSize);

        long filesTotalSize = 0;
        for (Node node : nodesDao.getChildren(new NodePath("repo1", "", "", false))) {
            filesTotalSize += nodesDao.getFilesTotalSize(node.getNodePath());
        }

        assertEquals(filesTotalSize, totalExpectedSize,
                "sum of children size in repo1 should be " + filesTotalSize);

        assertEquals(nodesDao.getFilesTotalSize(new NodePath("repo1", "org/yossis", "tools", false)), toolsExpectedSize,
                "total size of files under org/yossis should be " + toolsExpectedSize);
    }

    public void countRepositoryFilesAndFolders() throws SQLException {
        assertEquals(nodesDao.getNodesCount("repo1"), 19);
    }

    public void countRepositoryFilesAndFoldersUnderFolder() throws SQLException {
        assertEquals(nodesDao.getNodesCount(new NodePath("repo1", "", "ant", false)), 3);
    }

    public void countFilesAndFoldersUnderFolderWithDirectChildren() throws SQLException {
        assertEquals(nodesDao.getNodesCount(new NodePath("repo1", "ant/ant", "1.5", false)), 1);
    }

    public void countFilesAndFoldersUnderNonExistentFolder() throws SQLException {
        assertEquals(nodesDao.getNodesCount(new NodePath("repo1", "xxx", "boo", false)), 0);
    }

    public void countFilesAndFoldersUnderFile() throws SQLException {
        assertEquals(nodesDao.getNodesCount(fileNodePath), 0);
    }

    public void countFilesUnderFolderWithPrefix() throws SQLException {
        assertEquals(nodesDao.getFilesCount(new NodePath("repo2", "", "a", false)), 2,
                "Files from 'repo2:/aa' were counted");
    }

    public void countNodesUnderFolderWithPrefix() throws SQLException {
        assertEquals(nodesDao.getNodesCount(new NodePath("repo2", "", "a", false)), 3,
                "Nodes from 'repo2:/aa' were counted");
    }

    public void getTotalSizeUnderFolderWithPrefix() throws SQLException {
        assertEquals(nodesDao.getFilesTotalSize(new NodePath("repo2", "", "a", false)), 716139 * 2,
                "Files from 'repo2:/aa' were counted");
    }

    public void countConanV2Files() throws SQLException {
        assertEquals(nodesDao.getConanV1LayoutArtifactCount(), 0);
    }

    public void updateFolderNode() throws SQLException {
        int nodeId = 2000;
        NodeBuilder b = new NodeBuilder().nodeId(nodeId).file(false).repo("repo").path("path/to/dir").name("toupdate")
                .createdBy("yossis").modifiedBy("yossis");

        nodesDao.create(b.build());

        Node nodeToUpdate = b.repo("repo2").path("new/path").name("updatedfolder")
                .created(1111).createdBy("updater-creator")
                .modified(2222).modifiedBy("updater").updated(3333).build();

        int updateCount = nodesDao.update(nodeToUpdate);
        assertEquals(updateCount, 1);

        Node updatedNode = nodesDao.get(nodeToUpdate.getNodePath());
        assertNotNull(updatedNode);
        assertEquals(updatedNode.getRepo(), nodeToUpdate.getRepo());
        assertEquals(updatedNode.getPath(), nodeToUpdate.getPath());
        assertEquals(updatedNode.getName(), nodeToUpdate.getName());
        assertEquals(updatedNode.getCreated(), nodeToUpdate.getCreated());
        assertEquals(updatedNode.getCreatedBy(), nodeToUpdate.getCreatedBy());
        assertEquals(updatedNode.getModified(), nodeToUpdate.getModified());
        assertEquals(updatedNode.getModifiedBy(), nodeToUpdate.getModifiedBy());
        assertEquals(updatedNode.getUpdated(), nodeToUpdate.getUpdated());
        assertEquals(updatedNode.getNodeId(), nodeId, "Node id shouldn't have been updated");
        assertFalse(updatedNode.isFile(), "Node type shouldn't have been updated");
    }


    public void nodeIdRoot() throws SQLException {
        assertEquals(nodesDao.getNodeId(new NodePath("repo1", "", "", false)), 1);
    }

    public void nodeIdNoSuchNode() throws SQLException {
        assertEquals(nodesDao.getNodeId(new NodePath("repo2", "no", "folder", false)), DbService.NO_DB_ID);
    }

    public void nodeSha1OfFile() throws SQLException {
        assertEquals(nodesDao.getNodeSha1(new NodePath("repo1", "org/yossis/tools", "test.bin", true)),
                "acab88fc2a043c2479a6de676a2f8179e9ea2167");
    }

    public void nodeSha1NotExist() throws SQLException {
        assertNull(nodesDao.getNodeSha1(new NodePath("repo2", "no", "folder", false)));
    }

    public void nodeSha1OfFolder() throws SQLException {
        assertTrue(nodesDao.exists(new NodePath("repo1", "org/yossis", "tools", false)));
        assertNull(nodesDao.getNodeSha1(new NodePath("repo1", "org/yossis", "tools", false)));
    }

    public void searchFilesByProperty() throws SQLException {
        List<Node> nodes = nodesDao.searchNodesByProperty("repo1", "build.number");
        assertNotNull(nodes);
        assertEquals(nodes.size(), 1);
        assertEquals(nodes.getFirst().getNodeId(), 5);
    }

    public void searchFilesByPropertyNoMatch() throws SQLException {
        //TODO: [by YS] the property value is ignored. this method is re-evaluated
        List<Node> nodes = nodesDao.searchNodesByProperty("repo1", "build.number");
        assertNotNull(nodes);
        assertEquals(nodes.size(), 1);
    }

    public void findChecksumsBySha1() throws SQLException {
        List<Node> nodes;
        nodes = nodesDao.searchByChecksum(ChecksumType.sha1, "dcab88fc2a043c2479a6de676a2f8179e9ea2167");
        assertEquals(nodes.size(), 1);
        assertEquals(nodes.getFirst().getNodeId(), 5L);
        assertEquals(nodes.getFirst().getRepo(), "repo1");
        assertEquals(nodes.getFirst().getName(), "ant-1.5.jar");
        nodes = nodesDao.searchByChecksum(ChecksumType.sha1, "dddd88fc2a043c2479a6de676a2f8179e9eadddd");
        assertEquals(nodes.size(), 2);
        Node node = getById(nodes, 13);
        assertNotNull(node);
        assertEquals(node.getRepo(), "repo1");
        assertEquals(node.getName(), "file3.pom");
        node = getById(nodes, 15);
        assertNotNull(node);
        assertEquals(node.getRepo(), "repo-copy");
        assertEquals(node.getName(), "file3.bin");
        nodes = nodesDao.searchByChecksum(ChecksumType.sha1, "acab88fc2a043c2479a6de676a2f8179e9ea2222");
        assertTrue(nodes.isEmpty());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testFindEmptyChecksumsBySha1() throws SQLException {
        nodesDao.searchByChecksum(ChecksumType.sha1, "wrong");
    }

    public void findChecksumsByMd5() throws SQLException {
        List<Node> nodes;
        nodes = nodesDao.searchByChecksum(ChecksumType.md5, "902a360ecad98a34b59863c1e65bcf71");
        assertEquals(nodes.size(), 1);
        assertEquals(nodes.getFirst().getNodeId(), 5L);
        assertEquals(nodes.getFirst().getRepo(), "repo1");
        assertEquals(nodes.getFirst().getName(), "ant-1.5.jar");
        nodes = nodesDao.searchByChecksum(ChecksumType.md5, "502a360ecad98a34b59863c1e65bcf71");
        assertEquals(nodes.size(), 2);
        Node node = getById(nodes, 13);
        assertNotNull(node);
        assertEquals(node.getRepo(), "repo1");
        assertEquals(node.getName(), "file3.pom");
        node = getById(nodes, 15);
        assertNotNull(node);
        assertEquals(node.getRepo(), "repo-copy");
        assertEquals(node.getName(), "file3.bin");
        nodes = nodesDao.searchByChecksum(ChecksumType.md5, "902a360ecad98a34b59863c1e65b2222");
        assertTrue(nodes.isEmpty());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testFindEmptyChecksumsByMd5() throws SQLException {
        nodesDao.searchByChecksum(ChecksumType.md5, "wrong");
    }

    public void findChecksumsBySha2() throws SQLException {
        List<Node> nodes;

        nodes = nodesDao.searchByChecksum(ChecksumType.sha256,
                "bbbb23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60bbbb");
        assertEquals(nodes.size(), 1);
        assertEquals(nodes.getFirst().getNodeId(), 5L);
        assertEquals(nodes.getFirst().getRepo(), "repo1");
        assertEquals(nodes.getFirst().getName(), "ant-1.5.jar");
        nodes = nodesDao.searchByChecksum(ChecksumType.sha256,
                "acdc23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60febd");
        assertEquals(nodes.size(), 2);
        Node node = getById(nodes, 16);
        assertNotNull(node);
        assertEquals(node.getRepo(), "repo-copy");
        assertEquals(node.getName(), "trustme.jar");
        node = getById(nodes, 17);
        assertNotNull(node);
        assertEquals(node.getRepo(), "repo-copy");
        assertEquals(node.getName(), "badmd5.jar");
        nodes = nodesDao.searchByChecksum(ChecksumType.sha256,
                "ABCD23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60ACDC");
        assertTrue(nodes.isEmpty());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testFindEmptyChecksumsBySha2() throws SQLException {
        nodesDao.searchByChecksum(ChecksumType.sha256, "wrong");
    }

    public void searchBadSha1Checksums() {
        List<Node> nodes =
                nodesDao.searchBadChecksums(ChecksumType.sha1, repos);
        assertBadChecksumNodes(nodes);
    }

    @Test
    public void searchBadSha256Checksums() {
        Set<String> reposWithTrash = Set.of("repo1", "repo-copy", "repo2", "repo3", "auto-trashcan");
        List<Node> nodes =
                nodesDao.searchBadChecksums(ChecksumType.sha256, reposWithTrash);
        assertEquals(nodes.size(), 4);
        assertTrue(nodes.stream().anyMatch(node -> node.getRepo().contains("auto-trashcan")));
    }

    public void searchBadCheckSumWithLimit() throws Exception {
        setArtifactoryProperty(ConstantValues.badChecksumsSearchLimit, "2");
        List<Node> nodes =
                nodesDao.searchBadChecksums(ChecksumType.sha256, repos);
        assertEquals(nodes.size(), 2);
        setArtifactoryProperty(ConstantValues.badChecksumsSearchLimit, "10000");
    }

    private void assertBadChecksumNodes(List<Node> nodes) {
        assertEquals(nodes.size(), 3);
        Node node = getById(nodes, 12);
        assertNotNull(node);
        assertEquals(node.getRepo(), "repo1");
        assertEquals(node.getName(), "file2.pom");
        node = getById(nodes, 13);
        assertNotNull(node);
        assertEquals(node.getRepo(), "repo1");
        assertEquals(node.getName(), "file3.pom");
        node = getById(nodes, 15);
        assertNotNull(node);
        assertEquals(node.getRepo(), "repo-copy");
        assertEquals(node.getName(), "file3.bin");
    }

    public void findMissingDirectories() throws Exception {
        if (dbProperties.getDbType() == DbType.DERBY) {
            return; // CONCAT doesn't work on Derby + it's irrelevant
        }
        List<Node> nodes = nodesDao.getOrphanNodes(new NodePath("repo3", null, null, false));
        assertEquals(nodes.size(), 2);
        Node node = getById(nodes, 33);
        assertNotNull(node);
        assertEquals(node.getPath(), "a/B");
        assertEquals(node.getName(), "C");
        node = getById(nodes, 34);
        assertNotNull(node);
        assertEquals(node.getPath(), "B");
        assertEquals(node.getName(), "test.txt");
    }

    public void findMissingDirectoriesUnderPath() throws Exception {
        if (dbProperties.getDbType() == DbType.DERBY) {
            return; // CONCAT doesn't work on Derby + it's irrelevant
        }
        List<Node> nodes = nodesDao.getOrphanNodes(new NodePath("repo3", "a", "B", false));
        assertEquals(nodes.size(), 1);
        Node node = getById(nodes, 33);
        assertNotNull(node);
        assertEquals(node.getPath(), "a/B");
        assertEquals(node.getName(), "C");
    }

    public void searchBadMd5Checksums() {
        List<Node> nodes = nodesDao.searchBadChecksums(ChecksumType.md5, repos);
        assertEquals(nodes.size(), 1);
        Node node = getById(nodes, 17);
        assertNotNull(node);
        assertEquals(node.getRepo(), "repo-copy");
        assertEquals(node.getName(), "badmd5.jar");
    }

    public void constantValuesGetQuery() throws SQLException, InvocationTargetException, IllegalAccessException {
        setArtifactoryProperty(ConstantValues.nodesDaoSqlGetNodeByPath,
                "SELECT * FROM nodes WHERE repo = ? AND node_path = ? AND node_name = ?");
        Node node = nodesDao.get(new NodePath("repo1", "", "org", false));
        assertNotNull(node);
        try {
            setArtifactoryProperty(ConstantValues.nodesDaoSqlGetNodeByPath, "SELECT * from not_nodes");
            nodesDao.get(new NodePath("repo1", "", "org", false));
        } catch (SQLException e) {
            return; // expected
        } finally {
            removeArtifactoryProperty(ConstantValues.nodesDaoSqlGetNodeByPath);
        }
        fail();
    }

    public void constantValuesExistsQuery() throws SQLException, InvocationTargetException, IllegalAccessException {
        setArtifactoryProperty(ConstantValues.nodesDaoSqlNodeExists,
                "SELECT count(*) FROM nodes WHERE repo = ? AND node_path = ? AND node_name = ?");
        boolean exists = nodesDao.exists(new NodePath("repo1", "", "org", false));
        assertTrue(exists);
        try {
            setArtifactoryProperty(ConstantValues.nodesDaoSqlNodeExists, "SELECT * from not_nodes");
            nodesDao.exists(new NodePath("repo1", "", "org", false));
        } catch (SQLException e) {
            return; // expected
        } finally {
            removeArtifactoryProperty(ConstantValues.nodesDaoSqlGetNodeByPath);
        }
        fail();
    }

    public void constantValuesGetNodeIdQuery() throws SQLException, InvocationTargetException, IllegalAccessException {
        setArtifactoryProperty(ConstantValues.nodesDaoSqlGetNodeIdByPath,
                "SELECT node_id FROM nodes WHERE repo = ? AND node_path = ? AND node_name = ?");
        long nodeId = nodesDao.getNodeId(new NodePath("repo1", "", "org", false));
        assertEquals(nodeId, 8);
        try {
            setArtifactoryProperty(ConstantValues.nodesDaoSqlGetNodeIdByPath, "SELECT * from not_nodes");
            nodesDao.getNodeId(new NodePath("repo1", "", "org", false));
        } catch (SQLException e) {
            return; // expected
        } finally {
            removeArtifactoryProperty(ConstantValues.nodesDaoSqlGetNodeIdByPath);
        }
        fail();
    }

    public void constantValuesSearchNodeByPropQuery()
            throws SQLException, InvocationTargetException, IllegalAccessException {
        setArtifactoryProperty(ConstantValues.nodesDaoSqlSearchFilesByProperty,
                "SELECT n.* FROM nodes n JOIN node_props p ON n.node_id = p.node_id WHERE repo = ? AND p.prop_key = ?");
        List<Node> repo1 = nodesDao.searchNodesByProperty("repo1", "build.number");
        assertNotNull(repo1);
        try {
            setArtifactoryProperty(ConstantValues.nodesDaoSqlSearchFilesByProperty, "SELECT * from not_nodes");
            nodesDao.searchNodesByProperty("repo1", "build.number");
        } catch (SQLException e) {
            return; // expected
        } finally {
            removeArtifactoryProperty(ConstantValues.nodesDaoSqlSearchFilesByProperty);
        }
        fail();
    }

    public void constantValuesNodeItemTypeQuery()
            throws SQLException, InvocationTargetException, IllegalAccessException {
        setArtifactoryProperty(ConstantValues.nodesDaoSqlGetItemType,
                "SELECT node_type FROM nodes WHERE repo = ? AND node_path = ? AND node_name = ?");
        boolean exists = nodesDao.exists(new NodePath("repo1", "", "org", false));
        assertTrue(exists);
        try {
            setArtifactoryProperty(ConstantValues.nodesDaoSqlGetItemType, "SELECT * from not_nodes");
            nodesDao.getItemType(new NodePath("repo1", "", "org", false));
        } catch (SQLException e) {
            return; // expected
        } finally {
            removeArtifactoryProperty(ConstantValues.nodesDaoSqlGetItemType);
        }
        fail();
    }

    @Test(dataProvider = "existingRepos")
    public void testFindExists(Set<String> reposToTest, Collection<String> expectedRepos, int chunkSize)
            throws SQLException {
        try {
            if (chunkSize > 0) {
                ArtifactoryHome.get().getArtifactoryProperties()
                        .setProperty(ConstantValues.existsRepoChunkSize.getPropertyName(),
                                String.valueOf(chunkSize));
            }
            Set<String> existingRepos = nodesDao.findExistsRepoRootPaths(reposToTest);
            assertTrue(existingRepos.containsAll(expectedRepos));
            assertEquals(expectedRepos.size(), existingRepos.size());
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.existsRepoChunkSize.getPropertyName(),
                            ConstantValues.existsRepoChunkSize.getDefValue());
        }
    }

    @DataProvider
    public Object[][] existingRepos() {
        return new Object[][] {
                {Set.of("nonExisted", "nonExisted2"), Collections.emptySet(), -1},
                {Set.of("nonExisted", "nonExisted2"), Collections.emptySet(), 2},
                {Set.of("nonExisted", "nonExisted2"), Collections.emptySet(), 100},
                {Set.of("repo1", "repo2", "repo3"), Set.of("repo1", "repo2", "repo3"), -1},
                {Set.of("repo1", "repo2", "repo3"), Set.of("repo1", "repo2", "repo3"), 2},
                {Set.of("repo1", "repo2", "repo3"), Set.of("repo1", "repo2", "repo3"), 100},
                {Set.of("repo1", "repo2", "repo4"), Set.of("repo1", "repo2"), -1},
                {Set.of("repo1", "repo2", "repo4"), Set.of("repo1", "repo2"), 2},
                {Set.of("repo1", "repo2", "repo4"), Set.of("repo1", "repo2"), 100}
        };
    }

    public void testGetLastNodeId() throws SQLException {
        assertEquals(nodesDao.findLastNodeId(), 10001);
    }

    @Test
    public void testGetTrashFoldersByNodeIdRangeExcludesFilesInRange() throws SQLException {
        NodeBuilder folderBuilder1 = new NodeBuilder().nodeId(2005).file(false).repo("auto-trashcan")
                .path("test").name("testFolder1").createdBy("test").modifiedBy("test");
        NodeBuilder fileBuilder = new NodeBuilder().nodeId(2004).file(true).repo("auto-trashcan")
                .path("test").name("testFile.txt").createdBy("test").modifiedBy("test");
        NodeBuilder folderBuilder2 = new NodeBuilder().nodeId(2003).file(false).repo("auto-trashcan")
                .path("test").name("testFolder2").createdBy("test").modifiedBy("test");
        NodeBuilder folderBuilder3 = new NodeBuilder().nodeId(2002).file(false).repo("repo1")
                .path("test").name("testFolder3").createdBy("test").modifiedBy("test");
        NodeBuilder folderBuilder4 = new NodeBuilder().nodeId(2001).file(false).repo("auto-trashcan")
                .path("test").name("testFolder4").createdBy("test").modifiedBy("test");
        try {
            nodesDao.create(fileBuilder.build());
            nodesDao.create(folderBuilder1.build());
            nodesDao.create(folderBuilder2.build());
            nodesDao.create(folderBuilder3.build());
            nodesDao.create(folderBuilder4.build());
            List<Node> folders = nodesDao.getTrashFoldersByNodeIdRange(2002, 5, 100);
            assertNotNull(folders);
            assertEquals(folders.size(), 2);
            assertTrue(folders.stream().anyMatch(f -> f.getNodeId() == 2003));
            assertTrue(folders.stream().anyMatch(f -> f.getNodeId() == 2005));
            folders = nodesDao.getTrashFoldersByNodeIdRange(2002, 5, 1);
            assertEquals(folders.size(), 1);
            assertTrue(folders.stream().anyMatch(f -> f.getNodeId() == 2003));
        } finally {
            nodesDao.delete(2002);
            nodesDao.delete(2003);
            nodesDao.delete(2004);
            nodesDao.delete(2005);
            nodesDao.delete(2001);
        }
    }

    @Test
    public void testGetNodesBulk() throws SQLException {
        // setup
        List<NodePath> paths = List.of(
                new NodePath("repo1", "", "ant", false),
                new NodePath("repo1", "ant", "ant", false),
                new NodePath("repo1", "ant/ant", "1.5", false),
                new NodePath("repo1", "ant/ant/1.5", "ant-1.5.jar", true)
        );

        // test
        Map<NodePath, Node> nodesMap = nodesDao.get(paths);

        // verify
        assertEquals(nodesMap.size(), 4);
        assertEquals(nodesMap.keySet(), new HashSet<>(paths));
        assertFalse(nodesMap.values().stream().anyMatch(Objects::isNull));

        for (Map.Entry<NodePath, Node> nodePathNodeEntry : nodesMap.entrySet()) {
            NodePath nodePath = nodePathNodeEntry.getKey();
            Node node = nodePathNodeEntry.getValue();

            assertEquals(nodePath.getName(), node.getName());
            assertEquals(nodePath.getPath(), node.getPath());
            assertEquals(nodePath.getRepo(), node.getRepo());
        }
    }

    @Test
    public void testGetNodesIds() throws SQLException {
        // setup
        List<NodePath> paths = List.of(
                new NodePath("repo1", "", "ant", false),
                new NodePath("repo1", "ant", "ant", false),
                new NodePath("repo1", "ant/ant", "1.5", false),
                new NodePath("repo1", "ant/ant/1.5", "ant-1.5.jar", true)
        );
        Set<Long> expectedIds = Set.of(2L, 3L, 4L, 5L); // as appears in nodes.sql file

        // test
        Map<NodePath, Long> nodesIds = nodesDao.getNodesIds(paths);

        // verify
        assertEquals(nodesIds.size(), 4);
        assertEquals(new HashSet<>(nodesIds.values()), expectedIds);
        assertEquals(nodesIds.get(paths.get(0)), Long.valueOf(2));
        assertEquals(nodesIds.get(paths.get(1)), Long.valueOf(3));
        assertEquals(nodesIds.get(paths.get(2)), Long.valueOf(4));
        assertEquals(nodesIds.get(paths.get(3)), Long.valueOf(5));
    }

    @Test
    public void testCreateBulk() throws SQLException {
        try {
            // setup
            List<Node> bulkToCreate = List.of(
                    new NodeBuilder().nodeId(21111).file(false).repo("repo").path("path/to/dir").name("name11")
                            .createdBy("elis").modifiedBy("elis").build(),
                    new NodeBuilder().nodeId(21112).file(true).repo("repo").path("path/to/dir/name11").name("file1.zip")
                            .createdBy("elis").modifiedBy("elis").build(),
                    new NodeBuilder().nodeId(21113).file(true).repo("repo").path("path/to/dir/name11").name("file2.zip")
                            .createdBy("elis").modifiedBy("elis").build(),
                    new NodeBuilder().nodeId(21114).file(true).repo("repo").path("path/to/dir/name11").name("file3.zip")
                            .createdBy("elis").modifiedBy("elis").build(),
                    new NodeBuilder().nodeId(21115).file(true).repo("repo").path("path/to/dir/name11").name("file4.zip")
                            .createdBy("elis").modifiedBy("elis").build()
            );
            // test
            nodesDao.createBulk(bulkToCreate);

            // verify
            Node firstNode = nodesDao.get(21111);
            Node secondNode = nodesDao.get(21112);
            Node thirdNode = nodesDao.get(21113);
            Node fourthNode = nodesDao.get(21114);
            Node fifthNode = nodesDao.get(21115);

            assertEquals(bulkToCreate.get(0), firstNode);
            assertEquals(bulkToCreate.get(1), secondNode);
            assertEquals(bulkToCreate.get(2), thirdNode);
            assertEquals(bulkToCreate.get(3), fourthNode);
            assertEquals(bulkToCreate.get(4), fifthNode);
        } finally {
            // cleanup
            nodesDao.deleteBulk(List.of(21111L, 21112L, 21113L, 21114L, 21115L));
        }
    }

    @Test
    public void testGetExistingItems() throws SQLException {
        // setup
        List<NodePath> paths = List.of(
                new NodePath("repo1", "", "ant", false),
                new NodePath("repo1", "nonExistingPath", "ant-1.5.jar", true),
                new NodePath("repo1", "ant/ant/1.5", "nonExistingFile", true),
                new NodePath("repo1", "ant/ant/1.5", "ant-1.5.jar", true)
        );

        // test
        List<RepoPath> existingItems = nodesDao.getExistingItems(paths);
        // verify

        assertEquals(existingItems.size(), 2);
        assertTrue(existingItems.contains(paths.get(0).toRepoPath()));
        assertFalse(existingItems.get(existingItems.indexOf(paths.get(0).toRepoPath())).isFile());
        assertTrue(existingItems.contains(paths.get(3).toRepoPath()));
        assertTrue(existingItems.get(existingItems.indexOf(paths.get(3).toRepoPath())).isFile());
    }

    @Test
    public void testDeleteBulk() throws SQLException {
        // setup
        List<Node> bulkToCreate = List.of(
                new NodeBuilder().nodeId(11111).file(false).repo("repo").path("path/to/dir").name("name0")
                        .createdBy("elis").modifiedBy("elis").build(),
                new NodeBuilder().nodeId(11112).file(true).repo("repo").path("path/to/dir/name0").name("file10.zip")
                        .createdBy("elis").modifiedBy("elis").build(),
                new NodeBuilder().nodeId(11113).file(true).repo("repo").path("path/to/dir/name0").name("file20.zip")
                        .createdBy("elis").modifiedBy("elis").build(),
                new NodeBuilder().nodeId(11114).file(true).repo("repo").path("path/to/dir/name0").name("file30.zip")
                        .createdBy("elis").modifiedBy("elis").build(),
                new NodeBuilder().nodeId(11115).file(true).repo("repo").path("path/to/dir/name0").name("file40.zip")
                        .createdBy("elis").modifiedBy("elis").build()
        );
        nodesDao.createBulk(bulkToCreate);

        assertNotNull(nodesDao.get(11111));
        assertNotNull(nodesDao.get(11112));
        assertNotNull(nodesDao.get(11113));
        assertNotNull(nodesDao.get(11114));
        assertNotNull(nodesDao.get(11115));

        List<Long> idsToDelete = List.of(11111L, 11112L, 11113L, 11114L, 11115L);

        // test
        nodesDao.deleteBulk(idsToDelete);
        // verify

        assertNull(nodesDao.get(11111));
        assertNull(nodesDao.get(11112));
        assertNull(nodesDao.get(11113));
        assertNull(nodesDao.get(11114));
        assertNull(nodesDao.get(11115));
    }

    @Test
    public void testAreFiles() throws SQLException {
        // setup
        List<RepoPath> repoPathsToCheck = List.of(
                InternalRepoPathFactory.create("repo1", "ant/ant", false),
                InternalRepoPathFactory.create("repo1", "ant/ant/1.5", false),
                InternalRepoPathFactory.create("repo1", "ant/ant/1.5/ant-1.5.jar", true)
        );

        // test
        Map<RepoPath, Boolean> repoPathBooleanMap = nodesDao.areFiles(repoPathsToCheck);

        // verify
        assertFalse(repoPathBooleanMap.get(repoPathsToCheck.get(0)));
        assertFalse(repoPathBooleanMap.get(repoPathsToCheck.get(1)));
        assertTrue(repoPathBooleanMap.get(repoPathsToCheck.get(2)));
    }

    private Node getById(List<Node> nodes, long id) {
        for (Node node : nodes) {
            if (node.getNodeId() == id) {
                return node;
            }
        }
        return null;
    }

    private void setArtifactoryProperty(ConstantValues property, String query)
            throws InvocationTargetException, IllegalAccessException {
        ArtifactoryHome.get().getArtifactoryProperties().setProperty(property.getPropertyName(), query);
        Method initExternalQueries = ReflectionUtils.findMethod(nodesDao.getClass(), "initExternalQueries");
        initExternalQueries.setAccessible(true);
        initExternalQueries.invoke(nodesDao);
    }

    private void removeArtifactoryProperty(ConstantValues property)
            throws InvocationTargetException, IllegalAccessException {
        ArtifactoryHome.get().getArtifactoryProperties().removeProperty(property);
        Method initExternalQueries = ReflectionUtils.findMethod(nodesDao.getClass(), "initExternalQueries");
        initExternalQueries.setAccessible(true);
        initExternalQueries.invoke(nodesDao);
    }

    @Test
    public void testGetFileNodesBySha1() throws SQLException {
        List<Node> nodes = nodesDao.getFileNodesBySha1(List.of("dddd89fc2a043c2479a6de676a2f7179e9eaddac"));

        Node node = getById(nodes, 21);

        assertNotNull(node);
        assertTrue(node.isFile());
        assertEquals(node.getSha1Actual(), "dddd89fc2a043c2479a6de676a2f7179e9eaddac");
        assertEquals(node.getRepo(), "repo2");
        assertEquals(node.getPath(), "a");
    }

    @Test
    public void testGetFileNodesBySha1NonExistingSha1EmptyListRetrieved() throws SQLException {
        List<Node> nodes = nodesDao.getFileNodesBySha1(List.of("non_existing_sha1"));
        assertTrue(nodes.isEmpty());
    }

    @Test
    public void testGetFileNodesBySha1QueryWithMultipleBulksNodesRetrievedCorrectly()
            throws SQLException, InvocationTargetException, IllegalAccessException {
        setArtifactoryProperty(ConstantValues.dbOperationsBulkSize, "2");

        List<Node> nodes = nodesDao.getFileNodesBySha1(List.of("dddd89fc2a043c2479a6de676a2f7179e9eaddac",
                "dddd89fc2a043c2479a6de676a2f7179e9e11111", "dddd89fc2a043c2479a6de676a2f7179e9e22222",
                "dddd89fc2a043c2479a6de676a2f7179e9e33333", "dddd89fc2a043c2479a6de676a2f7179e9e44444"));

        assertFalse(nodes.isEmpty());
    }

    public void constantValuesNodeHasChildren() throws SQLException, InvocationTargetException, IllegalAccessException {
        boolean hasChildren = nodesDao.hasChildren(new NodePath("non-existing", "path", "dir", false));
        assertFalse(hasChildren);

        hasChildren = nodesDao.hasChildren(new NodePath("repo3", "a", "b", false));
        assertTrue(hasChildren);

        setArtifactoryProperty(ConstantValues.nodesDaoSqlNodeHasChildren,
                "SELECT COUNT(1) FROM nodes WHERE sha256 = 'yop' AND repo = ? AND node_path = ? AND depth = ?");
        hasChildren = nodesDao.hasChildren(new NodePath("repo3", "a", "b", false));
        assertFalse(hasChildren);
        boolean errorOccurred = false;
        try {
            setArtifactoryProperty(ConstantValues.nodesDaoSqlNodeHasChildren, "SELECT repo from nodes");
            nodesDao.hasChildren(new NodePath("repo3", "a", "b", false));
        } catch (Exception e) {
            errorOccurred = true; // expected
        } finally {
            removeArtifactoryProperty(ConstantValues.nodesDaoSqlNodeHasChildren);
        }
        if (!errorOccurred) {
            fail();
        }
    }

    public void constantValuesNodeGetChildren() throws SQLException, InvocationTargetException, IllegalAccessException {
        List<Node> children = nodesDao.getChildren(new NodePath("non-existing", "path", "dir", false));
        assertTrue(children.isEmpty());

        children = nodesDao.getChildren(new NodePath("repo3", "a", "b", false));
        assertEquals(children.size(), 1);

        setArtifactoryProperty(ConstantValues.nodesDaoSqlNodeGetChildren, "SELECT * FROM nodes WHERE " +
                "sha256 = 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy' AND repo = ? " +
                "AND node_path = ? AND depth = ?");
        children = nodesDao.getChildren(new NodePath("repo3", "a", "b", false));
        assertEquals(children.size(), 1);

        setArtifactoryProperty(ConstantValues.nodesDaoSqlNodeGetChildren, "SELECT * FROM nodes WHERE " +
                "sha256 = 'empty' AND repo = ? " +
                "AND node_path = ? AND depth = ?");
        children = nodesDao.getChildren(new NodePath("repo3", "a", "b", false));
        assertTrue(children.isEmpty());
        boolean errorOccurred = false;
        try {
            setArtifactoryProperty(ConstantValues.nodesDaoSqlNodeGetChildren, "SELECT repo from nodes");
            nodesDao.getChildren(new NodePath("repo3", "a", "b", false));
        } catch (Exception e) {
            errorOccurred = true; // expected
        } finally {
            removeArtifactoryProperty(ConstantValues.nodesDaoSqlNodeGetChildren);
        }
        if (!errorOccurred) {
            fail();
        }
    }

    @Test(dataProvider = "nodeSha1Provider")
    public void testExistBySha1(String sha1, boolean expected) throws SQLException {
        assertEquals(nodesDao.existsBySha1(sha1), expected);
    }

    @DataProvider
    public Object[][] nodeSha1Provider() {
        return new Object[][] {
                {"dcab88fc2a043c2479a6de676a2f8179e9ea2167", true},
                {"dddd89fc2a043c2479a6de676a2f7179e9e33322", false}
        };
    }

    @Test(dataProvider = "nodeRepoKeyProvider")
    public void testisRepoContainsFiles(String repoKey, boolean expected) throws SQLException {
        assertEquals(nodesDao.isRepoContainsFiles(repoKey), expected);
    }

    @DataProvider
    public Object[][] nodeRepoKeyProvider() {
        return new Object[][] {
                {"repo2", true},
                {"repo-non-existent", false}
        };
    }

    @Test(dataProvider = "dockerFoldersProvider")
    public void testIsDockerManifestFolder(RepoPath repoPath, long expected) throws SQLException {
        assertEquals(nodesDao.getFolderArtifactsCount(repoPath, List.of("manifest.json", "manifest.list.json")),
                expected);
    }

    @DataProvider
    public Object[][] dockerFoldersProvider() {
        return new Object[][] {
                {RepoPathFactory.create("repo4", "a"), 1},
                {RepoPathFactory.create("repo4", "b"), 0}
        };
    }

    @Test
    public void testGetFullSyncEntriesPagedByCreated_firstPageLimitOne() throws SQLException {
        List<FileListEntry> firstPage = nodesDao.getFullSyncEntriesPaged("repo1", null, 1);
        assertThat(firstPage).hasSize(1);
        assertThat(firstPage.get(0).repo).isEqualTo("repo1");
    }

    @Test
    public void testGetFullSyncEntriesPagedByCreated() throws SQLException {
        // First page: null cursor returns the two entries with the smallest 'created' in repo1
        // (nodes 6 & 7 have created=1340223204457, earlier than all other repo1 nodes)
        List<FileListEntry> firstPage = nodesDao.getFullSyncEntriesPaged("repo1", null, 2);
        assertThat(firstPage).hasSize(2);
        assertThat(firstPage).extracting(e -> e.created).doesNotContainNull();

        // Second page: inclusive created lower bound overlaps first page; full slice still bounded by limit
        FileListEntry lastOnFirst = firstPage.getLast();
        Long afterMillis = Instant.parse(lastOnFirst.created).toEpochMilli();
        List<FileListEntry> secondPage = nodesDao.getFullSyncEntriesPaged("repo1", afterMillis, 100);
        assertThat(secondPage).isNotEmpty();
        assertThat(secondPage).hasSize(19);
    }

    @Test
    public void testGetFullSyncEntriesPagedByCreated_severalNodesSameRepoAndSameCreated_returnsExpectedEntries()
            throws SQLException {
        final String repo = "sameCreatedPagingRepo";
        final long sameCreatedMillis = 8_888_888_888_888L;
        final long rootId = 93001L;
        final List<Long> fileIds = List.of(93002L, 93003L, 93004L, 93005L);
        List<Long> idsToDelete = Stream.concat(Stream.of(rootId), fileIds.stream()).toList();
        try {
            nodesDao.create(new NodeBuilder().nodeId(rootId).file(false).repo(repo).path("").name("")
                    .created(sameCreatedMillis).createdBy("test").modifiedBy("test").build());
            for (int i = 0; i < fileIds.size(); i++) {
                long id = fileIds.get(i);
                nodesDao.create(new NodeBuilder().nodeId(id).file(true).repo(repo).path("").name("f" + i + ".jar")
                        .created(sameCreatedMillis).createdBy("test").modifiedBy("test").build());
            }
            int expectedEntryCount = idsToDelete.size();

            List<FileListEntry> paged = nodesDao.getFullSyncEntriesPaged(repo, null, 50);
            assertThat(paged).hasSize(expectedEntryCount);
            String createdField = paged.getFirst().created;
            assertThat(paged).extracting(e -> e.created).containsOnly(createdField);
            assertThat(paged).extracting(e -> e.repo).containsOnly(repo);
        } finally {
            nodesDao.deleteBulk(idsToDelete);
        }
    }

    /**
     * With {@code created >= ?} paging, every node in this repo shares one millis. Each page is ordered by
     * {@code (created, node_id)} in SQL, so every returned row has the same {@link FileListEntry#created} string; a
     * later page using the last row's millis still matches the same lower bound and therefore overlaps the first page.
     */
    @Test
    public void testGetFullSyncEntriesPaged_sameCreatedMillis_multiplePages_eachRowSharesCreatedWithOverlap()
            throws SQLException {
        final String repo = "sameCreatedMultiPageRepo";
        final long sameCreatedMillis = 7_777_777_777_777L;
        final long rootId = 92001L;
        final List<Long> fileIds = List.of(92002L, 92003L, 92004L, 92005L);
        List<Long> idsToDelete = Stream.concat(Stream.of(rootId), fileIds.stream()).toList();
        try {
            nodesDao.create(new NodeBuilder().nodeId(rootId).file(false).repo(repo).path("").name("")
                    .created(sameCreatedMillis).createdBy("test").modifiedBy("test").build());
            for (int i = 0; i < fileIds.size(); i++) {
                long id = fileIds.get(i);
                nodesDao.create(new NodeBuilder().nodeId(id).file(true).repo(repo).path("").name("m" + i + ".jar")
                        .created(sameCreatedMillis).createdBy("test").modifiedBy("test").build());
            }
            int pageSize = 2;
            List<FileListEntry> firstPage = nodesDao.getFullSyncEntriesPaged(repo, null, pageSize);
            assertThat(firstPage).hasSize(pageSize);
            String createdIso = firstPage.getFirst().created;
            assertThat(firstPage).extracting(e -> e.created).containsOnly(createdIso);

            FileListEntry lastOnFirst = firstPage.getLast();
            Long afterMillis = Instant.parse(lastOnFirst.created).toEpochMilli();
            List<FileListEntry> secondPage = nodesDao.getFullSyncEntriesPaged(repo, afterMillis, pageSize);
            assertThat(secondPage).hasSize(pageSize);
            assertThat(secondPage).extracting(e -> e.created).containsOnly(createdIso);
        } finally {
            nodesDao.deleteBulk(idsToDelete);
        }
    }

    @Test
    public void testHasMoreThanXItems() throws SQLException {
        assertTrue(nodesDao.getNumberOfItemsForPathWithLimit(
                new NodePath("repo1", "", "", false), 2) > 1);
        assertFalse(nodesDao.getNumberOfItemsForPathWithLimit(
                new NodePath("repo3", "org", "yossis", false), 2) > 1);
        assertFalse(nodesDao.getNumberOfItemsForPathWithLimit(
                new NodePath("repo1", "", "", false), 101) > 100);
    }

    @DataProvider(name = "getReposBinarySizeData")
    private Object[][] getReposBinarySizeData() {
        return new Object[][] {
                // repos, shouldBeZero (true if expected to be 0, false if expected to be > 0)
                {Collections.emptySet(), true},
                {Sets.of("non-existent-repo"), true},
                {Sets.of("repo1"), false},
                {Sets.of("repo2"), false},
                {Sets.of("repo1", "repo2"), false},
                {Sets.of("repo-copy"), false},
                {Sets.of("repo1", "repo-copy"), false},
                {Sets.of("repo1", "repo2", "repo3"), false},
                {Sets.of("repo1", "non-existent-repo"), false}
        };
    }

    @Test(dataProvider = "getReposBinarySizeData")
    public void testGetReposBinarySize(Set<String> repos, boolean shouldBeZero) {
        long result = nodesDao.getReposBinarySize(repos);
        if (shouldBeZero) {
            assertEquals(result, 0L,
                    String.format("Distinct binary size for repos %s should be 0", repos));
        } else {
            assertTrue(result > 0,
                    String.format("Distinct binary size for repos %s should be greater than 0, got %d", repos, result));
        }
    }

    @Test
    public void testGetReposBinarySizeWithMixedRepos() {
        // Test with mix of existing and non-existing repos
        Set<String> mixedRepos = Sets.of("repo1", "non-existent-1", "repo2", "non-existent-2", "repo-copy");
        long result = nodesDao.getReposBinarySize(mixedRepos);

        // Should be sum of distinct sizes from repo1, repo2, and repo-copy
        // Note: repo2 files share sha1s with repo1, so distinct size is less than sum
        assertTrue(result >= 0, "Result should be non-negative");
        assertTrue(result <= 846441L + 716139L + 130302L,
                "Result should not exceed sum of individual repo sizes");
    }

    @Test(dependsOnMethods = "createDirectoryNode")
    public void testGetReposBinarySizeOnlyCountsFiles() {
        // Verify that only files (node_type = 1) are counted, folders (node_type = 0) are excluded
        // repo1 has both files and folders, but getReposBinarySize should only count files
        long result = nodesDao.getReposBinarySize(Sets.of("repo1"));

        // Verify that result is greater than 0 (repo1 has files)
        assertTrue(result > 0, "Result should be greater than 0 for repo1 which has files");

        // Verify that adding a repo with only folders doesn't increase the size
        // repo has a folder node (created in createDirectoryNode test), should not affect size
        long resultWithFolderOnlyRepo = nodesDao.getReposBinarySize(Sets.of("repo1", "repo"));
        assertEquals(resultWithFolderOnlyRepo, result,
                "Adding a repo with only folders (node_type = 0) should not increase the binary size");

        // Verify that a repo with only folders returns 0
        long folderOnlyRepoResult = nodesDao.getReposBinarySize(Sets.of("repo"));
        assertEquals(folderOnlyRepoResult, 0L,
                "Repo with only folders (node_type = 0) should return 0");
    }

    @Test
    public void testGetManifestPropertiesIfExist_emptyInputs() throws SQLException {
        Map<String, List<NodeProperty>> result =
                nodesDao.getPropertiesForFiles(RepoPathFactory.create("repo1", "test"), Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetPropertiesForFiles_noFilesFound() throws SQLException {
        RepoPath folderPath = RepoPathFactory.create("repo1", "org/yossis");
        List<String> files = List.of("manifest.json", "list.manifest.json");
        Map<String, List<NodeProperty>> result = nodesDao.getPropertiesForFiles(folderPath, files);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetPropertiesForFiles_withFileAndProperties() throws SQLException {
        RepoPath folderPath = RepoPathFactory.create("repo1", "ant/ant/1.5");
        List<String> files = List.of("ant-1.5.jar");
        Map<String, List<NodeProperty>> result = nodesDao.getPropertiesForFiles(folderPath, files);

        assertFalse(result.isEmpty(), "Should find manifest file");
        assertTrue(result.containsKey("ant-1.5.jar"), "Should contain ant-1.5.jar");

        List<NodeProperty> properties = result.get("ant-1.5.jar");
        assertNotNull(properties, "Properties should not be null");
        assertEquals(properties.size(), 2, "Should have exactly 2 properties (build.name and build.number)");
        assertTrue(properties.stream().anyMatch(p -> "build.name".equals(p.getPropKey())),
                "Should contain build.name property");
        assertTrue(properties.stream().anyMatch(p -> "build.number".equals(p.getPropKey())),
                "Should contain build.number property");
    }

    @Test
    public void testGetManifestPropertiesIfExist_nonExistentFolder() throws SQLException {
        RepoPath folderPath = RepoPathFactory.create("repo1", "non/existent/path");
        List<String> files = List.of("manifest.json");
        Map<String, List<NodeProperty>> result = nodesDao.getPropertiesForFiles(folderPath, files);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetManifestPropertiesIfExist_multipleFiles() throws SQLException {
        RepoPath folderPath = RepoPathFactory.create("repo4", "a");
        List<String> files = List.of("manifest.json", "list.manifest.json");

        Map<String, List<NodeProperty>> result = nodesDao.getPropertiesForFiles(folderPath, files);

        assertNotNull(result);
        if (result.size() > 1) {
            assertTrue(result.containsKey("manifest.json") || result.containsKey("list.manifest.json"),
                    "Should contain at least one manifest file");
        }
    }

    @Test
    public void testGetManifestPropertiesIfExist_multipleFilesWithProps() throws SQLException {
        RepoPath folderPath = RepoPathFactory.create("repo1", "org/yossis/tools");
        List<String> files = List.of("test.bin", "file2.pom", "file3.pom");

        Map<String, List<NodeProperty>> result = nodesDao.getPropertiesForFiles(folderPath, files);

        assertNotNull(result);
        assertEquals(result.size(), 3, "Should find all 3 files");
        assertTrue(result.containsKey("test.bin"), "Should contain test.bin");
        assertTrue(result.containsKey("file2.pom"), "Should contain file2.pom");
        assertTrue(result.containsKey("file3.pom"), "Should contain file3.pom");

        assertTrue(result.get("test.bin").isEmpty(), "test.bin should have no properties");
        assertTrue(result.get("file2.pom").isEmpty(), "file2.pom should have no properties");
        assertFalse(result.get("file3.pom").isEmpty(), "file3.pom should have properties");
        assertEquals(result.get("file3.pom").size(), 1, "file3.pom should have exactly 1 property");
        assertEquals(result.get("file3.pom").getFirst().getPropKey(), "trash.time",
                "file3.pom property should be trash.time");
    }

    private static FileListEntry expectedFileListEntry(String path,
            String name, int depth, String propsModified, String propsMd5) {
        FileListEntry entry = new FileListEntry();
        entry.type = AqlItemTypeEnum.folder.signature;
        entry.repo = "repo1";
        entry.path = path;
        entry.name = name;
        entry.depth = depth;
        entry.modified = "2012-06-21T12:53:24.448Z";
        entry.created = "2012-06-21T12:53:24.448Z";
        entry.sha256 = null;
        entry.size = 0L;
        entry.itemPropsModified = propsModified;
        entry.itemPropsMd5 = propsMd5;
        return entry;
    }

    @Test
    public void testCreateIfNotExists_insertsNewNode() throws SQLException {
        NodeBuilder b = new NodeBuilder().nodeId(900).file(false)
                .repo("repo").path("conditional").name("new-folder")
                .createdBy("test").modifiedBy("test");
        Node node = b.build();

        int result = nodesDao.createIfNotExists(node);

        assertEquals(result, 1);
        Node loaded = nodesDao.get(node.getNodePath());
        assertNotNull(loaded);
        assertEquals(loaded.getNodeId(), 900);

        nodesDao.delete(900);
    }

    @Test
    public void testCreateIfNotExists_duplicateReturnsZero() throws SQLException {
        DbType dbType = dbProperties.getDbType();
        if (dbType == DbType.MYSQL || dbType == DbType.MARIADB) {
            return; // no unique index on (repo, node_path, node_name), the test is not relevant for these DB types
        }

        NodeBuilder b = new NodeBuilder().nodeId(901).file(false)
                .repo("repo").path("conditional").name("dup-folder")
                .createdBy("test").modifiedBy("test");
        nodesDao.create(b.build());

        Node duplicate = new NodeBuilder().nodeId(902).file(false)
                .repo("repo").path("conditional").name("dup-folder")
                .createdBy("test").modifiedBy("test")
                .build();

        int result = nodesDao.createIfNotExists(duplicate);

        assertEquals(result, 0, "Duplicate should return 0");
        Node loaded = nodesDao.get(duplicate.getNodePath());
        assertNotNull(loaded);
        assertEquals(loaded.getNodeId(), 901,
                "Original node ID should be preserved");

        nodesDao.delete(901);
    }

    @Test
    public void testCreateIfNotExists_afterDeleteAllowsReinsert() throws SQLException {
        NodeBuilder b = new NodeBuilder().nodeId(903).file(false)
                .repo("repo").path("conditional").name("reinsert-folder")
                .createdBy("test").modifiedBy("test");
        nodesDao.createIfNotExists(b.build());

        nodesDao.delete(903);
        assertFalse(nodesDao.exists(b.build().getNodePath()));

        Node reinserted = new NodeBuilder().nodeId(904).file(false)
                .repo("repo").path("conditional").name("reinsert-folder")
                .createdBy("test").modifiedBy("test")
                .build();
        int result = nodesDao.createIfNotExists(reinserted);

        assertEquals(result, 1);
        Node loaded = nodesDao.get(reinserted.getNodePath());
        assertNotNull(loaded);
        assertEquals(loaded.getNodeId(), 904);

        nodesDao.delete(904);
    }

}