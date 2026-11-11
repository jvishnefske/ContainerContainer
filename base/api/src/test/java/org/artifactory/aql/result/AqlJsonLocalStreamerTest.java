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

package org.artifactory.aql.result;

import org.artifactory.aql.action.AqlFindAction;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AqlJsonLocalStreamerTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AqlPermissionProvider permissionProvider;
    @Mock
    private AqlRepoProvider repoProvider;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        setStringSystemProperty(ConstantValues.inflateRowEnhancementEnabled, "true");
        when(permissionProvider.isAdmin()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Smoke test for the production hot path: ResultSet row -> InflatableRow.inflate() -> readNext().
     * Multi-row merge (same item, different properties) is covered in InflatableRowTest via Row.merge;
     * a two-row streamer test is unreliable because resolveId(items) iterates the full HashMap, so
     * property columns can change composite key string order across separate InflatableRow instances.
     */
    @Test
    void readNext_shouldInflateItemAndPropertyDomains_whenSingleDbRowReturned() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        stubSingleItemPropertyRow(resultSet);

        @SuppressWarnings("unchecked")
        AqlLazyResult<AqlRowResult> lazyResult = mock(AqlLazyResult.class);
        when(lazyResult.getPermissionProvider()).thenReturn(permissionProvider);
        when(lazyResult.getRepoProvider()).thenReturn(repoProvider);
        when(lazyResult.getResultSet()).thenReturn(resultSet);
        when(lazyResult.getLimit()).thenReturn(100L);
        when(lazyResult.getOffset()).thenReturn(0L);
        when(lazyResult.getDomain()).thenReturn(AqlDomainEnum.items);
        when(lazyResult.getAction()).thenReturn(new AqlFindAction());
        when(lazyResult.getFields()).thenReturn(buildItemPropertyFields());

        try (AqlJsonLocalStreamer streamer = new AqlJsonLocalStreamer(lazyResult, null, false)) {
            Row row = streamer.readNext();

            assertNotNull(row);
            assertEquals("libs-local", row.itemRepo);
            assertEquals(100L, row.itemId);
            assertEquals(1, row.subDomains.size());
            Row propertyRow = row.subDomains.values().iterator().next();
            assertEquals(AqlDomainEnum.properties, propertyRow.domain);
            assertEquals("build.number", propertyRow.propertyKey);
            assertEquals("42", propertyRow.propertyValue);
            assertEquals(7L, propertyRow.propertyId);
            assertNull(streamer.readNext());
        }
    }

    private static List<DomainSensitiveField> buildItemPropertyFields() {
        List<AqlDomainEnum> itemsPath = List.of(AqlDomainEnum.items);
        List<AqlDomainEnum> itemPropertyPath = List.of(AqlDomainEnum.items, AqlDomainEnum.properties);
        return List.of(
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo, itemsPath),
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemId, itemsPath),
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemPath, itemsPath),
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemName, itemsPath),
                new DomainSensitiveField(AqlPhysicalFieldEnum.propertyKey, itemPropertyPath),
                new DomainSensitiveField(AqlPhysicalFieldEnum.propertyValue, itemPropertyPath),
                new DomainSensitiveField(AqlPhysicalFieldEnum.propertyId, itemPropertyPath)
        );
    }

    private static void stubSingleItemPropertyRow(ResultSet resultSet) throws SQLException {
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemRepo.name())).thenReturn("libs-local");
        when(resultSet.getString(AqlPhysicalFieldEnum.itemPath.name())).thenReturn(".");
        when(resultSet.getString(AqlPhysicalFieldEnum.itemName.name())).thenReturn("artifact.jar");
        when(resultSet.getLong(AqlPhysicalFieldEnum.itemId.name())).thenReturn(100L);
        when(resultSet.getString(AqlPhysicalFieldEnum.propertyKey.name())).thenReturn("build.number");
        when(resultSet.getString(AqlPhysicalFieldEnum.propertyValue.name())).thenReturn("42");
        when(resultSet.getLong(AqlPhysicalFieldEnum.propertyId.name())).thenReturn(7L);
    }
}
