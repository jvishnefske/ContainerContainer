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

package org.artifactory.storage.db.properties.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.config.db.DbVersionUtil;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Nonnull;
import java.sql.SQLException;
import java.util.List;

/**
 * Date: 7/10/13 3:08 PM
 *
 * @author freds
 */
@Repository
public class DbPropertiesDao extends BaseDao {

    private ArtifactoryDbProperties dbProperties;

    @Autowired
    public DbPropertiesDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper);
        this.dbProperties = dbProperties;
    }

    @Nonnull
    public List<DbVersionInfo> getProperties() throws SQLException {
        return DbVersionUtil.getVersions(jdbcHelper);
    }

    public boolean createProperties(DbVersionInfo dbProperties) throws SQLException {
        return DbVersionUtil.createVersion(jdbcHelper, dbProperties);
    }

    public boolean isDbPropertiesTableExists() throws SQLException {
        return DbVersionUtil.isDbPropertiesTableExists(jdbcHelper, dbProperties.getDbType());

    }

    public boolean deleteVersion(DbVersionInfo dbVersionInfo) throws SQLException {
        return DbVersionUtil.deleteVersion(jdbcHelper, dbVersionInfo);
    }
}
