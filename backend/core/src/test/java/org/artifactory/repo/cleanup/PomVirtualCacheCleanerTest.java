package org.artifactory.repo.cleanup;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.search.VfsQueryService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.search.model.VfsQueryDbImpl;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.common.ConstantValues.virtualCleanupMaxAgeHours;
import static org.artifactory.common.ConstantValues.virtualCleanupMaxBatch;
import static org.artifactory.common.ConstantValues.virtualCleanupMaxIterations;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PomVirtualCacheCleanerTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private VfsQueryService vfsQueryService;

    @Mock
    private InternalRepositoryService repositoryService;

    @Mock
    private VirtualRepoConfig repoConfig;
    @Mock
    private ArtifactoryApplicationContext artifactoryContext;
    @Mock
    private DbService dbService;
    @Mock
    private ArtifactoryJdbcHelper jdbcHelper;

    private PomVirtualCacheCleaner cleaner;
    private AutoCloseable mocks;

    private static final String BATCH_QUERY =
            "SELECT nodes.node_id, nodes.node_type, nodes.repo, nodes.node_path, nodes.node_name FROM nodes WHERE " +
                    "nodes.node_type=1 AND  nodes.repo  = ? AND ( nodes.node_name LIKE ? AND nodes.created <= ? )";

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(artifactoryContext.beanForType(DbService.class)).thenReturn(dbService);
        when(artifactoryContext.beanForType(ArtifactoryJdbcHelper.class)).thenReturn(jdbcHelper);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(repoConfig.getKey()).thenReturn("repo");
        cleaner = new PomVirtualCacheCleaner(vfsQueryService, repositoryService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void cleanCacheReturnsZeroWhenMaxAgeHoursIsNegative() {
        homeStub.setProperty(virtualCleanupMaxAgeHours, "-1");
        long result = cleaner.cleanCache(repoConfig);
        assertEquals(0, result);
    }

    @Test
    void cleanCacheStopOnNoResults() throws SQLException {
        homeStub.setProperty(virtualCleanupMaxAgeHours, "1");
        homeStub.setProperty(virtualCleanupMaxBatch, "1");
        homeStub.setProperty(virtualCleanupMaxIterations, "1");
        VfsQueryDbImpl vfsQueryDb = new VfsQueryDbImpl();
        when(vfsQueryService.createQuery()).thenReturn(vfsQueryDb);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(eq(BATCH_QUERY), eq("repo"), eq("%.pom"), anyLong())).thenReturn(resultSet);
        long result = cleaner.cleanCache(repoConfig);
        assertEquals(0, result);
        verify(repositoryService, times(0)).undeploy(any());
        verify(jdbcHelper, times(1)).executeSelect(eq(BATCH_QUERY), eq("repo"), eq("%.pom"), anyLong());
    }

    @Test
    void cleanCacheReturnsStopOnMaxIterations() throws SQLException {
        homeStub.setProperty(virtualCleanupMaxAgeHours, "1");
        homeStub.setProperty(virtualCleanupMaxBatch, "1");
        homeStub.setProperty(virtualCleanupMaxIterations, "2");
        VfsQueryDbImpl vfsQueryDb = new VfsQueryDbImpl();
        VfsQueryDbImpl vfsQueryDb1 = new VfsQueryDbImpl();
        when(vfsQueryService.createQuery()).thenReturn(vfsQueryDb, vfsQueryDb1);
        ResultSet resultSet = mockResultSet(true);
        when(jdbcHelper.executeSelect(eq(BATCH_QUERY), eq("repo"), eq("%.pom"), anyLong())).thenReturn(resultSet);
        long result = cleaner.cleanCache(repoConfig);
        assertEquals(2, result);
        verify(repositoryService, times(2)).undeploy(any());
        verify(jdbcHelper, times(2)).executeSelect(eq(BATCH_QUERY), eq("repo"), eq("%.pom"), anyLong());

    }

    @Test
    void cleanCacheStopsWhenNoMoreResults() throws SQLException {
        homeStub.setProperty(virtualCleanupMaxAgeHours, "1");
        homeStub.setProperty(virtualCleanupMaxBatch, "10");
        homeStub.setProperty(virtualCleanupMaxIterations, "10");
        VfsQueryDbImpl vfsQueryDb = new VfsQueryDbImpl();
        when(vfsQueryService.createQuery()).thenReturn(vfsQueryDb);
        ResultSet resultSet = mockResultSet(false);
        when(jdbcHelper.executeSelect(eq(BATCH_QUERY), eq("repo"), eq("%.pom"), anyLong())).thenReturn(resultSet);
        long result = cleaner.cleanCache(repoConfig);
        assertEquals(1, result);
        verify(repositoryService, times(1)).undeploy(any());
        verify(jdbcHelper, times(1)).executeSelect(eq(BATCH_QUERY), eq("repo"), eq("%.pom"), anyLong());
    }

    @Test
    void shouldRunByPackageType() {
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        assertTrue(cleaner.shouldRun(repoConfig));
        when(repoConfig.getPackageType()).thenReturn(RepoType.Npm);
        assertFalse(cleaner.shouldRun(repoConfig));
    }

    private static ResultSet mockResultSet(Boolean... andThen) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, andThen);
        when(resultSet.getString(3)).thenReturn("repo");
        when(resultSet.getString(4)).thenReturn("path");
        when(resultSet.getString(5)).thenReturn("file");
        return resultSet;
    }

}