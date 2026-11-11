package org.artifactory.ph;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.jfrog.ph.acl.model.curation.CurationPackageForRequest;
import com.jfrog.ph.acl.model.curation.CurationRequest;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import com.jfrog.ph.acl.model.curation.CurationVersionForRequest;
import org.artifactory.addon.curation.CurationService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageCurationServiceImplTest {

    private static final String REPO_NAME = "repo-remote";
    private static final String USERNAME = "user";
    private final Comparator<CurationVersionForRequest> versionComparator =
            Comparator.comparing(CurationVersionForRequest::version);
    @Mock
    private CurationService curationService;
    @Mock
    private RepositoryService repositoryService;
    @InjectMocks
    private PackageCurationServiceImpl packageCurationServiceImpl;

    @Nested
    @DisplayName("canCallFilterBlockedVersions Tests")
    class CanCallFilterBlockedVersionsTests {

        @Test
        @DisplayName("Should return false if curation service is disabled")
        void canCallFilterBlockedVersions_curationDisabled_returnsFalse() {
            when(curationService.isEnabled()).thenReturn(false);
            boolean result = packageCurationServiceImpl.canCallFilterBlockedVersions(REPO_NAME);
            assertFalse(result);
            verify(curationService, never()).isIndexVersionsCompliantEnabled();
            verify(repositoryService, never()).getRemoteRepoConfigByKey(anyString());
        }

        @Test
        @DisplayName("Should return false if index compliant is off globally and package is not force CVS")
        void canCallFilterBlockedVersions_indexCompliantDisabledGlobally_returnsFalse() {
            RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create(REPO_NAME, RepoType.Npm);
            when(curationService.isEnabled()).thenReturn(true);
            when(curationService.isIndexVersionsCompliantEnabled()).thenReturn(false);
            when(curationService.getForceCVSPackages()).thenReturn(Set.of());
            when(repositoryService.getRemoteRepoConfigByKey(REPO_NAME)).thenReturn(remoteRepoConfig);
            when(curationService.isCurationEnabledForRepo(remoteRepoConfig)).thenReturn(true);
            boolean result = packageCurationServiceImpl.canCallFilterBlockedVersions(REPO_NAME);
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return true when force CVS is enabled for the repo package type")
        void canCallFilterBlockedVersions_forceCVSPackageEnabled_returnsTrue() {
            RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create(REPO_NAME, RepoType.Npm);
            when(curationService.isEnabled()).thenReturn(true);
            when(curationService.isIndexVersionsCompliantEnabled()).thenReturn(false);
            when(curationService.getForceCVSPackages()).thenReturn(Set.of(PackageType.NPM));
            when(repositoryService.getRemoteRepoConfigByKey(REPO_NAME)).thenReturn(remoteRepoConfig);
            when(curationService.isCurationEnabledForRepo(remoteRepoConfig)).thenReturn(true);
            boolean result = packageCurationServiceImpl.canCallFilterBlockedVersions(REPO_NAME);
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false if remote repo config is not found")
        void canCallFilterBlockedVersions_repoConfigNotFound_returnsFalse() {
            when(curationService.isEnabled()).thenReturn(true);
            when(repositoryService.getRemoteRepoConfigByKey(REPO_NAME)).thenReturn(null);
            boolean result = packageCurationServiceImpl.canCallFilterBlockedVersions(REPO_NAME);
            assertFalse(result);
            verify(curationService, never()).isCurationEnabledForRepo(any());
        }

        @Test
        @DisplayName("Should return false if curation is not enabled for the specific repository")
        void canCallFilterBlockedVersions_curationDisabledForRepo_returnsFalse() {
            RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create(REPO_NAME, RepoType.Npm);
            when(curationService.isEnabled()).thenReturn(true);
            when(repositoryService.getRemoteRepoConfigByKey(REPO_NAME)).thenReturn(remoteRepoConfig);
            when(curationService.isCurationEnabledForRepo(remoteRepoConfig)).thenReturn(false);
            boolean result = packageCurationServiceImpl.canCallFilterBlockedVersions(REPO_NAME);
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return true if all conditions are met")
        void canCallFilterBlockedVersions_allConditionsMet_returnsTrue() {
            RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create(REPO_NAME, RepoType.Npm);
            when(curationService.isEnabled()).thenReturn(true);
            when(curationService.isIndexVersionsCompliantEnabled()).thenReturn(true);
            when(repositoryService.getRemoteRepoConfigByKey(REPO_NAME)).thenReturn(remoteRepoConfig);
            when(curationService.isCurationEnabledForRepo(remoteRepoConfig)).thenReturn(true);
            boolean result = packageCurationServiceImpl.canCallFilterBlockedVersions(REPO_NAME);
            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("filterBlockedVersions Tests")
    class FilterBlockedVersionsTests {

        private CurationRequest sampleCurationRequest;
        private CurationVersionForRequest version100Date, version101Date, version090Date;
        private CurationVersionForRequest version200NoDate, version210NoDate;

        @BeforeEach
        void setUp() {
            version101Date =
                    new CurationVersionForRequest("1.0.1", "url/1.0.1", Instant.parse("2023-01-02T10:00:00.000000Z"));
            version100Date =
                    new CurationVersionForRequest("1.0.0", "url/1.0.0", Instant.parse("2023-01-01T10:00:00.123456Z"));
            version090Date =
                    new CurationVersionForRequest("0.9.0", "url/0.9.0", Instant.parse("2022-12-31T10:00:00.654321Z"));

            List<CurationVersionForRequest> versionsWithDates =
                    new ArrayList<>(Arrays.asList(version100Date, version101Date, version090Date));

            version200NoDate = new CurationVersionForRequest("2.0.0", "url/2.0.0", null);
            version210NoDate = new CurationVersionForRequest("2.1.0", "url/2.1.0", null);
            List<CurationVersionForRequest> versionsWithNullDates =
                    new ArrayList<>(Arrays.asList(version210NoDate, version200NoDate));

            List<CurationVersionForRequest> versionsMixedDates = new ArrayList<>(Arrays.asList(
                    new CurationVersionForRequest("3.0.0", "url/3.0.0", Instant.parse("2023-03-01T10:00:00.112233Z")),
                    new CurationVersionForRequest("3.1.0", "url/3.1.0", null),
                    new CurationVersionForRequest("3.0.5", "url/3.0.5", Instant.parse("2023-03-05T10:00:00.445566Z"))
            ));

            // Mutable lists are returned from mocks so sortVersions can sort in-place (PH 5.634+ defensively
            // copies versions inside CurationPackageForRequest, which would make real records immutable).
            CurationPackageForRequest packageWithDates = mock(CurationPackageForRequest.class);
            when(packageWithDates.versions()).thenReturn(versionsWithDates);
            CurationPackageForRequest packageWithNullDates = mock(CurationPackageForRequest.class);
            when(packageWithNullDates.versions()).thenReturn(versionsWithNullDates);
            CurationPackageForRequest packageWithMixedDates = mock(CurationPackageForRequest.class);
            when(packageWithMixedDates.versions()).thenReturn(versionsMixedDates);

            List<CurationPackageForRequest> packages =
                    Arrays.asList(packageWithDates, packageWithNullDates, packageWithMixedDates);
            sampleCurationRequest = new CurationRequest(REPO_NAME, PackageType.PYPI, packages, USERNAME);
        }

        @Test
        @DisplayName("Should sort versions by release date descending if all dates are present")
        void filterBlockedVersions_sortsByReleaseDate_whenAllDatesPresent() {
            CurationResponse expectedResponse = mock(CurationResponse.class);
            when(curationService.filterBlockedVersions(any(CurationRequest.class))).thenReturn(expectedResponse);

            packageCurationServiceImpl.filterBlockedVersions(sampleCurationRequest, versionComparator);

            ArgumentCaptor<CurationRequest> requestCaptor = ArgumentCaptor.forClass(CurationRequest.class);
            verify(curationService).filterBlockedVersions(requestCaptor.capture());
            List<CurationVersionForRequest> sortedVersions = requestCaptor.getValue().packages().getFirst().versions();

            assertEquals(version101Date, sortedVersions.get(0), "Version 1.0.1 should be first (latest date)");
            assertEquals(version100Date, sortedVersions.get(1), "Version 1.0.0 should be second");
            assertEquals(version090Date, sortedVersions.get(2), "Version 0.9.0 should be third (oldest date)");
        }

        @Test
        @DisplayName("Should sort versions by provided comparator (version string) if any release date is null")
        void filterBlockedVersions_sortsByComparator_whenAnyDateIsNull() {
            CurationResponse expectedResponse = mock(CurationResponse.class);
            when(curationService.filterBlockedVersions(any(CurationRequest.class))).thenReturn(expectedResponse);

            packageCurationServiceImpl.filterBlockedVersions(sampleCurationRequest, versionComparator);

            ArgumentCaptor<CurationRequest> requestCaptor = ArgumentCaptor.forClass(CurationRequest.class);
            verify(curationService).filterBlockedVersions(requestCaptor.capture());

            List<CurationVersionForRequest> sortedNullDateVersions =
                    requestCaptor.getValue().packages().get(1).versions();

            assertEquals(version200NoDate, sortedNullDateVersions.get(0),
                    "version 2.0.0 should be first (sorted by version string)");
            assertEquals(version210NoDate, sortedNullDateVersions.get(1),
                    "version 2.1.0 should be second (sorted by version string)");

            List<CurationVersionForRequest> sortedMixedDateVersions =
                    requestCaptor.getValue().packages().get(2).versions();
            assertEquals("3.0.0", sortedMixedDateVersions.get(0).version());
            assertEquals("3.0.5", sortedMixedDateVersions.get(1).version());
            assertEquals("3.1.0", sortedMixedDateVersions.get(2).version());
        }

        @Test
        @DisplayName("Should call curationService.filterBlockedVersions, return its response, and log debug info")
        void filterBlockedVersions_callsServiceAndReturnsResponse() {
            Logger packageServiceLogger = (Logger) LoggerFactory.getLogger(PackageCurationServiceImpl.class);
            packageServiceLogger.setLevel(Level.DEBUG);

            CurationResponse expectedResponse = mock(CurationResponse.class);
            when(curationService.filterBlockedVersions(any(CurationRequest.class))).thenReturn(expectedResponse);
            when(expectedResponse.debugDescription()).thenReturn("Mocked debug description");

            CurationResponse actualResponse =
                    packageCurationServiceImpl.filterBlockedVersions(sampleCurationRequest, versionComparator);

            verify(curationService).filterBlockedVersions(any(CurationRequest.class));
            assertSame(expectedResponse, actualResponse);
            verify(expectedResponse).debugDescription();
        }

        @Test
        @DisplayName("Should handle null response from curationService gracefully for logging")
        void filterBlockedVersions_handlesNullResponseForLogging() {
            when(curationService.filterBlockedVersions(any(CurationRequest.class))).thenReturn(null);

            assertDoesNotThrow(() -> {
                CurationResponse actualResponse =
                        packageCurationServiceImpl.filterBlockedVersions(sampleCurationRequest, versionComparator);
                assertNull(actualResponse, "Response should be null as mocked");
            }, "Method should not throw an exception with null response");

            verify(curationService).filterBlockedVersions(any(CurationRequest.class));
        }
    }

}