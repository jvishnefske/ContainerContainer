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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.artifactory.storage.db.fs.dao.NodeMetaInfoDao;
import org.artifactory.storage.db.fs.entity.NodeMetaInfo;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link org.artifactory.storage.db.fs.dao.NodeMetaInfoDao}.
 *
 * @author Yossi Shaul
 */
@Test
public class NodeMetaInfosDaoTest extends DbBaseTest {

    @Autowired
    private NodeMetaInfoDao metaDao;

    @BeforeClass
    public void setup() {
        importSql("/sql/nodes.sql");
    }

    public void hasNodeMetadata() throws Exception {
        assertTrue(metaDao.hasNodeMetadata(5));
    }

    public void hasNodeMetadataNodeWithNoProps() throws Exception {
        assertFalse(metaDao.hasNodeMetadata(2));
    }

    public void getNodeMetadata() throws SQLException {
        NodeMetaInfo nodeMetadata = metaDao.getNodeMetadata(5);
        assertNotNull(nodeMetadata);
        assertEquals(nodeMetadata.getNodeId(), 5);
        assertEquals(nodeMetadata.getPropsModified(), 1340286103555L);
        assertEquals(nodeMetadata.getPropsModifiedBy(), "yossis");
    }

    public void getNodeMetadataNoSuchNode() throws SQLException {
        assertNull(metaDao.getNodeMetadata(748378));
    }

    public void getNodeMetadataNoMetadata() throws SQLException {
        assertNull(metaDao.getNodeMetadata(2));
    }

    public void insertMetadata() throws SQLException {
        long time = System.currentTimeMillis();
        NodeMetaInfo toInsert = new NodeMetaInfo(6, time, "me",
                "3b20a5bcefcd4c16d8aa45930429d78a");
        metaDao.create(toInsert);

        NodeMetaInfo fromDb = metaDao.getNodeMetadata(toInsert.getNodeId());

        assertTrue(EqualsBuilder.reflectionEquals(toInsert, fromDb));
    }

    @Test(expectedExceptions = SQLException.class)
    public void insertMetadataNonExistentNode() throws SQLException {
        metaDao.create(new NodeMetaInfo(6, System.currentTimeMillis(), "me",
                "3b20a5bcefcd4c16d8aa45930429d78a"));
    }

    public void updateMetadata() throws SQLException {
        NodeMetaInfo metaInfo = new NodeMetaInfo(4, System.currentTimeMillis(), "someone",
                "3b20a5bcefcd4c16d8aa45930429d78a");
        metaDao.create(metaInfo);

        NodeMetaInfo toUpdate = new NodeMetaInfo(4, 12345678L, "someoneelse",
                "3b20a5bcefcd4c16d8aa45930429d78a");
        metaDao.update(toUpdate);

        NodeMetaInfo updatedMetaInfo = metaDao.getNodeMetadata(4);
        assertNotNull(updatedMetaInfo);
        assertTrue(EqualsBuilder.reflectionEquals(toUpdate, updatedMetaInfo));
    }

    public void updateMetadataNotExist() throws SQLException {
        assertEquals(0, metaDao.update(new NodeMetaInfo(8989439, 12345678L,
                "someone", "3b20a5bcefcd4c16d8aa45930429d78a")));
    }

    public void deleteMetadata() throws SQLException {
        assertTrue(metaDao.hasNodeMetadata(9));
        assertEquals(1, metaDao.deleteNodeMeta(9));
        assertFalse(metaDao.hasNodeMetadata(9));
    }

    public void deleteMetadataNotExist() throws SQLException {
        assertEquals(0, metaDao.deleteNodeMeta(11));
    }

    public void updateMetadataPropsModified() throws SQLException {
        NodeMetaInfo nodeMetadataBefore = metaDao.getNodeMetadata(5);
        assert nodeMetadataBefore != null;
        assertEquals(metaDao.updatePropsModified(5, nodeMetadataBefore.getPropsModified() + 1L), 1);
        NodeMetaInfo nodeMetadataAfter = metaDao.getNodeMetadata(5);
        assert nodeMetadataAfter != null;
        assertEquals(nodeMetadataAfter.getPropsMd5(), nodeMetadataBefore.getPropsMd5());
        assertEquals(nodeMetadataAfter.getPropsModifiedBy(), nodeMetadataBefore.getPropsModifiedBy());
        assertEquals(nodeMetadataAfter.getPropsModified(), nodeMetadataBefore.getPropsModified() + 1L);
    }

}
