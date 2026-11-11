package org.artifactory.pipelines;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.system.SystemRepoConfigFactory;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.rest.exception.RestException;
import org.artifactory.security.SecurityServiceImpl;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.artifactory.pipelines.InternalPipelinesService.PIPE_INFO_REPO_SUFFIX;
import static org.artifactory.projects.ProjectsService.DEFAULT_ENVIRONMENT;
import static org.artifactory.repo.system.SystemRepos.PIPEINFO_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.PIPEINFO;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * @author Alexei Vainshtein
 */
public class PipelinesServiceImplTest {

    private PipelinesServiceImpl service;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private AuthorizationService authService;
    @Mock
    private SecurityService securityService;
    @Mock
    private InternalRepositoryService internalRepositoryService;
    @Mock
    private CentralConfigService centralConfigService;

    private final String projectPrefix = "proj";
    private final String projectRepo = projectPrefix + PIPE_INFO_REPO_SUFFIX;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        service = spy(new PipelinesServiceImpl());
        service.setAuthService(authService);
        service.setSecurityService(securityService);
        service.setInternalRepositoryService(internalRepositoryService);
        service.setProjectsService(projectsService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void testIsPipeInfoRepo() {
        String key = "somekey";
        service.isPipeInfoRepo(key);
        verify(internalRepositoryService, times(1)).isPipeInfoRepository(key);
    }

    @Test
    public void testGetRepoName() {
        assertEquals(service.getRepoName("default"), PIPEINFO_REPO_KEY);
        assertEquals(service.getRepoName(projectPrefix), projectRepo);
    }

    @Test(dataProvider = "repoPerProjectProvider")
    public void testCreateAndRetrieveRepoName(String projectPrefix, String projectRepo) {
        doNothing().when(service).createPipeInfoRepoIfNeeded(anyString(), anyString());
        assertEquals(service.createAndRetrieveRepoName(projectPrefix), projectRepo);
        verify(service, times(1)).createPipeInfoRepoIfNeeded(anyString(), anyString());
    }

    @Test
    public void testCreatePipeInfoRepoIfNeeded() {
        doNothing().when(service).createRepoAndAddToProject(projectRepo, projectPrefix);
        when(internalRepositoryService.getRepositoryConfigByKey(projectRepo)).thenReturn(null);
        service.createPipeInfoRepoIfNeeded(projectRepo, projectPrefix);
        verify(service, times(1)).createRepoAndAddToProject(eq(projectRepo), eq(projectPrefix));

        RepoConfig repoConfig = mock(RepoConfig.class);
        doNothing().when(service).validateExistingRepo(projectRepo, repoConfig);
        when(internalRepositoryService.getRepositoryConfigByKey(projectRepo)).thenReturn(repoConfig);
        service.createPipeInfoRepoIfNeeded(projectRepo, projectPrefix);
        verify(service, times(1)).validateExistingRepo(eq(projectRepo), eq(repoConfig));
    }

    @Test(dataProvider = "validatorProvider")
    public void testValidateExistingRepo(boolean isLocal, boolean shouldThrowException, RepoType type) {
        RepoConfig repo = isLocal ? mock(LocalRepoConfig.class) : mock(RepoConfig.class);
        when(repo.getPackageType()).thenReturn(type);
        try {
            service.validateExistingRepo(projectRepo, repo);
            assertFalse(shouldThrowException);
        } catch (IllegalArgumentException e) {
            assertTrue(shouldThrowException);
            assertTrue(e.getMessage().contains("is already taken"));
        }
    }

    @Test
    public void testCreatePipeInfoRepository() {
        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        when(centralConfigService.getMutableDescriptor()).thenReturn(descriptor);
        doAnswer(answer -> {
            assertEquals(answer.getArguments().length, 2);
            assertEquals(answer.getArgument(0, MutableCentralConfigDescriptor.class), descriptor);
            LocalRepoDescriptor localRepoDescriptor = answer.getArgument(1, LocalRepoDescriptor.class);
            assertEquals(localRepoDescriptor.getType(), RepoType.PipeInfo);
            assertEquals(localRepoDescriptor.getKey(), projectRepo);
            return null;
        }).when(centralConfigService).saveEditedDescriptorAndReload(any(), any());
        service.createRepoAndAddToProject(projectRepo, "dummy");
    }

    @Test
    public void testValidateProvidedParamsProject() {
        service.validateProvidedParams(null, null);

        ProjectSlimEntity slimEntity = ProjectSlimEntity.builder().build();
        when(projectsService.getProjectSpec(ProjectsService.DEFAULT_PROJECT)).thenReturn(slimEntity);
        service.validateProvidedParams(null, ProjectsService.DEFAULT_PROJECT);
        try {
            service.validateProvidedParams(null, "test");
            fail("Project not found, should've failed");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "No project matches the project key test prefix");
        }
    }

    @Test
    public void testValidateProvidedParamsRepo() {
        service.validateProvidedParams(null, null);

        ProjectSlimEntity slimEntity = ProjectSlimEntity.builder().build();
        when(projectsService.getProjectSpec(ProjectsService.DEFAULT_PROJECT)).thenReturn(slimEntity);
        service.validateProvidedParams(PIPEINFO_REPO_KEY, null);

        try {
            service.validateProvidedParams("null", null);
            fail("Project not found, should've failed");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Pipe repo null is not matching the convention of pipe info repos");
        }

        try {
            service.validateProvidedParams("-pipe-info", null);
            fail("Project not found, should've failed");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Pipe repo -pipe-info is not matching the convention of pipe info repos");
        }
    }

    @Test(dataProvider = "saveContentProvider")
    public void testSaveContent(int hits, boolean canDeploy, boolean exceptionExpected) throws IOException {
        doNothing().when(service).validateProvidedParams(anyString(), anyString());
        InputStream is = mock(InputStream.class);
        doAnswer(invocationOnMock -> projectRepo).when(service).getAndCreatePipeRepo(eq(projectRepo), eq(null));
        RepoPath repoPath = RepoPathFactory.create(projectRepo, "path/to/file");
        when(authService.canDeploy(repoPath)).thenReturn(canDeploy);
        when(securityService.currentUsername()).thenReturn("Name");
        String err = "This is error from deploy";
        try {
            doAnswer(answer -> {
                BasicStatusHolder statusHolder = new BasicStatusHolder();
                if (exceptionExpected && hits > 0) {
                    Logger log = Mockito.mock(Logger.class);
                    statusHolder.error(err, log);
                }
                return statusHolder;
            }).when(internalRepositoryService).deploy(any(), any());
            service.saveContent("path/to/file", projectRepo, null, is);
            assertFalse(exceptionExpected);
        } catch (ForbiddenException e) {
            assertTrue(exceptionExpected);
            assertEquals(e.getMessage(), String.format("User %s is not permitted to use this api", "Name"));
        } catch (RestException e) {
            assertTrue(exceptionExpected);
            assertEquals(e.getMessage(), err);
        }
        verify(internalRepositoryService, times(hits)).deploy(repoPath, is);
    }

    @Test
    public void testCreateRepoAndAddToProject() {
        service.createRepoAndAddToProject(projectRepo, projectPrefix);
        verify(securityService, times(1)).doAsSystem(any());

        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityServiceImpl securityService = mock(SecurityServiceImpl.class);
        SystemRepoConfigFactory systemRepoConfigFactory = mock(SystemRepoConfigFactory.class);
        service.setAuthService(authorizationService);
        service.setSecurityService(securityService);
        service.setSystemRepoConfigFactory(systemRepoConfigFactory);
        doCallRealMethod().when(securityService).doAsSystem(any());
        doNothing().when(systemRepoConfigFactory).buildSystemRepo(PIPEINFO, projectRepo);
        service.createRepoAndAddToProject(projectRepo, projectPrefix);
        verify(systemRepoConfigFactory, times(1)).buildSystemRepo(PIPEINFO, projectRepo);
        verify(projectsService, times(1))
                .addProjectRepository(projectRepo, projectPrefix, Set.of(DEFAULT_ENVIRONMENT));
    }

    @Test
    public void testGetAndCreatePipeRepo() {
        doAnswer(answer -> ("repo")).when(service).createAndRetrieveRepoName(projectPrefix);
        assertEquals(service.getAndCreatePipeRepo("", projectPrefix), "repo");
        verify(service, times(1)).createAndRetrieveRepoName(projectPrefix);

        try {
            assertEquals(service.getAndCreatePipeRepo("wrongRepo", projectPrefix), "repo");
            fail("Expected to fail since wrong repository");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(),
                    String.format("Repository %s doesn't meet the pipeline info convention", "wrongRepo"));
        }
        doNothing().when(service).createRepoBasedOnProjectAndRepo(projectRepo, projectPrefix);
        assertEquals(service.getAndCreatePipeRepo(projectRepo, projectPrefix), projectRepo);
        verify(service, times(1)).createRepoBasedOnProjectAndRepo(projectRepo, projectPrefix);

        doNothing().when(service).createPipeRepoIfNeededBasedOnRepo(null);
        assertEquals(service.getAndCreatePipeRepo(null, null), PIPEINFO_REPO_KEY);
        verify(service, times(1)).createPipeRepoIfNeededBasedOnRepo(null);
    }

    @Test
    public void testCreateRepoBasedOnProjectAndRepo() {
        try {
            service.createRepoBasedOnProjectAndRepo("test", projectPrefix);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(),
                    String.format("Repository %s doesn't belong to project key %s", "test", projectPrefix));
        }
        Set<String> projectRepos = new HashSet<>();
        projectRepos.add(projectRepo);
        when(projectsService.getProjectRepositoriesKeys(eq(projectPrefix), anyBoolean())).thenReturn(projectRepos);
        service.createRepoBasedOnProjectAndRepo(projectRepo, projectPrefix);
        verify(projectsService, times(1)).getProjectRepositoriesKeys(projectPrefix, false);

        when(projectsService.getProjectRepositoriesKeys(eq(projectPrefix), anyBoolean())).thenReturn(new HashSet<>());
        when(internalRepositoryService.getRepositoryConfigByKey(projectRepo)).thenReturn(null);
        doNothing().when(service).createRepoAndAddToProject(projectRepo, projectPrefix);
        service.createRepoBasedOnProjectAndRepo(projectRepo, projectPrefix);
        verify(service, times(1)).createRepoAndAddToProject(projectRepo, projectPrefix);

        RepoConfig repo = mock(RepoConfig.class);
        when(internalRepositoryService.getRepositoryConfigByKey(projectRepo)).thenReturn(repo);
        try {
            service.createRepoBasedOnProjectAndRepo(projectRepo, projectPrefix);
            fail("Expected to fail since repo is not part of the project");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(),
                    String.format("Found repo %s, however, this repo doesn't belong to project %s", projectRepo,
                            projectPrefix));
        }
    }

    @Test
    public void testCreatePipeRepoIfNeededBasedOnRepo() {
        doNothing().when(service).createPipeInfoRepoIfNeeded(PIPEINFO_REPO_KEY, ProjectsService.DEFAULT_PROJECT);
        service.createPipeRepoIfNeededBasedOnRepo(null);
        verify(service, times(1)).createPipeInfoRepoIfNeeded(PIPEINFO_REPO_KEY, ProjectsService.DEFAULT_PROJECT);

        ProjectSlimEntity entity = ProjectSlimEntity.builder().projectKey(projectPrefix).build();
        when(projectsService.getProjectSpec(projectPrefix)).thenReturn(entity);
        doNothing().when(service).createPipeInfoRepoIfNeeded(projectRepo, projectPrefix);
        service.createPipeRepoIfNeededBasedOnRepo(projectRepo);
        verify(service, times(1)).createPipeInfoRepoIfNeeded(projectRepo, projectPrefix);

        when(projectsService.getProjectSpec(projectPrefix)).thenReturn(null);
        try {
            service.createPipeRepoIfNeededBasedOnRepo(projectRepo);
            fail("Expected to fail since project doesn't exists for this key");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), String.format("No project matches the project key %s prefix", projectPrefix));
        }

    }

    @DataProvider
    public Object[][] saveContentProvider() {
        return new Object[][] {
                {1, true, false},
                {1, true, true},
                {0, false, true}
        };
    }

    @DataProvider
    public Object[][] repoPerProjectProvider() {
        return new Object[][] {
                {ProjectsService.DEFAULT_PROJECT, PIPEINFO_REPO_KEY},
                {projectPrefix, projectRepo},
        };
    }

    @DataProvider
    public Object[][] validatorProvider() {
        return new Object[][] {
                {true, false, RepoType.PipeInfo},
                {true, true, RepoType.Gems},
                {false, true, RepoType.PipeInfo},
                {false, true, RepoType.Generic},
        };
    }
}