package org.artifactory.release.bundle;

import jakarta.ws.rs.InternalServerErrorException;
import org.artifactory.api.release.bundle.ReleaseBundleSearchFilter;
import org.artifactory.api.rest.distribution.bundle.models.BundleVersionsResponse;
import org.artifactory.api.rest.distribution.bundle.models.BundlesResponse;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.result.AqlEagerResult;
import org.artifactory.aql.result.rows.AqlReleaseBundle;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.artifactory.storage.db.bundle.model.DBArtifactsBundle;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.artifactory.bundle.BundleType.SOURCE;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


class ReleaseBundleSearchServiceImplTest {

    @Mock
    private ArtifactBundlesDao artifactsBundleDao;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AqlService aqlService;
    @Mock
    private AqlEagerResult<AqlReleaseBundle> aqlEagerResult;
    @Mock
    private AqlEagerResult<AqlReleaseBundle> aqlEagerResult2;
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;

    private ReleaseBundleSearchServiceImpl service;

    AutoCloseable autoCloseableMock;

    @BeforeEach
    void setUp() {
        autoCloseableMock = MockitoAnnotations.openMocks(this);
        ArtifactoryHome.bind(artifactoryHome);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        // Default to false for releaseBundleReceivedAdminOnly unless specifically set in test
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.releaseBundleReceivedAdminOnly))
                .thenReturn(false);
        service = new ReleaseBundleSearchServiceImpl(
                artifactsBundleDao, authorizationService,
                securityService, aqlService);
        verifyNoMoreInteractions(artifactsBundleDao, authorizationService, securityService, aqlService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        autoCloseableMock.close();
    }

    @Test
    void testGetFilteredBundles_AdminUser() throws SQLException {
        // Arrange
        int limit = 10;
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder().limit(limit).build();
        when(securityService.isAdmin()).thenReturn(true);
        when(artifactsBundleDao.getFilteredBundlesLastVersion(filter)).thenReturn(Collections.emptyList());

        // Act
        BundlesResponse response = service.getFilteredBundles(filter);

        // Assert
        assertNotNull(response);
        verify(artifactsBundleDao, times(1)).getFilteredBundlesLastVersion(filter);
        // Verify that for admin users, daoLimit is set to the requested limit
        Assertions.assertEquals(limit, filter.getDaoLimit());
    }

    @Test
    void testGetFilteredBundles_NonAdminUserWhenAdminOnly() throws SQLException {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder().limit(10).build();
        when(securityService.isAdmin()).thenReturn(false);
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.releaseBundleReceivedAdminOnly))
                .thenReturn(true);

        // Act
        BundlesResponse response = service.getFilteredBundles(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(0, response.getBundles().size(), "Should return empty list when adminOnly is true");
        verify(securityService, times(1)).isAdmin();
        // Should not make any database calls when adminOnly is true
        verify(artifactsBundleDao, times(0)).getFilteredBundlesCount(any());
        verify(artifactsBundleDao, times(0)).getFilteredBundlesLastVersion(any());
    }

    @Test
    void testGetFilteredBundles_NonAdminUserEmptyList() throws SQLException {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder().limit(10).daoLimit(5).offset(0).build();
        when(securityService.isAdmin()).thenReturn(false);
        when(authorizationService.canRead(RepoPathFactory.create("release-bundle"))).thenReturn(false);
        when(artifactsBundleDao.getFilteredBundlesCount(filter)).thenReturn(0); // No bundles in DB
        when(artifactsBundleDao.getFilteredBundlesLastVersion(filter)).thenReturn(Collections.emptyList());

        // Act
        BundlesResponse response = service.getFilteredBundles(filter);

        // Assert
        assertNotNull(response);
        verify(artifactsBundleDao, times(1)).getFilteredBundlesCount(filter);
        // Since totalDBBundles is 0, the loop should not execute
        verify(artifactsBundleDao, times(0)).getFilteredBundlesLastVersion(filter);
    }

    @Test
    void testGetFilteredBundles_DatabaseReturnsFewerResults() throws SQLException {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder().limit(10).daoLimit(5).offset(0).build();
        DBArtifactsBundle bundle = mock(DBArtifactsBundle.class);
        when(securityService.isAdmin()).thenReturn(false);
        when(authorizationService.canRead(any())).thenReturn(false, true);
        when(artifactsBundleDao.getFilteredBundlesLastVersion(any()))
                .thenReturn(List.of(bundle, bundle));
        when(artifactsBundleDao.getFilteredBundlesCount(filter)).thenReturn(2);
        when(bundle.getStoringRepo()).thenReturn("release-bundle");
        when(bundle.getDateCreated()).thenReturn(DateTime.now());
        when(bundle.getName()).thenReturn("test-bundle");
        when(bundle.getVersion()).thenReturn("1.0.0");

        // Act
        BundlesResponse response = service.getFilteredBundles(filter);

        // Assert
        assertNotNull(response);
        verify(artifactsBundleDao, times(1)).getFilteredBundlesCount(filter);
        // Since totalDBBundles is 2 and daoLimit is 5, we should only fetch once
        verify(artifactsBundleDao, times(1)).getFilteredBundlesLastVersion(any());
    }

    @Test
    void testGetFilteredBundles_ThrowsSQLException_NonAdmin() throws SQLException {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder().limit(10).build();
        when(securityService.isAdmin()).thenReturn(false); // Non-admin to trigger getFilteredBundlesCount
        when(artifactsBundleDao.getFilteredBundlesCount(filter)).thenThrow(new SQLException("Test exception"));

        // Act & Assert
        try {
            service.getFilteredBundles(filter);
            Assertions.fail("Expected InternalServerErrorException to be thrown");
        } catch (InternalServerErrorException e) {
            assertNotNull(e);
            assertNotNull(e.getCause());
            Assertions.assertInstanceOf(SQLException.class, e.getCause());
            verify(securityService, times(1)).isAdmin();
            verify(artifactsBundleDao, times(1)).getFilteredBundlesCount(filter);
            // Should not reach the getFilteredBundlesLastVersion call
            verify(artifactsBundleDao, times(0)).getFilteredBundlesLastVersion(any());
        }
    }

    @Test
    void testGetFilteredBundles_ThrowsSQLException_Admin() throws SQLException {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder().limit(10).build();
        when(securityService.isAdmin()).thenReturn(true); // Admin flow
        when(artifactsBundleDao.getFilteredBundlesLastVersion(filter)).thenThrow(new SQLException("Test exception"));

        // Act & Assert
        try {
            service.getFilteredBundles(filter);
            Assertions.fail("Expected InternalServerErrorException to be thrown");
        } catch (InternalServerErrorException e) {
            assertNotNull(e);
            assertNotNull(e.getCause());
            Assertions.assertInstanceOf(SQLException.class, e.getCause());
            verify(securityService, times(1)).isAdmin();
            // Should not call getFilteredBundlesCount for admin
            verify(artifactsBundleDao, times(0)).getFilteredBundlesCount(any());
            // Should call getFilteredBundlesLastVersion for admin
            verify(artifactsBundleDao, times(1)).getFilteredBundlesLastVersion(filter);
        }
    }

    @Test
    void testGetFilteredBundles_UserWithoutPermissions() throws SQLException {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder().limit(10).daoLimit(5).offset(0).build();
        DBArtifactsBundle bundle = mock(DBArtifactsBundle.class);
        when(securityService.isAdmin()).thenReturn(false);
        when(authorizationService.canRead(any())).thenReturn(false);
        when(artifactsBundleDao.getFilteredBundlesLastVersion(any()))
                .thenReturn(List.of(bundle, bundle, bundle, bundle, bundle)); // Always return daoLimit results
        when(artifactsBundleDao.getFilteredBundlesCount(filter)).thenReturn(5);
        when(bundle.getStoringRepo()).thenReturn("release-bundle");
        when(bundle.getName()).thenReturn("test-bundle");
        when(bundle.getVersion()).thenReturn("1.0.0");


        // Act
        BundlesResponse response = service.getFilteredBundles(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(0, response.getBundles().size());
        verify(artifactsBundleDao, times(1)).getFilteredBundlesCount(filter);
        // Should make only one call since totalDBBundles is 5 and offset 0 + daoLimit 5 = 5
        verify(artifactsBundleDao, times(1)).getFilteredBundlesLastVersion(any());
        verify(authorizationService, times(5)).canRead(any());
    }

    @Test
    void testGetFilteredBundles_ReturnsExactNumberOfRequestedItems() throws SQLException {
        // Arrange
        int requestedLimit = 3;
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder().limit(requestedLimit).daoLimit(5)
                .offset(0)
                .build();


        when(securityService.isAdmin()).thenReturn(false);
        when(authorizationService.canRead(any())).thenReturn(true); // All bundles have read permission
        when(artifactsBundleDao.getFilteredBundlesCount(filter)).thenReturn(10);
        List<DBArtifactsBundle> bundles = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            DBArtifactsBundle bundle = new DBArtifactsBundle();
            bundle.setName("bundle-" + i);
            bundle.setVersion("1.0." + i);
            bundle.setStoringRepo("release-bundle");
            bundle.setDateCreated(DateTime.now());
            bundles.add(bundle);
        }
        when(artifactsBundleDao.getFilteredBundlesLastVersion(any())).thenReturn(bundles);

        // Act
        BundlesResponse response = service.getFilteredBundles(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(requestedLimit, response.getBundles().size(),
                "The number of returned items should match the requested limit.");
        verify(artifactsBundleDao, times(1)).getFilteredBundlesCount(filter);
        // Should only need one fetch since we get 5 bundles and need only 3
        verify(artifactsBundleDao, times(1)).getFilteredBundlesLastVersion(any());
    }

    @Test
    void testGetFilteredBundles_InfiniteLoopPrevention() throws SQLException {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder().limit(1).daoLimit(5).offset(0).build();
        DBArtifactsBundle bundle = mock(DBArtifactsBundle.class);

        when(securityService.isAdmin()).thenReturn(false);
        when(authorizationService.canRead(any())).thenReturn(false); // No read permissions
        when(artifactsBundleDao.getFilteredBundlesCount(filter)).thenReturn(100);

        // Return 5 bundles each time
        List<DBArtifactsBundle> bundles = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            bundles.add(bundle);
        }
        when(artifactsBundleDao.getFilteredBundlesLastVersion(any())).thenReturn(bundles);

        when(bundle.getDateCreated()).thenReturn(DateTime.now());
        when(bundle.getName()).thenReturn("test-bundle");
        when(bundle.getVersion()).thenReturn("1.0.0");
        when(bundle.getStoringRepo()).thenReturn("release-bundle");


        // Act
        BundlesResponse response = service.getFilteredBundles(filter);

        // Assert
        assertNotNull(response);
        verify(artifactsBundleDao, times(1)).getFilteredBundlesCount(filter);
        // The loop should stop when offset >= totalDBBundles (100 in this case)
        // With daoLimit=5, it would take 20 iterations to reach offset=100
        verify(artifactsBundleDao, times(20)).getFilteredBundlesLastVersion(any());
    }

    @Test
    void testGetFilteredBundles_AdminUserWithBundles() throws SQLException {
        // Arrange - Test case with 15 bundles total, admin user requests 10
        int requestedLimit = 10;
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .limit(requestedLimit)
                .daoLimit(5) // Initial daoLimit, should be overridden for admin
                .offset(0)
                .build();

        // Create 15 bundles total
        List<DBArtifactsBundle> bundles = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            DBArtifactsBundle bundle = new DBArtifactsBundle();
            bundle.setName("bundle-" + i);
            bundle.setVersion("1.0." + i);
            bundle.setStoringRepo("release-bundle");
            bundle.setDateCreated(DateTime.now());
            bundles.add(bundle);
        }

        when(securityService.isAdmin()).thenReturn(true);

        // Admin flow should set daoLimit to limit, so return only the first 10 bundles
        when(artifactsBundleDao.getFilteredBundlesLastVersion(filter))
                .thenReturn(bundles.subList(0, requestedLimit));

        // Act
        BundlesResponse response = service.getFilteredBundles(filter);

        // Assert
        assertNotNull(response);
        // Should return exactly the requested limit
        Assertions.assertEquals(requestedLimit, response.getBundles().size());

        // Verify the correct bundles are in the response (first 10: 0-9)
        List<Integer> expectedBundles = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        response.getBundles().forEach((name, versions) -> {
            int bundleNum = Integer.parseInt(name.substring(7));
            Assertions.assertTrue(expectedBundles.contains(bundleNum),
                    "Bundle " + name + " should be one of the first 10 bundles");
        });

        // For admin users, daoLimit should be set to limit
        Assertions.assertEquals(requestedLimit, filter.getDaoLimit());
        // Should only make one database call since admin gets all results at once
        verify(artifactsBundleDao, times(1)).getFilteredBundlesLastVersion(filter);
        // Should NOT check permissions for admin users
        verify(authorizationService, times(0)).canRead(any());
    }

    @Test
    void testGetFilteredBundles_NonAdminPaginationWithScatteredPermissions() throws SQLException {
        // Arrange - Test case with 15 bundles total (0-14), user has permissions for bundles 5, 7, 12, 13, 14
        // User requests 10 bundles but only has access to 5 scattered throughout the result set
        int requestedLimit = 10;
        int daoLimit = 5;
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .limit(requestedLimit)
                .daoLimit(daoLimit)
                .offset(0)
                .build();

        // Create 3 batches of 5 bundles each (total 15 bundles, numbered 0-14)
        List<List<DBArtifactsBundle>> batches = new ArrayList<>();

        for (int batch = 0; batch < 3; batch++) {
            List<DBArtifactsBundle> currentBatch = new ArrayList<>();
            for (int i = 0; i < daoLimit; i++) {
                DBArtifactsBundle bundle = new DBArtifactsBundle();
                int bundleIndex = batch * daoLimit + i;
                bundle.setName("bundle-" + bundleIndex);
                bundle.setVersion("1.0." + bundleIndex);
                bundle.setStoringRepo("release-bundle");
                bundle.setDateCreated(DateTime.now());
                currentBatch.add(bundle);
            }
            batches.add(currentBatch);
        }

        when(securityService.isAdmin()).thenReturn(false);
        when(artifactsBundleDao.getFilteredBundlesCount(filter)).thenReturn(15); // 15 bundles total (0-14)

        // Set up permissions - only bundles 5, 7, 12, 13, 14 have read permissions
        when(authorizationService.canRead(any())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0).toString();
            // Extract bundle number from path (format: release-bundle:bundle-X/1.0.X)
            if (path.contains("bundle-")) {
                String bundleNum = path.substring(path.indexOf("bundle-") + 7, path.indexOf("/"));
                int num = Integer.parseInt(bundleNum);
                return num == 5 || num == 7 || num == 12 || num == 13 || num == 14;
            }
            return false;
        });

        // Return batches in sequence
        when(artifactsBundleDao.getFilteredBundlesLastVersion(any()))
                .thenReturn(batches.get(0))  // bundles 0-4 (no permissions)
                .thenReturn(batches.get(1))  // bundles 5-9 (has permissions for 5, 7)
                .thenReturn(batches.get(2)); // bundles 10-14 (has permissions for 12, 13, 14)

        // Act
        BundlesResponse response = service.getFilteredBundles(filter);

        // Assert
        assertNotNull(response);
        // User requested 10 but only has access to 5 (bundles 5, 7, 12, 13, 14)
        Assertions.assertEquals(5, response.getBundles().size());

        // Verify the correct bundles are in the response
        List<Integer> expectedBundles = List.of(5, 7, 12, 13, 14);
        response.getBundles().forEach((name, versions) -> {
            int bundleNum = Integer.parseInt(name.substring(7));
            Assertions.assertTrue(expectedBundles.contains(bundleNum),
                    "Bundle " + name + " should be one of: 5, 7, 12, 13, 14");
        });

        verify(artifactsBundleDao, times(1)).getFilteredBundlesCount(filter);
        // Should have made 3 database calls to scan all 15 bundles
        verify(artifactsBundleDao, times(3)).getFilteredBundlesLastVersion(any());
        // Should have checked permissions for all 15 bundles
        verify(authorizationService, times(15)).canRead(any());
    }

    @Test
    void testGetFilteredBundles_ThrowsSQLExceptionInLoop_NonAdmin() throws SQLException {
        // Arrange - Test case where SQLException is thrown inside the while loop
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .limit(10)
                .daoLimit(5)
                .offset(0)
                .build();

        // Create first batch of bundles
        List<DBArtifactsBundle> firstBatch = new ArrayList<>();
        for (int i = 0; i < 3; i++) {  // Less than limit to force second iteration
            DBArtifactsBundle bundle = new DBArtifactsBundle();
            bundle.setName("bundle-" + i);
            bundle.setVersion("1.0." + i);
            bundle.setStoringRepo("release-bundle");
            bundle.setDateCreated(DateTime.now());
            firstBatch.add(bundle);
        }

        when(securityService.isAdmin()).thenReturn(false);
        when(artifactsBundleDao.getFilteredBundlesCount(filter)).thenReturn(20); // Enough for multiple iterations
        when(authorizationService.canRead(any())).thenReturn(true); // All have permissions

        // First call succeeds, second call throws exception
        when(artifactsBundleDao.getFilteredBundlesLastVersion(any()))
                .thenReturn(firstBatch)
                .thenThrow(new SQLException("Test exception in loop"));

        // Act & Assert
        try {
            service.getFilteredBundles(filter);
            Assertions.fail("Expected InternalServerErrorException to be thrown");
        } catch (InternalServerErrorException e) {
            assertNotNull(e);
            assertNotNull(e.getCause());
            Assertions.assertInstanceOf(SQLException.class, e.getCause());
            Assertions.assertEquals("Test exception in loop", e.getCause().getMessage());

            // Verify the sequence of calls
            verify(securityService, times(1)).isAdmin();
            verify(artifactsBundleDao, times(1)).getFilteredBundlesCount(filter);
            // Should have made 2 calls: one successful, one that threw exception
            verify(artifactsBundleDao, times(2)).getFilteredBundlesLastVersion(any());
            // Should have checked permissions for the 3 bundles from first batch
            verify(authorizationService, times(3)).canRead(any());
        }
    }

    @Test
    void testGetBundles_ShouldReturnBundleVersion() throws SQLException {
        DBArtifactsBundle bundle = new DBArtifactsBundle();
        bundle.setName("bundle");
        bundle.setVersion("1");
        bundle.setStoringRepo("release-bundle");
        bundle.setDateCreated(DateTime.now());
        when(artifactsBundleDao.getArtifactsBundles("rb-test", SOURCE)).thenReturn(List.of(bundle));
        when(authorizationService.canRead(RepoPathFactory.create("release-bundle", "bundle/1"))).thenReturn(true);

        BundleVersionsResponse bundleVersions = service.getBundleVersions("rb-test", SOURCE);

        assertNotNull(bundleVersions);
        Assertions.assertEquals(1, bundleVersions.getVersions().size());

        verify(artifactsBundleDao, times(1)).getArtifactsBundles("rb-test", SOURCE);
    }

    @Test
    void testGetFilterBundleVersions_AdminUser_shouldMakeSingleQuery() {
        // Arrange
        int requestedLimit = 10;
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(requestedLimit)
                .daoLimit(2000)
                .offset(0)
                .orderBy("date_created")
                .direction("desc")
                .build();

        List<AqlReleaseBundle> bundles = createMockAqlBundles(5, 0);
        when(aqlEagerResult.getResults()).thenReturn(bundles);

        when(securityService.isAdmin()).thenReturn(true);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(5, response.getVersions().size());
        verify(securityService, times(1)).isAdmin();
        // Admin should make exactly one AQL query (no batching, no permission filtering)
        verify(aqlService, times(1)).executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any());
        // Admin should NOT check permissions
        verify(authorizationService, times(0)).canRead(any());
    }

    @Test
    void testGetFilterBundleVersions_NonAdminUser_shouldIncrementOffsetWhenBatching() {
        // Arrange
        int requestedLimit = 10;
        long daoLimit = 5;
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(requestedLimit)
                .daoLimit(daoLimit)
                .offset(0)
                .orderBy("date_created")
                .direction("desc")
                .build();

        when(aqlEagerResult.getResults()).thenReturn(Collections.emptyList());

        when(securityService.isAdmin()).thenReturn(false);
        // Return fewer results than daoLimit to stop the loop after first batch
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(0, response.getVersions().size());
        verify(securityService, times(1)).isAdmin();
        // Should only make one AQL query since batch returned empty (< daoLimit)
        verify(aqlService, times(1)).executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any());
    }

    @Test
    void testGetFilterBundleVersions_NonAdminUser_shouldMakeMultipleBatchQueries() {
        // Arrange
        int requestedLimit = 8;
        long daoLimit = 5;
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(requestedLimit)
                .daoLimit(daoLimit)
                .offset(0)
                .orderBy("date_created")
                .direction("desc")
                .build();

        // Create mock AQL results
        List<AqlReleaseBundle> batch1 = createMockAqlBundles(5, 0);
        List<AqlReleaseBundle> batch2 = createMockAqlBundles(3, 5);

        when(aqlEagerResult.getResults()).thenReturn(batch1);
        when(aqlEagerResult2.getResults()).thenReturn(batch2);

        when(securityService.isAdmin()).thenReturn(false);
        when(authorizationService.canRead(any())).thenReturn(true);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult)
                .thenReturn(aqlEagerResult2);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(requestedLimit, response.getVersions().size());
        verify(securityService, times(1)).isAdmin();
        // Should make 2 AQL queries: first returns 5 (need more), second returns 3 (total 8 = limit)
        verify(aqlService, times(2)).executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any());
        // Should check permissions for all 8 bundles
        verify(authorizationService, times(8)).canRead(any());
    }

    @Test
    void testGetFilterBundleVersions_NonAdminUser_shouldStopWhenEnoughResults() {
        // Arrange
        int requestedLimit = 3;
        long daoLimit = 5;
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(requestedLimit)
                .daoLimit(daoLimit)
                .offset(0)
                .orderBy("date_created")
                .direction("desc")
                .build();

        List<AqlReleaseBundle> batch = createMockAqlBundles(5, 0);
        when(aqlEagerResult.getResults()).thenReturn(batch);

        when(securityService.isAdmin()).thenReturn(false);
        when(authorizationService.canRead(any())).thenReturn(true);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        // Should return only the requested limit even though more were fetched
        Assertions.assertEquals(requestedLimit, response.getVersions().size());
        verify(aqlService, times(1)).executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any());
    }

    @Test
    void testGetFilterBundleVersions_NonAdminUser_shouldFilterByPermissions() {
        // Arrange
        int requestedLimit = 10;
        long daoLimit = 5;
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(requestedLimit)
                .daoLimit(daoLimit)
                .offset(0)
                .orderBy("date_created")
                .direction("desc")
                .build();

        // First batch: 5 bundles, only 3 have permissions
        List<AqlReleaseBundle> batchWithPermissions = createMockAqlBundles(5, 0);
        when(aqlEagerResult.getResults()).thenReturn(batchWithPermissions);

        // Second batch: empty (no more data in DB)
        when(aqlEagerResult2.getResults()).thenReturn(Collections.emptyList());

        when(securityService.isAdmin()).thenReturn(false);
        // Only allow access to bundles 0, 2, 4
        when(authorizationService.canRead(any())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0).toString();
            return path.contains("bundle-0") || path.contains("bundle-2") || path.contains("bundle-4");
        });
        // First call returns 5 bundles, second call returns empty (simulating no more data)
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult)
                .thenReturn(aqlEagerResult2);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        // Should return only 3 bundles (0, 2, 4) that have permissions
        Assertions.assertEquals(3, response.getVersions().size());
        // Should have made 2 AQL queries: first returns 5, second returns 0 (stops loop)
        verify(aqlService, times(2)).executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any());
    }

    @Test
    void testGetFilterBundleVersions_shouldHandleAscendingDirection() {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(10)
                .daoLimit(2000)
                .offset(0)
                .orderBy("date_created")
                .direction("asc")
                .build();

        List<AqlReleaseBundle> bundles = createMockAqlBundles(3, 0);
        when(aqlEagerResult.getResults()).thenReturn(bundles);
        when(securityService.isAdmin()).thenReturn(true);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(3, response.getVersions().size());
    }

    @Test
    void testGetFilterBundleVersions_shouldHandleVersionOrderBy() {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(10)
                .daoLimit(2000)
                .offset(0)
                .orderBy("version")
                .direction("desc")
                .build();

        List<AqlReleaseBundle> bundles = createMockAqlBundles(3, 0);
        when(aqlEagerResult.getResults()).thenReturn(bundles);
        when(securityService.isAdmin()).thenReturn(true);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(3, response.getVersions().size());
    }

    @Test
    void testGetFilterBundleVersions_shouldHandleNameOrderBy() {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(10)
                .daoLimit(2000)
                .offset(0)
                .orderBy("name")
                .direction("desc")
                .build();

        List<AqlReleaseBundle> bundles = createMockAqlBundles(3, 0);
        when(aqlEagerResult.getResults()).thenReturn(bundles);
        when(securityService.isAdmin()).thenReturn(true);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(3, response.getVersions().size());
    }

    @Test
    void testGetFilterBundleVersions_shouldHandleBeforeFilter() {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(10)
                .daoLimit(2000)
                .offset(0)
                .orderBy("date_created")
                .direction("desc")
                .before(System.currentTimeMillis())
                .build();

        List<AqlReleaseBundle> bundles = createMockAqlBundles(3, 0);
        when(aqlEagerResult.getResults()).thenReturn(bundles);
        when(securityService.isAdmin()).thenReturn(true);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(3, response.getVersions().size());
    }

    @Test
    void testGetFilterBundleVersions_shouldHandleAfterFilter() {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(10)
                .daoLimit(2000)
                .offset(0)
                .orderBy("date_created")
                .direction("desc")
                .after(System.currentTimeMillis() - 86400000)
                .build();

        List<AqlReleaseBundle> bundles = createMockAqlBundles(3, 0);
        when(aqlEagerResult.getResults()).thenReturn(bundles);
        when(securityService.isAdmin()).thenReturn(true);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(3, response.getVersions().size());
    }

    @Test
    void testGetFilterBundleVersions_shouldHandleVersionsFilter() {
        // Arrange
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(10)
                .daoLimit(2000)
                .offset(0)
                .orderBy("date_created")
                .direction("desc")
                .versions(List.of("1.0.0", "1.0.1", "1.0.2"))
                .build();

        List<AqlReleaseBundle> bundles = createMockAqlBundles(3, 0);
        when(aqlEagerResult.getResults()).thenReturn(bundles);
        when(securityService.isAdmin()).thenReturn(true);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(3, response.getVersions().size());
    }

    @Test
    void testGetFilterBundleVersions_shouldHandleNonAdminWithPermissionFiltering() {
        // Arrange - Tests the non-admin code path with permission filtering (lines 102-116)
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(5)
                .daoLimit(10)
                .offset(0)
                .orderBy("date_created")
                .direction("desc")
                .build();

        List<AqlReleaseBundle> bundles = createMockAqlBundles(5, 0);
        when(aqlEagerResult.getResults()).thenReturn(bundles);
        when(securityService.isAdmin()).thenReturn(false);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(5, response.getVersions().size());
    }

    @Test
    void testGetFilterBundleVersions_shouldHandleNonAdminWithPagination() {
        // Arrange - Tests the non-admin pagination loop when first batch doesn't have enough permitted items
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(5)
                .daoLimit(3)
                .offset(0)
                .orderBy("date_created")
                .direction("desc")
                .build();

        List<AqlReleaseBundle> firstBatch = createMockAqlBundles(3, 0);
        List<AqlReleaseBundle> secondBatch = createMockAqlBundles(3, 3);
        when(aqlEagerResult.getResults()).thenReturn(firstBatch).thenReturn(secondBatch);
        when(securityService.isAdmin()).thenReturn(false);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(5, response.getVersions().size());
    }

    @Test
    void testGetFilterBundleVersions_shouldHandleNullReleaseStatus() {
        // Arrange - Tests the null releaseStatus branch (line 213)
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(10)
                .daoLimit(2000)
                .offset(0)
                .orderBy("date_created")
                .direction("desc")
                .build();

        List<AqlReleaseBundle> bundles = createMockAqlBundlesWithNullStatus();
        when(aqlEagerResult.getResults()).thenReturn(bundles);
        when(securityService.isAdmin()).thenReturn(true);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(2, response.getVersions().size());
        // Verify status is null when releaseStatus is null
        assertNull(response.getVersions().getFirst().getStatus());
    }

    @Test
    void testGetFilterBundleVersions_shouldHandleBlankName() {
        // Arrange - Tests the case when filter.getName() is blank (line 133 condition false)
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("")
                .bundleType(SOURCE)
                .limit(10)
                .daoLimit(2000)
                .offset(0)
                .orderBy("date_created")
                .direction("desc")
                .build();

        List<AqlReleaseBundle> bundles = createMockAqlBundles(3, 0);
        when(aqlEagerResult.getResults()).thenReturn(bundles);
        when(securityService.isAdmin()).thenReturn(true);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(3, response.getVersions().size());
    }

    @Test
    void testGetFilterBundleVersions_shouldHandleDefaultOrderBy() {
        // Arrange - Tests the default case in sortResultsBy switch (line 174)
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .name("test-bundle")
                .bundleType(SOURCE)
                .limit(10)
                .daoLimit(2000)
                .offset(0)
                .orderBy("unknown_field")
                .direction("desc")
                .build();

        List<AqlReleaseBundle> bundles = createMockAqlBundles(3, 0);
        when(aqlEagerResult.getResults()).thenReturn(bundles);
        when(securityService.isAdmin()).thenReturn(true);
        when(aqlService.executeQueryEager(ArgumentMatchers.<AqlBase<?, AqlReleaseBundle>>any()))
                .thenReturn(aqlEagerResult);

        // Act
        BundleVersionsResponse response = service.getFilterBundleVersions(filter);

        // Assert
        assertNotNull(response);
        Assertions.assertEquals(3, response.getVersions().size());
    }

    private List<AqlReleaseBundle> createMockAqlBundlesWithNullStatus() {
        List<AqlReleaseBundle> bundles = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            AqlReleaseBundle bundle = mock(AqlReleaseBundle.class);
            when(bundle.getReleaseName()).thenReturn("bundle-" + i);
            when(bundle.getReleaseVersion()).thenReturn("1.0." + i);
            when(bundle.getStoringRepo()).thenReturn("release-bundles");
            when(bundle.getReleaseStatus()).thenReturn(null);
            when(bundle.getReleaseCreated()).thenReturn(new java.util.Date());
            when(bundle.getKeep()).thenReturn(0);
            bundles.add(bundle);
        }
        return bundles;
    }

    private List<AqlReleaseBundle> createMockAqlBundles(int count, int startIndex) {
        List<AqlReleaseBundle> bundles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            AqlReleaseBundle bundle = mock(AqlReleaseBundle.class);
            when(bundle.getReleaseName()).thenReturn("bundle-" + (startIndex + i));
            when(bundle.getReleaseVersion()).thenReturn("1.0." + (startIndex + i));
            when(bundle.getStoringRepo()).thenReturn("release-bundles");
            when(bundle.getReleaseStatus()).thenReturn("COMPLETE");
            when(bundle.getReleaseCreated()).thenReturn(new java.util.Date());
            when(bundle.getKeep()).thenReturn(0);
            bundles.add(bundle);
        }
        return bundles;
    }
}