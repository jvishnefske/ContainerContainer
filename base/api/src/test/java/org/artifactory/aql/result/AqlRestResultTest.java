package org.artifactory.aql.result;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.aqlQueryApplyIncludeExcludePatterns;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_AQL_FILTER_IN_DATABASE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AqlRestResultTest {

    private SysConfig sysConfig;
    private final AqlPermissionProvider permissionProvider = mock(AqlPermissionProvider.class);

    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private AqlRepoProvider repoProvider;

    AutoCloseable closeable;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        sysConfig = mock(SysConfig.class);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        closeable.close();
    }

    @Test
    void testCanReadReturnFalseWithEvidencePath() throws SQLException {
        //aql filtered in-memory
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(false);
        String repoKey = "maven-local";
        String repoPath = ".evidence/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repo = mock(RepoPath.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemRepo.name())).thenReturn(repoKey);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemPath.name())).thenReturn(repoPath);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemName.name())).thenReturn(itemName);
        when(repo.getPath()).thenReturn(repoPath);
        when(repo.getRepoKey()).thenReturn(repoKey);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider);
                MockedStatic<AqlUtils> mocked = mockStatic(AqlUtils.class)) {
            mocked.when(() -> AqlUtils.fromAql(repoKey, repoPath, itemName)).thenReturn(repo);
            boolean bool = aqlRestResult.canRead(AqlDomainEnum.items, resultSet);
            assertFalse(bool);
        }
    }

    @Test
    void testCanReadAdmin() throws SQLException {
        //aql filtered in database
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);
        String repoKey = "maven-local";
        String repoPath = ".evidence/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repo = mock(RepoPath.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemRepo.name())).thenReturn(repoKey);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemPath.name())).thenReturn(repoPath);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemName.name())).thenReturn(itemName);
        when(repo.getPath()).thenReturn(repoPath);
        when(repo.getRepoKey()).thenReturn(repoKey);
        when(permissionProvider.isAdmin()).thenReturn(true);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider);
                MockedStatic<AqlUtils> mocked = mockStatic(AqlUtils.class)) {
            mocked.when(() -> AqlUtils.fromAql(repoKey, repoPath, itemName)).thenReturn(repo);
            boolean bool = aqlRestResult.canRead(AqlDomainEnum.items, resultSet);
            assertTrue(bool);
        }
    }

    @Test
    void testCanReadNonAdminNonEvidenceJFrogMetadata() throws SQLException {
        //aql filtered in database
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(false);
        String repoKey = "maven-local";
        String repoPath = ".jfrog/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repo = mock(RepoPath.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemRepo.name())).thenReturn(repoKey);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemPath.name())).thenReturn(repoPath);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemName.name())).thenReturn(itemName);
        when(repo.getPath()).thenReturn(repoPath);
        when(repo.getRepoKey()).thenReturn(repoKey);
        when(permissionProvider.isAdmin()).thenReturn(false);
        when(permissionProvider.canRead(repo)).thenReturn(true);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider);
                MockedStatic<AqlUtils> mocked = mockStatic(AqlUtils.class)) {
            mocked.when(() -> AqlUtils.fromAql(repoKey, repoPath, itemName)).thenReturn(repo);
            boolean bool = aqlRestResult.canRead(AqlDomainEnum.items, resultSet);
            assertFalse(bool);
        }
    }

    @Test
    void testCanReadNonAdminNonEvidenceNonJFrogMetadata() throws SQLException {
        //aql filtered in database
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(false);
        String repoKey = "maven-local";
        String repoPath = "/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repo = mock(RepoPath.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemRepo.name())).thenReturn(repoKey);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemPath.name())).thenReturn(repoPath);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemName.name())).thenReturn(itemName);
        when(repo.getPath()).thenReturn(repoPath);
        when(repo.getRepoKey()).thenReturn(repoKey);
        when(permissionProvider.isAdmin()).thenReturn(false);
        when(permissionProvider.canRead(repo)).thenReturn(false);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider);
                MockedStatic<AqlUtils> mocked = mockStatic(AqlUtils.class)) {
            mocked.when(() -> AqlUtils.fromAql(repoKey, repoPath, itemName)).thenReturn(repo);
            boolean bool = aqlRestResult.canRead(AqlDomainEnum.items, resultSet);
            assertFalse(bool);
        }
    }

    @Test
    void testCanReadNonAdminNonEvidenceNonJFrogMetadataNonItemDomain() throws SQLException {
        //aql filtered in database
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(false);
        String repoKey = "maven-local";
        String repoPath = "/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repo = mock(RepoPath.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemRepo.name())).thenReturn(repoKey);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemPath.name())).thenReturn(repoPath);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemName.name())).thenReturn(itemName);
        when(repo.getPath()).thenReturn(repoPath);
        when(repo.getRepoKey()).thenReturn(repoKey);
        when(permissionProvider.isAdmin()).thenReturn(false);
        when(permissionProvider.canRead(repo)).thenReturn(false);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider);
                MockedStatic<AqlUtils> mocked = mockStatic(AqlUtils.class)) {
            mocked.when(() -> AqlUtils.fromAql(repoKey, repoPath, itemName)).thenReturn(repo);
            boolean bool = aqlRestResult.canRead(AqlDomainEnum.properties, resultSet);
            assertFalse(bool);
        }
    }

    @Test
    void testCanReadRepoPathNotAccepted() throws SQLException {
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(false);
        String repoKey = "maven-local";
        String path = "/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repoPath = mock(RepoPath.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemRepo.name())).thenReturn(repoKey);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemPath.name())).thenReturn(path);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemName.name())).thenReturn(itemName);
        when(repoPath.getPath()).thenReturn(path);
        when(permissionProvider.canRead(repoPath)).thenReturn(true);
        when(repoProvider.isRepoPathAccepted(repoPath)).thenReturn(false);
        when(artifactorySystemProperties.getBooleanProperty(aqlQueryApplyIncludeExcludePatterns)).thenReturn(true);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider);
                MockedStatic<AqlUtils> mocked = mockStatic(AqlUtils.class)) {
            mocked.when(() -> AqlUtils.fromAql(repoKey, path, itemName)).thenReturn(repoPath);
            assertFalse(aqlRestResult.canRead(AqlDomainEnum.items, resultSet));
        }
    }

    @Test
    void testCanReadLogError() throws SQLException {
        //aql filtered in database
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);
        String repoKey = "maven-local";
        String repoPath = "/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repo = mock(RepoPath.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemRepo.name())).thenThrow(RuntimeException.class);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemPath.name())).thenReturn(repoPath);
        when(resultSet.getString(AqlPhysicalFieldEnum.itemName.name())).thenReturn(itemName);
        when(repo.getPath()).thenReturn(repoPath);
        when(repo.getRepoKey()).thenReturn(repoKey);
        when(permissionProvider.isAdmin()).thenReturn(false);
        when(permissionProvider.canRead(repo)).thenReturn(false);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider);
                MockedStatic<AqlUtils> mocked = mockStatic(AqlUtils.class)) {
            mocked.when(() -> AqlUtils.fromAql(repoKey, repoPath, itemName)).thenReturn(repo);
            boolean bool = aqlRestResult.canRead(AqlDomainEnum.items, resultSet);
            assertFalse(bool);
        }
    }

    @Test
    void testCanReadReturnFalseWithEvidencePath_Method2() {
        //aql filtered in database
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);
        String repoKey = "maven-local";
        String repoPath = ".evidence/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repo = mock(RepoPath.class);
        when(repo.getRepoKey()).thenReturn(repoKey);
        when(permissionProvider.isAdmin()).thenReturn(true);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider)) {
            boolean bool = aqlRestResult.canRead(AqlDomainEnum.items, repoKey, repoPath, itemName);
            assertTrue(bool);
        }
    }

    @Test
    void testCanReadAdmin_Method2() {
        //aql filtered in-memory
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(false);
        String repoKey = "maven-local";
        String repoPath = ".evidence/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repo = mock(RepoPath.class);
        when(repo.getRepoKey()).thenReturn(repoKey);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider)) {
            boolean bool = aqlRestResult.canRead(AqlDomainEnum.items, repoKey, repoPath, itemName);
            assertFalse(bool);
        }
    }

    @Test
    void testCanReadNonEvidenceNonAdminDomainItem_Method2() {
        //aql filtered in database
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);
        String repoKey = "maven-local";
        String repoPath = ".evidence/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repo = mock(RepoPath.class);
        when(repo.getRepoKey()).thenReturn(repoKey);
        when(permissionProvider.isAdmin()).thenReturn(false);
        when(permissionProvider.canRead(repo)).thenReturn(false);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider);
                MockedStatic<AqlUtils> mocked = mockStatic(AqlUtils.class)) {
            mocked.when(() -> AqlUtils.fromAql(repoKey, repoPath, itemName)).thenReturn(repo);
            boolean bool = aqlRestResult.canRead(AqlDomainEnum.items, repoKey, repoPath, itemName);
            assertFalse(bool);
        }
    }

    @Test
    void testCanReadNonEvidenceNonAdminNonDomainItem_Method2() {
        //aql filtered in database
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);
        String repoKey = "maven-local";
        String repoPath = ".evidence/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repo = mock(RepoPath.class);
        when(repo.getRepoKey()).thenReturn(repoKey);
        when(permissionProvider.isAdmin()).thenReturn(false);
        when(permissionProvider.canRead(repo)).thenReturn(false);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider);
                MockedStatic<AqlUtils> mocked = mockStatic(AqlUtils.class)) {
            mocked.when(() -> AqlUtils.fromAql(repoKey, repoPath, itemName)).thenReturn(repo);
            boolean bool = aqlRestResult.canRead(AqlDomainEnum.properties, repoKey, repoPath, itemName);
            assertFalse(bool);
        }
    }

    @Test
    void testCanReadLogError_Method2() {
        //aql filtered in database
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);
        String repoKey = "maven-local";
        String repoPath = ".evidence/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repo = mock(RepoPath.class);
        when(repo.getRepoKey()).thenReturn(repoKey);
        when(permissionProvider.isAdmin()).thenReturn(false);
        when(permissionProvider.canRead(repo)).thenReturn(false);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider);
                MockedStatic<AqlUtils> mocked = mockStatic(AqlUtils.class)) {
            mocked.when(() -> AqlUtils.fromAql(repoKey, repoPath, itemName)).thenThrow(RuntimeException.class);
            boolean bool = aqlRestResult.canRead(AqlDomainEnum.items, repoKey, repoPath, itemName);
            assertFalse(bool);
        }
    }

    @Test
    void testCanReadRepoPathNotAccepted_Method2() {
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(false);
        String repoKey = "maven-local";
        String path = "/a/b/c/dummy.txt";
        String itemName = "dummy.txt";
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn(repoKey);
        when(repoPath.getPath()).thenReturn(path);
        when(permissionProvider.isAdmin()).thenReturn(false);
        when(permissionProvider.canRead(repoPath)).thenReturn(true);
        when(repoProvider.isRepoPathAccepted(repoPath)).thenReturn(false);
        when(artifactorySystemProperties.getBooleanProperty(aqlQueryApplyIncludeExcludePatterns)).thenReturn(true);
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider);
                MockedStatic<AqlUtils> mocked = mockStatic(AqlUtils.class)) {
            mocked.when(() -> AqlUtils.fromAql(repoKey, path, itemName)).thenReturn(repoPath);
            assertFalse(aqlRestResult.canRead(AqlDomainEnum.items, repoKey, path, itemName));
        }
    }

    @Test
    void testRangeNotifications() {
        when(artifactorySystemProperties.getLongProperty(ConstantValues.aqlSearchQueryMaxLimit))
                .thenReturn(2L);
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.aqlSearchNotifyReachHardLimit))
                .thenReturn(true);

        AqlRestResult.Range range = new AqlRestResult.Range(0, 10, 10);
        assertNull(range.notification);

        AqlRestResult.Range rangeWithNotification = new AqlRestResult.Range(0, 2, 2, 2);
        assertEquals(
                "AQL query reached the search hard limit, results are trimmed. " +
                        "Please address configurable limit.",
                rangeWithNotification.notification);
    }

    @ParameterizedTest
    @MethodSource("buildsDomainPermissionScenarios")
    void canRead_shouldHandleBuildsDomainPermissions(boolean canReadBuild, boolean throwsException,
            boolean expectedResult) throws SQLException {
        // Arrange
        ResultSet resultSet = mock(ResultSet.class);
        if (throwsException) {
            when(resultSet.getString(AqlPhysicalFieldEnum.buildName.name()))
                    .thenThrow(new SQLException("Test exception"));
        } else {
            when(resultSet.getString(AqlPhysicalFieldEnum.buildName.name())).thenReturn("my-build");
            when(resultSet.getString(AqlPhysicalFieldEnum.buildNumber.name())).thenReturn("42");
            when(resultSet.getString(AqlPhysicalFieldEnum.buildRepo.name())).thenReturn("artifactory-build-info");
            when(permissionProvider.canReadBuild("my-build", "42", "artifactory-build-info"))
                    .thenReturn(canReadBuild);
        }

        // Act & Assert
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider)) {
            if (expectedResult) {
                assertTrue(aqlRestResult.canRead(AqlDomainEnum.builds, resultSet));
            } else {
                assertFalse(aqlRestResult.canRead(AqlDomainEnum.builds, resultSet));
            }
        }
    }

    private static Stream<Arguments> buildsDomainPermissionScenarios() {
        return Stream.of(
                // canReadBuild returns true - permission granted
                Arguments.of(true, false, true),
                // canReadBuild returns false - permission denied for non-admin
                Arguments.of(false, false, false),
                // SQLException thrown - error handled, returns false for non-admin
                Arguments.of(false, true, false)
        );
    }

    @Test
    void canRead_shouldReturnTrueWithoutCheckingPermissions_whenAdmin() {
        // Arrange
        when(permissionProvider.isAdmin()).thenReturn(true);
        ResultSet resultSet = mock(ResultSet.class);

        // Act
        try (TestAqlRestResult aqlRestResult = new TestAqlRestResult(permissionProvider, repoProvider)) {
            assertTrue(aqlRestResult.canRead(AqlDomainEnum.builds, resultSet));
        }

        // Assert - ResultSet should never be accessed, avoiding the expensive canReadBuild call
        verifyNoInteractions(resultSet);
    }

    private static class TestAqlRestResult extends AqlRestResult {

        public TestAqlRestResult(AqlPermissionProvider permissionProvider, AqlRepoProvider repoProvider) {
            super(permissionProvider, repoProvider);
        }

        @Override
        public byte[] read() {
            return new byte[0];
        }

        @Override
        public byte[] readCompact() {
            return new byte[0];
        }

        @Override
        public long getRowsCount() {
            return 0;
        }

        @Override
        public void close() {

        }
    }
}
