package org.artifactory.rest.resource.storage;

import jakarta.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.service.ProjectBinarySizeService;
import org.artifactory.storage.service.ProjectSizeEntry;
import org.artifactory.storage.service.StorageCacheService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.AolUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.artifactory.storage.service.ProjectSizeEntry.CalcStatus.SERVER_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for ProjectBinaryStorageResource REST endpoint
 *
 * @author Evgeny Kurtser
 */
class ProjectBinaryStorageResourceTest extends ArtifactoryHomeBoundJUnitTest {

    private AutoCloseable autoCloseable;
    @Mock
    private ProjectBinarySizeService projectBinaryStorageService;
    @Mock
    private ArtifactoryApplicationContext context;

    private MockedStatic<AolUtils> mockedAolUtils;
    private ProjectBinaryStorageResource resource;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        mockedAolUtils = mockStatic(AolUtils.class);
        ArtifactoryContextThreadBinder.bind(context);
        resource = new ProjectBinaryStorageResource(projectBinaryStorageService);
        // Default: feature flag enabled (superuser check required)
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationEnforceDashboardUser, "true");
    }

    @AfterEach
    void tearDown() throws Exception {
        mockedAolUtils.close();
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void testGetAllProjectsBinaryStorage_WhenAuthorized_ReturnsList() {
        // Given
        mockedAolUtils.when(AolUtils::assertDashboardUser).thenAnswer(invocation -> null);
        long timestamp1 = System.currentTimeMillis();
        long timestamp2 = timestamp1 + 1000;
        ProjectBinarySizeService.ProjectBinarySizeInfo info1 =
                new ProjectBinarySizeService.ProjectBinarySizeInfo(new StorageCacheService.StorageCacheEntry<>(
                        "project1", ProjectSizeEntry.success(1024L), timestamp1));
        ProjectBinarySizeService.ProjectBinarySizeInfo info2 =
                new ProjectBinarySizeService.ProjectBinarySizeInfo(new StorageCacheService.StorageCacheEntry<>(
                        "project2", ProjectSizeEntry.success(2048L), timestamp2));
        List<ProjectBinarySizeService.ProjectBinarySizeInfo> allInfo = List.of(info1, info2);

        when(projectBinaryStorageService.getAllProjectsBinaryStorage()).thenReturn(allInfo);

        // When
        try (Response response = resource.getAllProjectsBinaryStorage()) {
            // Then
            assertEquals(HttpStatus.SC_OK, response.getStatus());
            assertNotNull(response.getEntity());
        }
        verify(projectBinaryStorageService).getAllProjectsBinaryStorage();
        mockedAolUtils.verify(AolUtils::assertDashboardUser);
    }

    @Test
    void getAllProjectsBinaryStorage_shouldReturnUnauthorized_whenNotAuthorized() {
        // Arrange
        mockedAolUtils.when(AolUtils::assertDashboardUser)
                .thenThrow(new BadRequestException("Not authorized"));

        // Act
        try (Response response = resource.getAllProjectsBinaryStorage()) {
            // Assert
            assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatus());
        }
        verify(projectBinaryStorageService, never()).getAllProjectsBinaryStorage();
    }

    @Test
    void testGetAllProjectsBinaryStorage_WhenServiceThrowsException_ReturnsInternalServerError() {
        // Given
        mockedAolUtils.when(AolUtils::assertDashboardUser).thenAnswer(invocation -> null);
        when(projectBinaryStorageService.getAllProjectsBinaryStorage())
                .thenThrow(new RuntimeException("Database error"));

        // When
        try (Response response = resource.getAllProjectsBinaryStorage()) {
            // Then
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
            assertNotNull(response.getEntity());
            String entity = (String) response.getEntity();
            assertTrue(entity.contains("Failed to retrieve projects binary storage"));
        }
        verify(projectBinaryStorageService).getAllProjectsBinaryStorage();
    }

    @Test
    void testGetAllProjectsBinaryStorage_WhenEmptyList_ReturnsEmptyList() {
        // Given
        mockedAolUtils.when(AolUtils::assertDashboardUser).thenAnswer(invocation -> null);
        when(projectBinaryStorageService.getAllProjectsBinaryStorage()).thenReturn(List.of());

        // When
        try (Response response = resource.getAllProjectsBinaryStorage()) {
            // Then
            assertEquals(HttpStatus.SC_OK, response.getStatus());
            assertNotNull(response.getEntity());
        }
        verify(projectBinaryStorageService).getAllProjectsBinaryStorage();
    }

    @Test
    void testGetAllProjectsBinaryStorage_WhenProjectHasError_IncludesErrorInResponse() {
        // Given
        mockedAolUtils.when(AolUtils::assertDashboardUser).thenAnswer(invocation -> null);
        long timestamp = System.currentTimeMillis();
        ProjectBinarySizeService.ProjectBinarySizeInfo infoWithError =
                new ProjectBinarySizeService.ProjectBinarySizeInfo(new StorageCacheService.StorageCacheEntry<>(
                        "project1", ProjectSizeEntry.failure(SERVER_ERROR), timestamp));
        List<ProjectBinarySizeService.ProjectBinarySizeInfo> allInfo = List.of(infoWithError);

        when(projectBinaryStorageService.getAllProjectsBinaryStorage()).thenReturn(allInfo);

        // When
        try (Response response = resource.getAllProjectsBinaryStorage()) {
            // Then
            assertEquals(HttpStatus.SC_OK, response.getStatus());
            assertNotNull(response.getEntity());
        }
        verify(projectBinaryStorageService).getAllProjectsBinaryStorage();
    }

    @Test
    void testGetProjectBinaryStorage_WhenAuthorizedButNotFound_ReturnsNotFound() {
        // Given
        String projectKey = "non-existent-project";
        mockedAolUtils.when(AolUtils::assertDashboardUser).thenAnswer(invocation -> null);
        when(projectBinaryStorageService.getProjectBinarySizeInfo(projectKey)).thenReturn(null);

        // When
        try (Response response = resource.getProjectBinaryStorage(projectKey)) {
            // Then
            assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
            String entity = (String) response.getEntity();
            assertTrue(entity.contains("Binary storage calculation not found for project"));
            assertTrue(entity.contains(projectKey));
        }
        verify(projectBinaryStorageService).getProjectBinarySizeInfo(projectKey);
    }

    @Test
    void testGetProjectBinaryStorage_WhenServiceThrowsException_ReturnsInternalServerError() {
        // Given
        String projectKey = "test-project";
        mockedAolUtils.when(AolUtils::assertDashboardUser).thenAnswer(invocation -> null);
        when(projectBinaryStorageService.getProjectBinarySizeInfo(projectKey))
                .thenThrow(new RuntimeException("Database error"));

        // When
        try (Response response = resource.getProjectBinaryStorage(projectKey)) {
            // Then
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
            assertNotNull(response.getEntity());
            String entity = (String) response.getEntity();
            assertTrue(entity.contains("Failed to retrieve project binary storage"));
        }
        verify(projectBinaryStorageService).getProjectBinarySizeInfo(projectKey);
    }

    @Test
    void testGetProjectBinaryStorage_WhenProjectHasError_ReturnsErrorInResponse() {
        // Given
        String projectKey = "test-project";
        mockedAolUtils.when(AolUtils::assertDashboardUser).thenAnswer(invocation -> null);
        long timestamp = System.currentTimeMillis();
        ProjectBinarySizeService.ProjectBinarySizeInfo infoWithError =
                new ProjectBinarySizeService.ProjectBinarySizeInfo(new StorageCacheService.StorageCacheEntry<>(
                        projectKey, ProjectSizeEntry.failure(SERVER_ERROR), timestamp));

        when(projectBinaryStorageService.getProjectBinarySizeInfo(projectKey)).thenReturn(infoWithError);

        // When
        try (Response response = resource.getProjectBinaryStorage(projectKey)) {
            // Then
            assertEquals(HttpStatus.SC_OK, response.getStatus());
            assertNotNull(response.getEntity());
            ProjectBinaryStorageResource.ProjectBinaryStorageModel model =
                    (ProjectBinaryStorageResource.ProjectBinaryStorageModel) response.getEntity();
            assertEquals(projectKey, model.project());
            assertEquals(SERVER_ERROR.getName(), model.status());
            assertNull(model.binariesSize());
            assertEquals(timestamp, model.calculationTimestamp());
        }
        verify(projectBinaryStorageService).getProjectBinarySizeInfo(projectKey);
    }

    @Test
    void testProjectBinaryStorageModel_WhenSuccess_ContainsCorrectFields() {
        // Given
        String project = "test-project";
        ProjectSizeEntry projectSizeStatus = ProjectSizeEntry.success(1024L);
        long timestamp = System.currentTimeMillis();

        // When
        ProjectBinaryStorageResource.ProjectBinaryStorageModel model =
                new ProjectBinaryStorageResource.ProjectBinaryStorageModel(project, projectSizeStatus, timestamp);

        // Then
        assertEquals(project, model.project());
        assertSame(ProjectSizeEntry.CalcStatus.SUCCESS.getName(), model.status());
        assertEquals(1024L, model.binariesSize());
        assertEquals(timestamp, model.calculationTimestamp());
    }

    @Test
    void testProjectBinaryStorageModel_WhenError_ContainsCorrectFields() {
        // Given
        String project = "test-project";
        ProjectSizeEntry projectSizeStatus = ProjectSizeEntry.failure(SERVER_ERROR);
        long timestamp = System.currentTimeMillis();

        // When
        ProjectBinaryStorageResource.ProjectBinaryStorageModel model =
                new ProjectBinaryStorageResource.ProjectBinaryStorageModel(project, projectSizeStatus, timestamp);

        // Then
        assertEquals(project, model.project());
        assertSame(SERVER_ERROR.getName(), model.status());
        assertNull(model.binariesSize());
        assertEquals(timestamp, model.calculationTimestamp());
    }

    @Test
    void testProjectsBinaryStorageModel_ContainsListOfProjects() {
        // Given
        ProjectBinaryStorageResource.ProjectBinaryStorageModel model1 =
                new ProjectBinaryStorageResource.ProjectBinaryStorageModel(
                        "project1", ProjectSizeEntry.success(1024L), System.currentTimeMillis());
        ProjectBinaryStorageResource.ProjectBinaryStorageModel model2 =
                new ProjectBinaryStorageResource.ProjectBinaryStorageModel(
                        "project2", ProjectSizeEntry.success(2048L), System.currentTimeMillis());
        List<ProjectBinaryStorageResource.ProjectBinaryStorageModel> models = List.of(model1, model2);

        // When
        ProjectBinaryStorageResource.ProjectsBinaryStorageModel response =
                new ProjectBinaryStorageResource.ProjectsBinaryStorageModel(models);

        // Then
        assertNotNull(response.projects());
        assertEquals(2, response.projects().size());
        assertEquals(model1, response.projects().get(0));
        assertEquals(model2, response.projects().get(1));
    }

    // ========== Parameterized test for authorization based on feature flag ==========

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAllEndpoints_AuthorizationCheck(boolean featureEnabled) {
        // Given
        String projectKey = "test-project";
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationEnforceDashboardUser,
                String.valueOf(featureEnabled));
        mockedAolUtils.when(AolUtils::assertDashboardUser).thenAnswer(invocation -> null);
        when(projectBinaryStorageService.getAllProjectsBinaryStorage()).thenReturn(List.of());
        long timestamp = System.currentTimeMillis();
        ProjectBinarySizeService.ProjectBinarySizeInfo info =
                new ProjectBinarySizeService.ProjectBinarySizeInfo(new StorageCacheService.StorageCacheEntry<>(
                        projectKey, ProjectSizeEntry.success(1024L), timestamp));
        when(projectBinaryStorageService.getProjectBinarySizeInfo(projectKey)).thenReturn(info);

        // When - call all three endpoints
        try (
                Response getAllResponse = resource.getAllProjectsBinaryStorage();
                Response getOneResponse = resource.getProjectBinaryStorage(projectKey);
                Response calculateResponse = resource.calculateProjectsBinaryStorage()) {

            // Then - verify responses
            assertEquals(HttpStatus.SC_OK, getAllResponse.getStatus());
            assertEquals(HttpStatus.SC_OK, getOneResponse.getStatus());
            assertEquals(HttpStatus.SC_OK, calculateResponse.getStatus());
            assertEquals("Project binary storage calculation started", calculateResponse.getEntity());
        }

        // Verify authorization check was called correct number of times based on feature flag
        if (featureEnabled) {
            mockedAolUtils.verify(AolUtils::assertDashboardUser, org.mockito.Mockito.times(3));
        } else {
            mockedAolUtils.verify(AolUtils::assertDashboardUser, never());
        }
    }

    // ========== Tests for calculateProjectsBinaryStorage() ==========

    @Test
    void testCalculateProjectsBinaryStorage_WhenAuthorized_StartsCalculation() {
        // Given
        mockedAolUtils.when(AolUtils::assertDashboardUser).thenAnswer(invocation -> null);

        // When
        try (Response response = resource.calculateProjectsBinaryStorage()) {
            // Then
            assertEquals(HttpStatus.SC_OK, response.getStatus());
            assertEquals("Project binary storage calculation started", response.getEntity());
        }
        verify(projectBinaryStorageService).calculateAllProjectsSizes();
        mockedAolUtils.verify(AolUtils::assertDashboardUser);
    }

    @Test
    void calculateProjectsBinaryStorage_shouldReturnUnauthorized_whenNotAuthorized() {
        // Arrange
        mockedAolUtils.when(AolUtils::assertDashboardUser)
                .thenThrow(new BadRequestException("Not authorized"));

        // Act
        try (Response response = resource.calculateProjectsBinaryStorage()) {
            // Assert
            assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatus());
        }
        verify(projectBinaryStorageService, never()).calculateAllProjectsSizes();
    }

    @Test
    void testCalculateProjectsBinaryStorage_WhenServiceThrowsException_ReturnsInternalServerError() {
        // Given
        mockedAolUtils.when(AolUtils::assertDashboardUser).thenAnswer(invocation -> null);
        doThrow(new RuntimeException("Calculation failed")).when(projectBinaryStorageService)
                .calculateAllProjectsSizes();

        // When
        try (Response response = resource.calculateProjectsBinaryStorage()) {
            // Then
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
            String entity = (String) response.getEntity();
            assertTrue(entity.contains("Failed to start project binary storage calculation: Calculation failed"));
        }
        verify(projectBinaryStorageService).calculateAllProjectsSizes();
    }
}

