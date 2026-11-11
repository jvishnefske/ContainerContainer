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

package org.artifactory.storage.db.fs.dao;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.fs.entity.NodeMetaInfo;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.util.DbUtils;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A data access table for the node meta infos table.
 *
 * @author Yossi Shaul
 */
@Repository
@Slf4j
public class NodeMetaInfoDao extends BaseDao {

    private final ArtifactoryDbProperties dbProperties;

    @Autowired
    public NodeMetaInfoDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper);
        this.dbProperties = dbProperties;
    }

    public boolean hasNodeMetadata(long nodeId) throws SQLException {
        ResultSet resultSet = null;
        QueryWriter buildsQuery = new QueryWriter(dbProperties.getDbType());
        buildsQuery.select("1 as count")
                .from("node_meta_infos")
                .where("node_id = ?")
                .limit(1L);
        try {
            resultSet = jdbcHelper.executeSelect(buildsQuery.build(), nodeId);
            return resultSet.next();
        } finally {
            DbUtils.close(resultSet);
        }
    }

    @Nullable
    public NodeMetaInfo getNodeMetadata(long nodeId) throws SQLException {
        ResultSet resultSet = null;
        try {
            // the child path must be the path+name of the parent
            resultSet = jdbcHelper.executeSelect("SELECT * FROM node_meta_infos WHERE node_id = ?", nodeId);
            if (resultSet.next()) {
                return metaInfosFromResultSet(resultSet);
            }
            return null;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public int deleteNodeMeta(long nodeId) throws SQLException {
        return jdbcHelper.executeUpdate("DELETE FROM node_meta_infos WHERE node_id = ?", nodeId);
    }

    public int create(NodeMetaInfo metaInfo) throws SQLException {
        return jdbcHelper.executeUpdate("INSERT INTO node_meta_infos " +
                        "(node_id, props_modified, props_modified_by, props_md5) " +
                        "VALUES(?, ?, ?, ?)",
                metaInfo.getNodeId(), metaInfo.getPropsModified(), metaInfo.getPropsModifiedBy(),
                metaInfo.getPropsMd5());
    }

    public int update(NodeMetaInfo metaInfo) throws SQLException {
        return jdbcHelper.executeUpdate("UPDATE node_meta_infos SET props_modified = ?, props_modified_by = ?, " +
                        "props_md5 = ? " +
                        "WHERE node_id = ?", metaInfo.getPropsModified(), metaInfo.getPropsModifiedBy(),
                metaInfo.getPropsMd5(), metaInfo.getNodeId());
    }

    /**
     * Update the entry of the node id with the updated propsModified value in node_meta_infos table
     * @param nodeId The node id we want to update
     * @param propsModified The updated propsModified value
     */
    public int updatePropsModified(long nodeId ,long propsModified) throws SQLException {
        return jdbcHelper.executeUpdate("UPDATE node_meta_infos SET props_modified = ? " +
                        "WHERE node_id = ?", propsModified, nodeId);
    }

    private NodeMetaInfo metaInfosFromResultSet(ResultSet resultSet) throws SQLException {
        long nodeId = resultSet.getLong(1);
        long propModified = resultSet.getLong(2);
        String propModifiedBy = resultSet.getString(3);
        String propsMd5 = resultSet.getString(4);
        return new NodeMetaInfo(nodeId, propModified, propModifiedBy, propsMd5);
    }

}
