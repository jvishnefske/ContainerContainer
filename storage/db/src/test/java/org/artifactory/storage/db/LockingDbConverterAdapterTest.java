package org.artifactory.storage.db;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.artifactory.storage.db.version.converter.LockingDBSqlConverter;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.converter.DbDetails;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ConverterExecutionType;
import org.jfrog.converter.util.DbConverterUtil;
import org.jfrog.storage.DbType;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Oleksandr Legenkiy
 */
public class LockingDbConverterAdapterTest {

    @Test
    public void testGetDbVersions() {
        DatabaseConverterAdapter adapter =
                new LockingDBConverterAdapter(mock(ArtifactoryHome.class), mock(DbDetails.class));
        DbVersion[] dbVersions = adapter.getDbVersions();
        for (DbVersion dbVersion : dbVersions) {
            assertEquals(dbVersion.type(), ConverterExecutionType.SEQ_SYNC);
            assertTrue(dbVersion.converter() instanceof LockingDBSqlConverter);
        }
    }

    /**
     * Checks that only the locking db converters are run
     */
    @Test
    public void testConvert() {
        DbVersion[] appliedVersions = {ArtifactoryDBVersion.v10};
        DbVersion[] allVersions = {ArtifactoryDBVersion.v12, ArtifactoryDBVersion.v82, ArtifactoryDBVersion.v95,
                ArtifactoryDBVersion.v259, ArtifactoryDBVersion.v260};
        DbVersion[] lockingDbVersions = {ArtifactoryDBVersion.v82};
        // Mock a call of a static method DbConverterUtil#calculateAndExecute
        try (MockedStatic<DbConverterUtil> utilMock = Mockito.mockStatic(DbConverterUtil.class)) {
            utilMock.when(() -> DbConverterUtil.calculateAndExecute(any(), any(), any(), any(), any()))
                    .thenAnswer((Answer<Void>) invocation -> null);
            // Mock calls of static methods ArtifactoryDBVersion#getAlreadyApplied and ArtifactoryDBVersion#get
            try (MockedStatic<ArtifactoryDBVersion> dbVersionMock = Mockito.mockStatic(ArtifactoryDBVersion.class)) {
                dbVersionMock.when(() -> ArtifactoryDBVersion.getAlreadyApplied(any(), any()))
                        .thenReturn(appliedVersions);
                dbVersionMock.when(() -> ArtifactoryDBVersion.get(ConverterExecutionType.SEQ_SYNC))
                        .thenReturn(allVersions);
                // Run the conversion
                DatabaseConverterAdapter convAdapter = new LockingDBConverterAdapter(mockArtiHome(), mockDbDetails());
                convAdapter.convert(mock(CompoundVersionDetails.class), mockTarget());
                // Verify that only the locking db converters were run
                utilMock.verify(
                        () -> DbConverterUtil.calculateAndExecute(any(), any(), eq(lockingDbVersions), any(), any()));
            }
        }
    }

    private ArtifactoryHome mockArtiHome() {
        ArtifactoryDbProperties dbPropsMock = mock(ArtifactoryDbProperties.class);
        when(dbPropsMock.getDbType()).thenReturn(DbType.ORACLE);
        ArtifactoryHome artifactoryHomeMock = mock(ArtifactoryHome.class);
        when(artifactoryHomeMock.getDBProperties()).thenReturn(dbPropsMock);
        when(artifactoryHomeMock.getServerId()).thenReturn("someServerId");
        return artifactoryHomeMock;
    }

    private DbDetails mockDbDetails() {
        DbDetails dbDetailsMock = mock(DbDetails.class);
        ArtifactoryJdbcHelper jdbcHelperMock = mock(ArtifactoryJdbcHelper.class);
        when(dbDetailsMock.getJdbcHelper()).thenReturn(jdbcHelperMock);
        return dbDetailsMock;
    }

    private CompoundVersionDetails mockTarget() {
        CompoundVersionDetails target = mock(CompoundVersionDetails.class);
        when(target.getVersionName()).thenReturn("someVersion");
        return target;
    }
}
