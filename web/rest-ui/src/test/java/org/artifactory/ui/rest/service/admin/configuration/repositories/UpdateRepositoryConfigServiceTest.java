package org.artifactory.ui.rest.service.admin.configuration.repositories;

import org.apache.http.HttpStatus;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.CommonLocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.UpdateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.security.crypto.exception.CryptoRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateRepositoryConfigServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private UpdateRepoConfigHelper updateRepoConfigHelper;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private RepoConfigValidator repoValidator;
    private AutoCloseable mockitoAutoCloseable;
    private UpdateRepositoryConfigService service;

    @BeforeEach
    void setup() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContext context = Mockito.mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        service = new UpdateRepositoryConfigService(updateRepoConfigHelper, projectsService, repoValidator);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testCryptoRuntimeExceptionHandling() throws RepoConfigException {
        String repoKey = "testRepoKey";
        var model = prepareModel("testProjectKey", repoKey, Set.of("test"));
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();
        var requestMock = mock(ArtifactoryRestRequest.class);

        when(requestMock.getImodel()).thenReturn(model);
        when(repositoryConfigService.existsByRepoKey(repoKey)).thenReturn(true);
        doThrow(new CryptoRuntimeException("Decryption failed")).when(model).updateRepo(updateRepoConfigHelper, false);

        service.execute(requestMock, response);

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getResponseCode());
        assertEquals("The password could not be decrypted", response.getFeedbackMsg().getError());
    }

    @Test
    void testExecute() throws RepoConfigException {
        String repoKey = "testRepoKey";
        String projectKey = "testProjectKey";
        Set<String> envs = Set.of("test");
        var model = prepareModel(projectKey, repoKey, envs);
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();
        var requestMock = mock(ArtifactoryRestRequest.class);

        when(requestMock.getImodel()).thenReturn(model);
        when(repositoryConfigService.existsByRepoKey(repoKey)).thenReturn(true);

        service.execute(requestMock, response);
        verify(model, times(1)).updateRepo(eq(updateRepoConfigHelper), anyBoolean());
    }

    @ParameterizedTest
    @MethodSource("updateRepoEnvironmentsIfNeededDataProvider")
    void testUpdateRepoEnvironmentsIfNeeded(Set<String> existingEnvs, Set<String> envsFromRequest,
            int timesToCallAccess) {
        String repoKey = "testRepoKey";
        String projectKey = "testProjectKey";
        var model = prepareModel(projectKey, repoKey, envsFromRequest);
        ProjectResourceEntity projectResourceEntity = ProjectResourceEntity.builder()
                .environments(existingEnvs)
                .build();
        when(projectsService.getProjectRepositoryEntity(projectKey, repoKey)).thenReturn(projectResourceEntity);

        service.updateRepoEnvironmentsIfNeeded(model, repoKey);
        verify(projectsService, times(timesToCallAccess)).updateProjectRepository(repoKey, envsFromRequest);
    }

    @ParameterizedTest
    @MethodSource("updateRepoEnvironmentsIfNeededProjectIsNullDataProvider")
    void testUpdateRepoEnvironmentsIfNeededWithNullProject(Set<String> envsFromRequest) {
        String repoKey = "testRepoKey";
        String projectKey = "testProjectKey";
        var model = prepareModel(projectKey, repoKey, envsFromRequest);

        when(projectsService.getProjectRepositoryEntity(projectKey, repoKey)).thenReturn(null);

        service.updateRepoEnvironmentsIfNeeded(model, repoKey);
        verify(projectsService, times(1)).updateProjectRepository(repoKey, envsFromRequest);
    }

    @Test
    void testRepoConfigExceptionHandling() throws RepoConfigException {
        // A RepoConfigException from env validation is handled by its dedicated catch: the response uses the
        // exception's own status code and the standard "Failed to update repository <key>: <message>" wrapping.
        String repoKey = "build-info-repo";
        var model = prepareModel("testProjectKey", repoKey, Set.of("DEV"));
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();
        var requestMock = mock(ArtifactoryRestRequest.class);
        when(requestMock.getImodel()).thenReturn(model);
        doThrow(new RepoConfigException("Assigning environments is not allowed for this repository type",
                HttpStatus.SC_BAD_REQUEST))
                .when(repoValidator).validateEnvironmentsForRepoType(eq(model), Mockito.any());

        service.execute(requestMock, response);

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getResponseCode());
        assertEquals("Failed to update repository " + repoKey
                        + ": Assigning environments is not allowed for this repository type",
                response.getFeedbackMsg().getError());
    }

    @Test
    void validateEnvironmentsForRepoType_usesActualAssignedProject() throws RepoConfigException {
        // The request omits the project key (defaults to "default"), but the repo is actually assigned to "realproj".
        // Current environments must be looked up on the actual project, otherwise a legitimate re-send is falsely
        // rejected because the "default" project returns no entity.
        String repoKey = "build-info-repo";
        String actualProjectKey = "realproj";
        var model = prepareModel(ProjectsService.DEFAULT_PROJECT, repoKey, Set.of("DEV"));
        when(projectsService.getProjectKeyOfRepository(repoKey)).thenReturn(actualProjectKey);
        when(projectsService.getProjectRepositoryEntity(actualProjectKey, repoKey))
                .thenReturn(ProjectResourceEntity.builder().resourceName(repoKey).environment("DEV").build());

        service.validateEnvironmentsForRepoType(model, repoKey);

        verify(projectsService).getProjectKeyOfRepository(repoKey);
        verify(projectsService).getProjectRepositoryEntity(actualProjectKey, repoKey);
        verify(projectsService, never()).getProjectRepositoryEntity(ProjectsService.DEFAULT_PROJECT, repoKey);
    }

    static Stream<Arguments> updateRepoEnvironmentsIfNeededDataProvider() {
        return Stream.of(
                Arguments.of(Set.of("test"), Set.of("test"), 0),
                Arguments.of(Set.of("test", "test1"), Set.of("test"), 1)
        );
    }

    static Stream<Arguments> updateRepoEnvironmentsIfNeededProjectIsNullDataProvider() {
        return Stream.of(
                Arguments.of(Set.of("test")),
                Arguments.of(new HashSet<>())
        );
    }

    private CommonLocalRepositoryConfigModel prepareModel(String projectKey, String repoKey, Set<String> envs) {
        CommonLocalRepositoryConfigModel model = spy(new LocalRepositoryConfigModel());
        GeneralRepositoryConfigModel generalModel = new GeneralRepositoryConfigModel();
        generalModel.setRepoKey(repoKey);
        generalModel.setProjectKey(projectKey);
        generalModel.setEnvironments(envs);
        model.setGeneral(generalModel);
        return model;
    }
}
