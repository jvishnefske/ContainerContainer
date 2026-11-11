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

package org.artifactory.storage.db.fs.itest.service;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FolderInfoImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.fs.dao.NodeMetaInfoDao;
import org.artifactory.storage.db.fs.service.NodeMetaInfoServiceImpl;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.fs.VfsException;
import org.artifactory.storage.fs.service.ItemMetaInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.sql.SQLException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * DB Tests the {@link org.artifactory.storage.db.fs.service.NodeMetaInfoServiceImpl}.
 *
 * @author Yossi Shaul
 */
@Test
public class NodeMetaInfoServiceImplTest extends DbBaseTest {

    @Autowired
    private NodeMetaInfoServiceImpl nodeMetaService;

    @BeforeClass
    public void setup() {
        importSql("/sql/nodes-for-service.sql");
    }

    public void hasNodeMetadata() throws Exception {
        assertTrue(nodeMetaService.hasNodeMetadata(new RepoPathImpl("repo1", "org/yossis")));
    }

    public void hasNodeMetadataNodeWithNoProps() throws Exception {
        assertFalse(nodeMetaService.hasNodeMetadata(new RepoPathImpl("repo2", "")));
    }

    public void getNodeMetadata() throws SQLException {
        ItemMetaInfo metaInfo = nodeMetaService.getNodeMetaInfo(new RepoPathImpl("repo1", "org/yossis"));
        assertNotNull(metaInfo);
        assertEquals(metaInfo.getPropsModified(), 1340286203121L);
        assertEquals(metaInfo.getPropsModifiedBy(), "yoyo");
    }

    public void getNodeMetadataNoSuchNode() throws SQLException {
        assertNull(nodeMetaService.getNodeMetaInfo(new RepoPathImpl("repo7", "no/item/here")));
    }

    public void getNodeMetadataNoMetadata() throws SQLException {
        assertNull(nodeMetaService.getNodeMetaInfo(new RepoPathImpl("repo2", "")));
    }

    public void getNodeMetadataItemInfoWithId() throws SQLException {
        ItemMetaInfo metaInfo = nodeMetaService.getNodeMetaInfo(
                new FolderInfoImpl(new RepoPathImpl("repo1", "org/yossis"), 7));
        assertNotNull(metaInfo);
        assertEquals(metaInfo.getPropsModified(), 1340286203121L);
        assertEquals(metaInfo.getPropsModifiedBy(), "yoyo");
    }

    public void getNodeMetadataItemInfoWithoutId() throws SQLException {
        ItemMetaInfo metaInfo = nodeMetaService.getNodeMetaInfo(
                new FolderInfoImpl(new RepoPathImpl("repo1", "org/yossis")));
        assertNotNull(metaInfo);
        assertEquals(metaInfo.getPropsModified(), 1340286203121L);
        assertEquals(metaInfo.getPropsModifiedBy(), "yoyo");
    }

    public void getNodeMetadataNoSuchItemInfo() throws SQLException {
        assertNull(nodeMetaService.getNodeMetaInfo(new FolderInfoImpl(new RepoPathImpl("repo7",
                "no/item/here"))));
    }

    public void getNodeMetadataItemInfoNoMetadata() throws SQLException {
        assertNull(nodeMetaService.getNodeMetaInfo(new FolderInfoImpl(new RepoPathImpl("repo2",
                "3b20a5bcefcd4c16d8aa45930429d78a"), 500)));
    }

    public void insertMetadata() throws SQLException {
        long time = System.currentTimeMillis();
        ItemMetaInfo toInsert = new ItemMetaInfo(time, "me", "3b20a5bcefcd4c16d8aa45930429d78a");
        // insert new
        nodeMetaService.createOrUpdateNodeMetaInfo(6, toInsert);
        ItemMetaInfo fromDb = nodeMetaService.getNodeMetaInfo(new RepoPathImpl("repo1", "org"));
        assertTrue(EqualsBuilder.reflectionEquals(toInsert, fromDb));

        // update existing
        ItemMetaInfo toUpdate = new ItemMetaInfo(time + 1000, "another",
                "3b20a5bcefcd4c16d8aa45930429d78a");
        nodeMetaService.createOrUpdateNodeMetaInfo(6, toUpdate);
        fromDb = nodeMetaService.getNodeMetaInfo(new RepoPathImpl("repo1", "org"));
        assertTrue(EqualsBuilder.reflectionEquals(toUpdate, fromDb));
    }

    @Test(expectedExceptions = VfsException.class)
    public void insertMetadataNonExistentNode() throws SQLException {
        nodeMetaService.createOrUpdateNodeMetaInfo(8989, new ItemMetaInfo(System.currentTimeMillis(),
                "me", "3b20a5bcefcd4c16d8aa45930429d78a"));
    }

    public void deleteMetadata() throws SQLException {
        RepoPath pathWithMeta = new RepoPathImpl("repo1", "ant/ant/1.5/ant-1.5.jar");
        assertTrue(nodeMetaService.hasNodeMetadata(pathWithMeta));
        nodeMetaService.deleteMetaInfo(pathWithMeta);
        assertFalse(nodeMetaService.hasNodeMetadata(pathWithMeta));
    }

    public void deleteMetadataNotExist() throws SQLException {
        RepoPath pathWithNoMeta = new RepoPathImpl("repo2", "org/jfrog/test/test2.jar");
        assertFalse(nodeMetaService.hasNodeMetadata(pathWithNoMeta));
        nodeMetaService.deleteMetaInfo(pathWithNoMeta);
        assertFalse(nodeMetaService.hasNodeMetadata(pathWithNoMeta));
    }

    public void updateModifiedProps() {
        ItemMetaInfo toInsert = new ItemMetaInfo(12345678L, "me", "3b20a5bcefcd4c16d8aa45930429d78a");
        // insert new
        nodeMetaService.createOrUpdateNodeMetaInfo(6, toInsert);
        ItemMetaInfo nodeMetaInfoBefore = nodeMetaService.getNodeMetaInfo(new RepoPathImpl("repo1", "org"));
        assert nodeMetaInfoBefore != null;
        nodeMetaService.updateNodePropertiesModified(6, nodeMetaInfoBefore.getPropsModified() + 1L);
        ItemMetaInfo nodeMetaInfoAfter = nodeMetaService.getNodeMetaInfo(new RepoPathImpl("repo1", "org"));
        assert nodeMetaInfoAfter != null;
        assertEquals(nodeMetaInfoAfter.getPropsModifiedBy(), nodeMetaInfoBefore.getPropsModifiedBy());
        assertEquals(nodeMetaInfoAfter.getMd5(), nodeMetaInfoBefore.getMd5());
        assertEquals(nodeMetaInfoAfter.getPropsModified(), nodeMetaInfoBefore.getPropsModified() + 1L);
    }

    public void updateModifiedPropsSqlException() throws NoSuchFieldException, SQLException, IllegalAccessException {
        NodeMetaInfoDao nodeMetaInfoDao = mock(NodeMetaInfoDao.class);
        when(nodeMetaInfoDao.updatePropsModified(6L, 12345678L)).thenThrow(SQLException.class);
        NodeMetaInfoServiceImpl metaInfoService = new NodeMetaInfoServiceImpl();
        Field nodeMetaInfoDaoField = NodeMetaInfoServiceImpl.class.getDeclaredField("nodeMetaInfoDao");
        nodeMetaInfoDaoField.setAccessible(true);
        nodeMetaInfoDaoField.set(metaInfoService, nodeMetaInfoDao);

        assertThrows("Failed to update node metadata info props modified", VfsException.class,
                () -> metaInfoService.updateNodePropertiesModified(6L, 12345678L));
    }
}
