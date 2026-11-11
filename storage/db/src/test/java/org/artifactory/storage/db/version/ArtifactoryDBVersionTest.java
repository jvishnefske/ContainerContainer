package org.artifactory.storage.db.version;

import org.artifactory.storage.db.util.ArtifactoryDbVersionUtil;
import org.artifactory.util.AolUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ConverterExecutionType;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.sql.Connection;
import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * @author Volodymyr Shulga
 */
public class ArtifactoryDBVersionTest {
    @Mock
    private CompoundVersionDetails compoundVersionDetails;
    private AutoCloseable closable;

    @BeforeEach
    public void setUp() {
        closable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void close() throws Exception {
        if (closable != null) {
            closable.close();
        }
    }

    @Test
    void allVersionsReturnedWhenSrcAbsent() {
        final DbVersion[] dbVersions = ArtifactoryDBVersion.get(ConverterExecutionType.SEQ_SYNC);
        ArtifactoryDBVersion beforeVersion = ArtifactoryDBVersion.v32;
        Mockito.when(compoundVersionDetails.getVersion()).thenReturn(beforeVersion.applyFromVersion.get());
        for (DbVersion dbVersion : dbVersions) {
            ArtifactoryDBVersion artifactoryDBVersion = (ArtifactoryDBVersion) dbVersion;

            if (artifactoryDBVersion.isAlreadyApplied(compoundVersionDetails)) {
                assertTrue(ArtifactoryDbVersionUtil.isLessOrEqual(artifactoryDBVersion.version(), beforeVersion));
            } else {
                assertTrue(ArtifactoryDbVersionUtil.isGreaterOrEqual(artifactoryDBVersion.version(), beforeVersion));
            }
        }
    }

    @Test
    void shouldReturnConvertersInCorrectOrder() {
        final DbVersion[] dbVersions = ArtifactoryDBVersion.get(ConverterExecutionType.SEQ_SYNC);
        Assertions.assertEquals("v10", dbVersions[0].version());
        // verifies that sorting is correct and v11 goes after v10
        Assertions.assertEquals("v11", dbVersions[1].version());
    }

    @Test
    void shouldReturnVersions() {
        String firstConverter = "v10";
        String secondConverter = "v11";
        final List<ArtifactoryDBVersion> versions = ArtifactoryDBVersion
                .getByVersions(List.of(firstConverter, secondConverter));
        Assertions.assertEquals(2, versions.size());
        var v10 = versions.stream().filter(v -> v.version().equals(firstConverter)).findFirst();
        var v11 = versions.stream().filter(v -> v.version().equals(secondConverter)).findFirst();
        Assertions.assertTrue(v10.isPresent());
        Assertions.assertTrue(v11.isPresent());
        Assertions.assertTrue(v10.get().isBackwardCompatible());
        Assertions.assertTrue(v11.get().isBackwardCompatible());
    }

    @Test
    void aolOnlyReturnsTrueWhenAolUtilsIsAolTrue() {
        try (MockedStatic<AolUtils> aolUtils = mockStatic(AolUtils.class)) {
            aolUtils.when(AolUtils::isAol).thenReturn(true);
            BiPredicate<Connection, DbType> predicate = ArtifactoryDBVersion.aolOnly();
            Connection conn = mock(Connection.class);
            Assertions.assertTrue(predicate.test(conn, DbType.POSTGRESQL));
        }
    }

    @Test
    void aolOnlyReturnsFalseWhenAolUtilsIsAolFalse() {
        try (MockedStatic<AolUtils> aolUtils = mockStatic(AolUtils.class)) {
            aolUtils.when(AolUtils::isAol).thenReturn(false);
            BiPredicate<Connection, DbType> predicate = ArtifactoryDBVersion.aolOnly();
            Connection conn = mock(Connection.class);
            assertFalse(predicate.test(conn, DbType.POSTGRESQL));
        }
    }

    @Test
    void aolOnlyReturnsFalseWhenAolUtilsIsAolThrows() {
        try (MockedStatic<AolUtils> aolUtils = mockStatic(AolUtils.class)) {
            aolUtils.when(AolUtils::isAol).thenThrow(new RuntimeException("context not ready"));
            BiPredicate<Connection, DbType> predicate = ArtifactoryDBVersion.aolOnly();
            Connection conn = mock(Connection.class);
            assertFalse(predicate.test(conn, DbType.POSTGRESQL));
        }
    }

}
