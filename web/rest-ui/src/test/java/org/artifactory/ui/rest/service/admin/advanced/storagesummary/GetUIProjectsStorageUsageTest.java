package org.artifactory.ui.rest.service.admin.advanced.storagesummary;

import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.ui.rest.model.storage.ProjectStorageUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

class GetUIProjectsStorageUsageTest {

    private final static String TEST_PROJECT = "pkey";
    private final static String TEST_PROJECT_NAME = "The Project";
    private AutoCloseable autoCloseable;
    @Mock
    private StorageService storageService;
    @Mock
    private StorageSummaryService storageSummaryService;
    @Mock
    private ProjectsService projectsService;

    private GetUIProjectsStorageUsage getUIProjectsStorageUsage;

    @BeforeEach
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        getUIProjectsStorageUsage = Mockito.spy(new GetUIProjectsStorageUsage());
        getUIProjectsStorageUsage.setProjectsService(projectsService);
        getUIProjectsStorageUsage.setStorageService(storageService);
        getUIProjectsStorageUsage.setStorageSummaryService(storageSummaryService);

        Map<String, Long> projectsTotalSizes = Map.of(TEST_PROJECT, 1000L);
        StorageSummaryInfo storageSummaryInfo = new StorageSummaryInfo(Map.of(), null, projectsTotalSizes, Map.of());
        Mockito.when(storageSummaryService.getLocalCache()).thenReturn(storageSummaryInfo);
        Mockito.when(projectsService.getAllProjectsManagedByUser()).thenReturn(List.of(ProjectsService.DEFAULT_PROJECT,
                TEST_PROJECT));
        Mockito.when(projectsService.getProjectStorageQuotaBytes(TEST_PROJECT)).thenReturn(10000L);
        Mockito.when(projectsService.getProjectDisplayName(TEST_PROJECT)).thenReturn(TEST_PROJECT_NAME);
    }

    @Test
    void testGetUIProjectsStorageUsageTest() {
        ArtifactoryRestRequest restRequest = Mockito.mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        getUIProjectsStorageUsage.execute(restRequest, response);
        List<ProjectStorageUsage> results = (List<ProjectStorageUsage>) response.getIModel();
        Assertions.assertEquals(1, results.size());
        ProjectStorageUsage projectStorageUsage = results.get(0);
        Assertions.assertEquals(TEST_PROJECT, projectStorageUsage.getProjectKey());
        Assertions.assertEquals(TEST_PROJECT_NAME, projectStorageUsage.getProjectName());
        Assertions.assertEquals(1000, projectStorageUsage.getUsedStorageBytes());
        Assertions.assertEquals(10000, projectStorageUsage.getStorageLimitBytes());
    }

    @AfterEach
    public void cleanup() throws Exception {
        if (autoCloseable != null) {
            autoCloseable.close();
            autoCloseable = null;
        }
    }
}