/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.storage.db.util;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeStub;
import org.jfrog.storage.DbType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tamir Hadad
 */
@Test
public class BaseDaoTest {

    private static final int DEFAULT_INDEX_MAX_VALUE_SIZE = 4000;

    public ArtifactoryContext init() {
        ArtifactoryHome artifactoryHome = new ArtifactoryHomeStub(getClass().getName());
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryHome.bind(artifactoryHome);
        ArtifactoryContextThreadBinder.bind(context);
        return context;
    }

    @AfterMethod
    public void cleanup() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    public void testPostgresIndexedMaxSize() {
        ArtifactoryContext context = init();
        ArtifactoryDbProperties artifactoryDbProperties = mock(ArtifactoryDbProperties.class);
        when(context.beanForType(ArtifactoryDbProperties.class)).thenReturn(artifactoryDbProperties);
        when(artifactoryDbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        BaseDao baseDao = new BaseDao(jdbcHelper);
        int dbIndexedValueMaxSize = baseDao.getDbIndexedValueMaxSize(DEFAULT_INDEX_MAX_VALUE_SIZE);
        assertThat(dbIndexedValueMaxSize).isEqualTo(ConstantValues.dbPostgresPropertyValueMaxSize.getInt());
    }

    public void testMssqlIndexedMaxSize() {
        ArtifactoryContext context = init();
        ArtifactoryDbProperties artifactoryDbProperties = mock(ArtifactoryDbProperties.class);
        when(context.beanForType(ArtifactoryDbProperties.class)).thenReturn(artifactoryDbProperties);
        when(artifactoryDbProperties.getDbType()).thenReturn(DbType.MSSQL);
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        BaseDao baseDao = new BaseDao(jdbcHelper);
        int dbIndexedValueMaxSize = baseDao.getDbIndexedValueMaxSize(DEFAULT_INDEX_MAX_VALUE_SIZE);
        assertThat(dbIndexedValueMaxSize).isEqualTo(ConstantValues.dbMsSqlPropertyValueMaxSize.getInt());
    }

    public void testMysqlIndexedMaxSize() {
        ArtifactoryContext context = init();
        ArtifactoryDbProperties artifactoryDbProperties = mock(ArtifactoryDbProperties.class);
        when(context.beanForType(ArtifactoryDbProperties.class)).thenReturn(artifactoryDbProperties);
        when(artifactoryDbProperties.getDbType()).thenReturn(DbType.MYSQL);
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        BaseDao baseDao = new BaseDao(jdbcHelper);
        int dbIndexedValueMaxSize = baseDao.getDbIndexedValueMaxSize(DEFAULT_INDEX_MAX_VALUE_SIZE);
        assertThat(dbIndexedValueMaxSize).isEqualTo(ConstantValues.dbMySqlPropertyValueMaxSize.getInt());
    }

    public void testDefaultIsReturnedOnNullDBType() {
        ArtifactoryContext context = init();
        ArtifactoryDbProperties artifactoryDbProperties = mock(ArtifactoryDbProperties.class);
        when(context.beanForType(ArtifactoryDbProperties.class)).thenReturn(artifactoryDbProperties);
        when(artifactoryDbProperties.getDbType()).thenReturn(null);
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        BaseDao baseDao = new BaseDao(jdbcHelper);
        int dbIndexedValueMaxSize = baseDao.getDbIndexedValueMaxSize(DEFAULT_INDEX_MAX_VALUE_SIZE);
        assertThat(dbIndexedValueMaxSize).isEqualTo(DEFAULT_INDEX_MAX_VALUE_SIZE);
    }

    public void testDerbyIndexedMaxSize() {
        ArtifactoryContext context = init();
        ArtifactoryDbProperties artifactoryDbProperties = mock(ArtifactoryDbProperties.class);
        when(context.beanForType(ArtifactoryDbProperties.class)).thenReturn(artifactoryDbProperties);
        when(artifactoryDbProperties.getDbType()).thenReturn(DbType.DERBY);
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        BaseDao baseDao = new BaseDao(jdbcHelper);
        int dbIndexedValueMaxSize = baseDao.getDbIndexedValueMaxSize(DEFAULT_INDEX_MAX_VALUE_SIZE);
        assertThat(dbIndexedValueMaxSize).isEqualTo(DEFAULT_INDEX_MAX_VALUE_SIZE);
    }
}