package org.artifactory.ui.rest.service.onboarding;

import org.apache.commons.lang3.builder.DiffResult;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;
import org.artifactory.ui.rest.model.onboarding.CreateQuickReposModel;
import org.artifactory.ui.rest.model.onboarding.QuickRepositoryModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.CreateRepositoryConfigService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

public class CreateQuickReposServiceTest {

    @Mock
    private CreateRepositoryConfigService createRepoService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private RepositoryConfigService repositoryConfigService;

    AutoCloseable mocks;

    @BeforeClass
    public void setup() throws RepoConfigException {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        when(centralConfigService.getMutableDescriptor()).thenReturn(descriptor);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        ArtifactoryContextThreadBinder.bind(context);
        when(createRepoService.createRepoConfig(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(mock(RepoConfig.class));

        DiffResult diffResult = mock(DiffResult.class);
        when(diffResult.getNumberOfDiffs()).thenReturn(0);
        when(centralConfigService.findDiff(any(), any())).thenReturn(diffResult);
    }

    @AfterClass
    public void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testExecute() {
        CreateQuickReposService service = new CreateQuickReposService();
        service.setCreateRepoService(createRepoService);
        service.setAddonsManager(addonsManager);
        service.setProjectsService(projectsService);
        service.setCentralConfigService(centralConfigService);
        service.setRepositoryConfigService(repositoryConfigService);

        ArtifactoryRestRequest<CreateQuickReposModel> request = mock(TestRequest.class);
        CreateQuickReposModel createQuickReposModel = mock(CreateQuickReposModel.class);
        QuickRepositoryModel model = new QuickRepositoryModel();
        model.setRepoName("repo");
        model.setProject("project");
        Set<String> envs = Set.of("prod");
        model.setEnvs(envs);
        model.setRepoType(RepositoryType.LOCAL);
        model.setPackageType(RepoType.Generic);
        when(createQuickReposModel.getRepositories()).thenReturn(List.of(model));
        when(request.getImodel()).thenReturn(createQuickReposModel);
        ArtifactoryRestResponse<?> mock = new ArtifactoryRestResponse<>();
        service.execute(request, mock);

        verify(projectsService, times(1)).validateRepoConfigIsMatchingToProjectSpec("project", "repo", envs);
        verify(projectsService, times(1)).upsertProjectRepository("repo", "project", envs);
    }


    @Test
    public void testExecuteOnDefaultProject() {
        CreateQuickReposService service = new CreateQuickReposService();
        service.setCreateRepoService(createRepoService);
        service.setAddonsManager(addonsManager);
        service.setProjectsService(projectsService);
        service.setCentralConfigService(centralConfigService);
        service.setRepositoryConfigService(repositoryConfigService);

        ArtifactoryRestRequest<CreateQuickReposModel> request = mock(TestRequest.class);
        CreateQuickReposModel createQuickReposModel = mock(CreateQuickReposModel.class);
        QuickRepositoryModel model = new QuickRepositoryModel();
        model.setRepoName("repo");
        model.setProject("default");
        Set<String> envs = Set.of("prod");
        model.setEnvs(envs);
        model.setRepoType(RepositoryType.VIRTUAL);
        model.setPackageType(RepoType.Generic);
        when(createQuickReposModel.getRepositories()).thenReturn(List.of(model));
        when(request.getImodel()).thenReturn(createQuickReposModel);
        ArtifactoryRestResponse<?> mock = new ArtifactoryRestResponse<>();
        service.execute(request, mock);

        verify(projectsService, times(1)).validateRepoConfigIsMatchingToProjectSpec("default", "repo", envs);
        verify(projectsService, times(0)).upsertProjectRepository("repo", "default", envs);
    }

    @Test
    public void testExecute_shouldRemoveReposFromProject_whenBulkInsertFails() throws RepoConfigException {
        CreateQuickReposService service = new CreateQuickReposService();
        service.setCreateRepoService(createRepoService);
        service.setAddonsManager(addonsManager);
        service.setProjectsService(projectsService);
        service.setCentralConfigService(centralConfigService);
        service.setRepositoryConfigService(repositoryConfigService);

        doThrow(new RuntimeException("Bulk insert failed"))
                .when(repositoryConfigService)
                .bulkInsertRepoConfigs(anyList(), anyBoolean(), anyBoolean(), anyBoolean());

        ArtifactoryRestRequest<CreateQuickReposModel> request = mock(TestRequest.class);
        CreateQuickReposModel createQuickReposModel = mock(CreateQuickReposModel.class);
        QuickRepositoryModel model = new QuickRepositoryModel();
        model.setRepoName("repo");
        model.setProject("project");
        Set<String> envs = Set.of("prod");
        model.setEnvs(envs);
        model.setRepoType(RepositoryType.LOCAL);
        model.setPackageType(RepoType.Generic);
        when(createQuickReposModel.getRepositories()).thenReturn(List.of(model));
        when(request.getImodel()).thenReturn(createQuickReposModel);
        ArtifactoryRestResponse<?> responseMock = new ArtifactoryRestResponse<>();

        assertThrows(RuntimeException.class, () -> service.execute(request, responseMock));

        verify(projectsService, times(1)).removeRepositoryFromCurrentProject("repo");
    }

    private abstract static class TestRequest extends ArtifactoryRestRequest<CreateQuickReposModel> {

        public TestRequest(RequestBuilder<CreateQuickReposModel> requestBuilder) {
            super(requestBuilder);
        }
    }

}