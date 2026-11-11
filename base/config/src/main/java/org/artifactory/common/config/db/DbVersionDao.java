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

package org.artifactory.common.config.db;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.jfrog.storage.JdbcHelper;

import java.sql.SQLException;

import static org.artifactory.common.config.db.DbVersionUtil.DB_PROPERTIES_TABLE;

/**
 * @author Dan Feldman
 */
public class DbVersionDao {

    private JdbcHelper jdbcHelper;

    public DbVersionDao(JdbcHelper jdbcHelper) {
        this.jdbcHelper = jdbcHelper;
    }

    public DbVersionInfo getDbServiceVersion() {
        return DbVersionUtil.getVersion(jdbcHelper);
    }

    public boolean isDbPropertiesTableExists() {
        try {
            return DbVersionUtil.isDbPropertiesTableExists(jdbcHelper,
                    ArtifactoryHome.get().getDBProperties().getDbType());
        } catch (SQLException e) {
            throw new IllegalStateException("Could not find database table: " + DB_PROPERTIES_TABLE, e);
        }
    }

    public boolean isDbPropertiesEmpty() {
        return DbVersionUtil.isDbPropertiesEmpty(jdbcHelper);
    }

    public boolean createVersion(DbVersionInfo dbVersionInfo) {
        try {
            return DbVersionUtil.createVersion(jdbcHelper, dbVersionInfo);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert version into " + DB_PROPERTIES_TABLE, e);
        }
    }
}
