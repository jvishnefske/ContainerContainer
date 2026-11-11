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

package org.artifactory.rest.resource.system;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;

import jakarta.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Shay Bagants
 */
public class PsqlResourceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private PropertiesService propertiesService;
    @Mock
    private ArtifactoryJdbcHelper jdbcHelper;
    @Mock
    private JFrogDataSource dataSource;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private ArtifactoryDbProperties dbProperties;
    @Mock
    private Connection connection;
    @Mock
    private Statement statement;
    @Mock
    private DatabaseMetaData databaseMetaData;
    @Mock
    private AolService aolService;
    AutoCloseable closeable;

    @BeforeEach
    public void beforeClass() throws SQLException {
        closeable = MockitoAnnotations.openMocks(this);
        when(artifactoryContext.beanForType(ArtifactoryDbProperties.class)).thenReturn(dbProperties);
        when(artifactoryContext.beanForType(ArtifactoryJdbcHelper.class)).thenReturn(jdbcHelper);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        when(jdbcHelper.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    public void cleanup() throws Exception {
        closeable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void fixIndex(boolean isPsqlDb) throws SQLException {
        PsqlResource psqlResource = spy(new PsqlResource(propertiesService));
        when(dbProperties.getDbType()).thenReturn(isPsqlDb ? DbType.POSTGRESQL : DbType.DERBY);
        doReturn(List.of(ConverterApplyStatus.COMPLETED, ConverterApplyStatus.COMPLETED)).when(psqlResource)
                .executeConverter();
        boolean fixIndex = psqlResource.fixIndex();
        assertEquals(fixIndex, isPsqlDb);

        VerificationMode times = times(isPsqlDb ? 2 : 0);
        verify(psqlResource, times).isCompleted(any(ConverterApplyStatus.class));
    }

    @Test
    public void fixIndexConvertFailed() throws SQLException {
        PsqlResource psqlResource = spy(new PsqlResource(propertiesService));
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        doReturn(List.of(ConverterApplyStatus.FAILED)).when(psqlResource).executeConverter();
        boolean fixIndex = psqlResource.fixIndex();
        assertFalse(fixIndex);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void handleAndBuildResponse(boolean isAol) {
        PsqlResource psqlResource = spy(new PsqlResource(propertiesService));
        when(propertiesService.trimPropertyValuesToAllowedLength()).thenReturn(1);
        when(aolService.isAol()).thenReturn(isAol);
        doReturn(true).when(psqlResource).fixIndex();
        if (isAol) {
            Response response = psqlResource.handleAndBuildResponse(0, Map.of());
            PostgresqlPropTrimAndIndexStatusModel entity = (PostgresqlPropTrimAndIndexStatusModel) response.getEntity();
            assertEquals(200, response.getStatus());
            assertFalse(entity.getIndexFixed());
            assertTrue(entity.getError().contains("SaaS environment"));
        } else {
            Response response = psqlResource.handleAndBuildResponse(0, Map.of());
            PostgresqlPropTrimAndIndexStatusModel entity = (PostgresqlPropTrimAndIndexStatusModel) response.getEntity();
            assertEquals(200, response.getStatus());
            assertTrue(entity.getIndexFixed());
            assertNull(entity.getError());
        }

    }
}