package org.artifactory.ui.rest.service.admin.configuration.repositories;

import org.apache.http.HttpStatus;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigModelBuilder;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Yevdo Abramov
 * Created on 03/11/2020
 */
public class GetRepositoryConfigServiceTest {
    private static final String REPO_KEY = "prj-myrepo";
    private static final String TRASH_REPO_KEY = "auto-trashcan";
    private static final String CACHE_REPO_KEY = "myremote-cache";
    private static final String PROJECT_NAME = "prj";
    @Mock
    private ProjectsService projectsService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RepoConfigModelBuilder configBuilder;

    private GetRepositoryConfigService service;
    private AutoCloseable mockitoAutoCloseable;

    @BeforeMethod
    public void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties systemProperties = mock(ArtifactorySystemProperties.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(systemProperties);
        when(systemProperties.getProperty(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        ArtifactoryHome.bind(artifactoryHome);
        service = new GetRepositoryConfigService();
        service.setConfigBuilder(configBuilder);
        service.setProjectsService(projectsService);
        service.setRepositoryService(repositoryService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        mockitoAutoCloseable.close();
    }

    @Test
    public void testExecuteWhenRepoIsUnmanaged() {
        // given — trash repos must return 404 without touching the config store
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("local");
        when(request.getPathParamByKey("repoKey")).thenReturn(TRASH_REPO_KEY);

        // when
        service.execute(request, response);

        // then
        assertEquals(response.getResponseCode(), HttpStatus.SC_NOT_FOUND);
        verifyNoMoreInteractions(projectsService, repositoryService, configBuilder);
    }

    @Test
    public void testExecuteWhenRepoIsCacheRepo() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("local");
        when(request.getPathParamByKey("repoKey")).thenReturn(CACHE_REPO_KEY);
        LocalCacheRepoConfig cacheRepoConfig =
                LocalCacheRepoConfig.create(RemoteRepoConfig.create("myremote", RepoType.Maven));
        when(repositoryService.getRepoConfigByKey(CACHE_REPO_KEY)).thenReturn(cacheRepoConfig);

        service.execute(request, response);

        assertEquals(response.getResponseCode(), HttpStatus.SC_NOT_FOUND);
        verify(repositoryService).getRepoConfigByKey(CACHE_REPO_KEY);
        verifyNoMoreInteractions(projectsService, repositoryService, configBuilder);
    }

    @Test
    public void testExecuteWhenRepoNotFound() {
        // given — repo key not in the config store at all
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("local");
        when(request.getPathParamByKey("repoKey")).thenReturn(REPO_KEY);
        when(repositoryService.getRepoConfigByKey(REPO_KEY)).thenReturn(null);

        // when
        service.execute(request, response);

        // then
        assertEquals(response.getResponseCode(), HttpStatus.SC_NOT_FOUND);
        verify(repositoryService).getRepoConfigByKey(REPO_KEY);
        verifyNoMoreInteractions(projectsService, repositoryService, configBuilder);
    }

    @Test
    public void testExecuteWhenRepoIsInfoRepo() {
        // given — repo exists but is a hidden/info repo that must not be exposed
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("local");
        when(request.getPathParamByKey("repoKey")).thenReturn(REPO_KEY);
        LocalRepoConfig infoRepoConfig = LocalRepoConfig.create(REPO_KEY, RepoType.Maven);
        when(repositoryService.getRepoConfigByKey(REPO_KEY)).thenReturn(infoRepoConfig);
        when(repositoryService.isInfoOrHiddenRepo(same(infoRepoConfig))).thenReturn(true);

        // when
        service.execute(request, response);

        // then
        assertEquals(response.getResponseCode(), HttpStatus.SC_NOT_FOUND);
        verify(repositoryService).getRepoConfigByKey(REPO_KEY);
        verify(repositoryService).isInfoOrHiddenRepo(same(infoRepoConfig));
        verifyNoMoreInteractions(projectsService, repositoryService, configBuilder);
    }

    @Test
    public void testExecuteWhenRepoTypeMismatch() {
        // given — key exists but is a remote repo requested as local
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("local");
        when(request.getPathParamByKey("repoKey")).thenReturn(REPO_KEY);
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create(REPO_KEY, RepoType.Maven);
        when(repositoryService.getRepoConfigByKey(REPO_KEY)).thenReturn(remoteRepoConfig);

        // when
        service.execute(request, response);

        // then
        assertEquals(response.getResponseCode(), HttpStatus.SC_NOT_FOUND);
        verify(repositoryService).getRepoConfigByKey(REPO_KEY);
        verify(repositoryService).isInfoOrHiddenRepo(same(remoteRepoConfig));
        verifyNoMoreInteractions(projectsService, repositoryService, configBuilder);
    }

    @Test
    public void testExecuteWhenRepoHasProjectMetadata() {
        // given
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("local");
        when(request.getPathParamByKey("repoKey")).thenReturn(REPO_KEY);

        LocalRepoConfig localConfig = LocalRepoConfig.create(REPO_KEY, RepoType.Maven);
        when(repositoryService.getRepoConfigByKey(REPO_KEY)).thenReturn(localConfig);
        when(projectsService.getProjectKeyOfRepository(REPO_KEY)).thenReturn(PROJECT_NAME);
        when(projectsService.getProjectRepositoryEntity(PROJECT_NAME, REPO_KEY))
                .thenReturn(ProjectResourceEntity.builder()
                        .resourceName(REPO_KEY).environment("test1").environment("test2").build());
        doAnswer((Answer<Void>) invocation -> {
            Object[] args = invocation.getArguments();
            LocalRepositoryConfigModel configModel = (LocalRepositoryConfigModel) args[1];
            configModel.setGeneral(new GeneralRepositoryConfigModel());
            return null;
        }).when(configBuilder)
                .populateLocalDescriptorValuesToModel(eq(localConfig), any());

        // when
        service.execute(request, response);

        // then
        assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
        RepositoryConfigModel repositoryConfigModel = (RepositoryConfigModel) response.getIModel();
        assertNotNull(repositoryConfigModel);
        assertTrue(repositoryConfigModel.toString().startsWith("{"), "response body should be JSON");
        assertEquals(repositoryConfigModel.getGeneral().getProjectKey(), PROJECT_NAME);
        assertTrue(repositoryConfigModel.getGeneral().getEnvironments().containsAll(Set.of("test1", "test2")));

        verify(repositoryService).getRepoConfigByKey(REPO_KEY);
        verify(repositoryService).isInfoOrHiddenRepo(same(localConfig));
        verify(projectsService).getProjectKeyOfRepository(REPO_KEY);
        verify(projectsService).getProjectRepositoryEntity(PROJECT_NAME, REPO_KEY);
        verify(configBuilder).populateLocalDescriptorValuesToModel(eq(localConfig), any());
        verifyNoMoreInteractions(projectsService, repositoryService, configBuilder);
    }

    @Test
    public void testExecuteWhenRepoHasDefaultProjectMetadata() {
        // given
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("local");
        when(request.getPathParamByKey("repoKey")).thenReturn(REPO_KEY);

        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(REPO_KEY, RepoType.Maven);
        when(repositoryService.getRepoConfigByKey(REPO_KEY)).thenReturn(localRepoConfig);
        when(projectsService.getProjectKeyOfRepository(REPO_KEY)).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(projectsService.getProjectRepositoryEntity(ProjectsService.DEFAULT_PROJECT, REPO_KEY))
                .thenReturn(null);
        doAnswer((Answer<Void>) invocation -> {
            Object[] args = invocation.getArguments();
            LocalRepositoryConfigModel configModel = (LocalRepositoryConfigModel) args[1];
            configModel.setGeneral(new GeneralRepositoryConfigModel());
            return null;
        }).when(configBuilder)
                .populateLocalDescriptorValuesToModel(eq(localRepoConfig), any());

        // when
        service.execute(request, response);

        // then
        assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
        RepositoryConfigModel repositoryConfigModel = (RepositoryConfigModel) response.getIModel();
        assertNotNull(repositoryConfigModel);
        assertTrue(repositoryConfigModel.toString().startsWith("{"), "response body should be JSON");
        assertEquals(repositoryConfigModel.getGeneral().getProjectKey(), ProjectsService.DEFAULT_PROJECT);

        verify(repositoryService).getRepoConfigByKey(REPO_KEY);
        verify(repositoryService).isInfoOrHiddenRepo(same(localRepoConfig));
        verify(projectsService).getProjectKeyOfRepository(REPO_KEY);
        verify(projectsService).getProjectRepositoryEntity(ProjectsService.DEFAULT_PROJECT, REPO_KEY);
        verify(configBuilder).populateLocalDescriptorValuesToModel(eq(localRepoConfig), any());
        verifyNoMoreInteractions(projectsService, repositoryService, configBuilder);
    }

    @Test
    public void testFedAttrOptIn_withFederatedRepoType_returns400() {
        // given — fedAttrOptIn=true with FEDERATED repoType is explicitly rejected
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("federated");
        when(request.getPathParamByKey("repoKey")).thenReturn(REPO_KEY);
        when(ArtifactoryHome.get().getArtifactoryProperties()
                .getBooleanProperty(ConstantValues.repositoriesFederationsAttributeEnabled)).thenReturn(true);
        when(request.getQueryParamByKey("isFedAttrSupported")).thenReturn("true");

        // when
        service.execute(request, response);

        // then
        assertEquals(response.getResponseCode(), HttpStatus.SC_BAD_REQUEST);
        verifyNoMoreInteractions(projectsService, repositoryService, configBuilder);
    }

    @Test
    public void testListLocalRepos_federatedInfoWithMembers_included() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("local");
        when(request.getPathParamByKey("repoKey")).thenReturn("");
        when(ArtifactoryHome.get().getArtifactoryProperties()
                .getBooleanProperty(ConstantValues.repositoriesFederationsAttributeEnabled)).thenReturn(true);
        when(request.getQueryParamByKey("isFedAttrSupported")).thenReturn("true");

        FederatedRepoConfig normalFedRepo = FederatedRepoConfig.create(REPO_KEY, RepoType.Maven);
        FederatedRepoConfig fedBuildInfoRepo = FederatedRepoConfig.create("build-info-fed", RepoType.BuildInfo);
        FederatedMember member1 = new FederatedMember();
        member1.setUrlOrTemplate("https://example.com/artifactory");
        FederatedMember member2 = new FederatedMember();
        member2.setUrlOrTemplate("https://example2.com/artifactory");
        fedBuildInfoRepo.setFederatedMembers(List.of(member1, member2));
        when(repositoryService.getLocalAndFederatedRepoConfigsIncludingInfoRepos())
                .thenReturn(List.of(normalFedRepo, fedBuildInfoRepo));
        when(repositoryService.isInfoOrHiddenRepo(same(normalFedRepo))).thenReturn(false);
        when(repositoryService.isInfoOrHiddenRepo(same(fedBuildInfoRepo))).thenReturn(true);
        doAnswer((Answer<Void>) inv -> {
            ((LocalRepositoryConfigModel) inv.getArgument(1)).setGeneral(new GeneralRepositoryConfigModel());
            return null;
        }).when(configBuilder).populateLocalDescriptorValuesToModel(any(), any());
        when(projectsService.getProjectKeyOfRepository(any())).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(projectsService.getProjectRepositoryEntity(eq(ProjectsService.DEFAULT_PROJECT), any())).thenReturn(null);

        service.execute(request, response);

        assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
        assertEquals(((List<?>) response.getiModelList()).size(), 2);
        verify(repositoryService).isInfoOrHiddenRepo(same(normalFedRepo));
        verify(repositoryService).isInfoOrHiddenRepo(same(fedBuildInfoRepo));
    }

    @Test
    public void testListLocalRepos_infoRepoFiltered() {
        // given — list of local repos contains an info repo; only the normal one should appear
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("local");
        when(request.getPathParamByKey("repoKey")).thenReturn("");
        LocalRepoConfig normalRepo = LocalRepoConfig.create(REPO_KEY, RepoType.Maven);
        LocalRepoConfig infoRepo = LocalRepoConfig.create("info-repo", RepoType.Maven);
        when(repositoryService.getLocalRepoConfigsExcludingInfoRepos()).thenReturn(List.of(normalRepo, infoRepo));
        when(repositoryService.isInfoOrHiddenRepo(same(normalRepo))).thenReturn(false);
        when(repositoryService.isInfoOrHiddenRepo(same(infoRepo))).thenReturn(true);
        doAnswer((Answer<Void>) invocation -> {
            LocalRepositoryConfigModel model = (LocalRepositoryConfigModel) invocation.getArgument(1);
            model.setGeneral(new GeneralRepositoryConfigModel());
            return null;
        }).when(configBuilder).populateLocalDescriptorValuesToModel(eq(normalRepo), any());
        when(projectsService.getProjectKeyOfRepository(REPO_KEY)).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(projectsService.getProjectRepositoryEntity(ProjectsService.DEFAULT_PROJECT, REPO_KEY)).thenReturn(null);

        // when
        service.execute(request, response);

        // then
        assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
        assertEquals(((List<?>) response.getiModelList()).size(), 1);
        verify(repositoryService).getLocalRepoConfigsExcludingInfoRepos();
        verify(repositoryService).isInfoOrHiddenRepo(same(normalRepo));
        verify(repositoryService).isInfoOrHiddenRepo(same(infoRepo));
        verify(configBuilder).populateLocalDescriptorValuesToModel(eq(normalRepo), any());
        verify(projectsService).getProjectKeyOfRepository(REPO_KEY);
        verify(projectsService).getProjectRepositoryEntity(ProjectsService.DEFAULT_PROJECT, REPO_KEY);
        verifyNoMoreInteractions(projectsService, repositoryService, configBuilder);
    }

    @Test
    public void testListRemoteRepos_isInfoOrHiddenNotCalled() {
        // given — for non-local list, isInfoOrHiddenRepo must never be called
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("remote");
        when(request.getPathParamByKey("repoKey")).thenReturn("");
        RemoteRepoConfig remoteRepo = RemoteRepoConfig.create(REPO_KEY, RepoType.Maven);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(remoteRepo));
        doAnswer((Answer<Void>) invocation -> {
            RemoteRepositoryConfigModel model = (RemoteRepositoryConfigModel) invocation.getArgument(1);
            model.setGeneral(new GeneralRepositoryConfigModel());
            return null;
        }).when(configBuilder).populateRemoteRepositoryConfigValuesToModel(eq(remoteRepo), any());
        when(projectsService.getProjectKeyOfRepository(REPO_KEY)).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(projectsService.getProjectRepositoryEntity(ProjectsService.DEFAULT_PROJECT, REPO_KEY)).thenReturn(null);

        // when
        service.execute(request, response);

        // then
        assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
        verify(repositoryService).getRemoteRepoConfigs();
        verify(configBuilder).populateRemoteRepositoryConfigValuesToModel(eq(remoteRepo), any());
        verify(projectsService).getProjectKeyOfRepository(REPO_KEY);
        verify(projectsService).getProjectRepositoryEntity(ProjectsService.DEFAULT_PROJECT, REPO_KEY);
        verifyNoMoreInteractions(projectsService, repositoryService, configBuilder);
    }

    @Test
    public void testSingleRemoteRepo_isInfoOrHiddenNotCalled() {
        // given — for a non-local single-repo lookup, isInfoOrHiddenRepo must never be called
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("remote");
        when(request.getPathParamByKey("repoKey")).thenReturn(REPO_KEY);
        RemoteRepoConfig remoteRepo = RemoteRepoConfig.create(REPO_KEY, RepoType.Maven);
        when(repositoryService.getRepoConfigByKey(REPO_KEY)).thenReturn(remoteRepo);
        doAnswer((Answer<Void>) invocation -> {
            RemoteRepositoryConfigModel model = (RemoteRepositoryConfigModel) invocation.getArgument(1);
            model.setGeneral(new GeneralRepositoryConfigModel());
            return null;
        }).when(configBuilder).populateRemoteRepositoryConfigValuesToModel(eq(remoteRepo), any());
        when(projectsService.getProjectKeyOfRepository(REPO_KEY)).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(projectsService.getProjectRepositoryEntity(ProjectsService.DEFAULT_PROJECT, REPO_KEY)).thenReturn(null);

        // when
        service.execute(request, response);

        // then
        assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
        assertTrue(response.getIModel().toString().startsWith("{"), "response body should be JSON");
        verify(repositoryService).getRepoConfigByKey(REPO_KEY);
        verify(configBuilder).populateRemoteRepositoryConfigValuesToModel(eq(remoteRepo), any());
        verify(projectsService).getProjectKeyOfRepository(REPO_KEY);
        verify(projectsService).getProjectRepositoryEntity(ProjectsService.DEFAULT_PROJECT, REPO_KEY);
        verifyNoMoreInteractions(projectsService, repositoryService, configBuilder);
    }

    @Test
    public void testListLocalRepos_federatedInfoWithoutMembers_included() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("local");
        when(request.getPathParamByKey("repoKey")).thenReturn("");
        when(ArtifactoryHome.get().getArtifactoryProperties()
                .getBooleanProperty(ConstantValues.repositoriesFederationsAttributeEnabled)).thenReturn(true);
        when(request.getQueryParamByKey("isFedAttrSupported")).thenReturn("true");

        FederatedRepoConfig normalFedRepo = FederatedRepoConfig.create(REPO_KEY, RepoType.Maven);
        FederatedRepoConfig fedBuildInfoNoMembers =
                FederatedRepoConfig.create("build-info-fed-no-members", RepoType.BuildInfo);
        when(repositoryService.getLocalAndFederatedRepoConfigsIncludingInfoRepos())
                .thenReturn(List.of(normalFedRepo, fedBuildInfoNoMembers));
        when(repositoryService.isInfoOrHiddenRepo(same(normalFedRepo))).thenReturn(false);
        when(repositoryService.isInfoOrHiddenRepo(same(fedBuildInfoNoMembers))).thenReturn(true);
        doAnswer((Answer<Void>) inv -> {
            ((LocalRepositoryConfigModel) inv.getArgument(1)).setGeneral(new GeneralRepositoryConfigModel());
            return null;
        }).when(configBuilder).populateLocalDescriptorValuesToModel(any(), any());
        when(projectsService.getProjectKeyOfRepository(any())).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(projectsService.getProjectRepositoryEntity(eq(ProjectsService.DEFAULT_PROJECT), any())).thenReturn(null);

        service.execute(request, response);

        assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
        assertEquals(((List<?>) response.getiModelList()).size(), 2);
    }

    @Test
    public void testFedAttrOptIn_singleFederatedBuildInfoWithMembers_returns200() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("local");
        when(request.getPathParamByKey("repoKey")).thenReturn(REPO_KEY);
        when(ArtifactoryHome.get().getArtifactoryProperties()
                .getBooleanProperty(ConstantValues.repositoriesFederationsAttributeEnabled)).thenReturn(true);
        when(request.getQueryParamByKey("isFedAttrSupported")).thenReturn("true");

        FederatedRepoConfig fedBuildInfoRepo = FederatedRepoConfig.create(REPO_KEY, RepoType.BuildInfo);
        FederatedMember member1 = new FederatedMember();
        member1.setUrlOrTemplate("https://example.com/artifactory");
        FederatedMember member2 = new FederatedMember();
        member2.setUrlOrTemplate("https://example2.com/artifactory");
        fedBuildInfoRepo.setFederatedMembers(List.of(member1, member2));
        when(repositoryService.getRepoConfigByKey(REPO_KEY)).thenReturn(fedBuildInfoRepo);
        when(repositoryService.isInfoOrHiddenRepo(same(fedBuildInfoRepo))).thenReturn(true);
        doAnswer((Answer<Void>) inv -> {
            ((LocalRepositoryConfigModel) inv.getArgument(1)).setGeneral(new GeneralRepositoryConfigModel());
            return null;
        }).when(configBuilder).populateLocalDescriptorValuesToModel(any(), any());
        when(projectsService.getProjectKeyOfRepository(REPO_KEY)).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(projectsService.getProjectRepositoryEntity(ProjectsService.DEFAULT_PROJECT, REPO_KEY)).thenReturn(null);

        service.execute(request, response);

        assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
    }

    @Test
    public void testSingleRepo_infoRepoWithoutMembers_returns404() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("local");
        when(request.getPathParamByKey("repoKey")).thenReturn(REPO_KEY);

        FederatedRepoConfig fedBuildInfoNoMembers = FederatedRepoConfig.create(REPO_KEY, RepoType.BuildInfo);
        when(repositoryService.getRepoConfigByKey(REPO_KEY)).thenReturn(fedBuildInfoNoMembers);
        when(repositoryService.isInfoOrHiddenRepo(same(fedBuildInfoNoMembers))).thenReturn(true);

        service.execute(request, response);

        assertEquals(response.getResponseCode(), HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testSingleVirtualRepo_responseIsJson() {
        // given — single virtual repo lookup must return JSON, not a Lombok toString
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn("virtual");
        when(request.getPathParamByKey("repoKey")).thenReturn(REPO_KEY);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create(REPO_KEY, RepoType.Maven);
        when(repositoryService.getRepoConfigByKey(REPO_KEY)).thenReturn(virtualRepoConfig);
        doAnswer((Answer<Void>) invocation -> {
            Object[] args = invocation.getArguments();
            VirtualRepositoryConfigModel model = (VirtualRepositoryConfigModel) args[1];
            model.setGeneral(new GeneralRepositoryConfigModel());
            return null;
        }).when(configBuilder).populateVirtualRepositoryConfigValuesToModel(eq(virtualRepoConfig), any());
        when(projectsService.getProjectKeyOfRepository(REPO_KEY)).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(projectsService.getProjectRepositoryEntity(ProjectsService.DEFAULT_PROJECT, REPO_KEY)).thenReturn(null);

        // when
        service.execute(request, response);

        // then
        assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
        assertTrue(response.getIModel().toString().startsWith("{"), "response body should be JSON");
        verify(repositoryService).getRepoConfigByKey(REPO_KEY);
        verify(configBuilder).populateVirtualRepositoryConfigValuesToModel(eq(virtualRepoConfig), any());
        verify(projectsService).getProjectKeyOfRepository(REPO_KEY);
        verify(projectsService).getProjectRepositoryEntity(ProjectsService.DEFAULT_PROJECT, REPO_KEY);
        verifyNoMoreInteractions(projectsService, repositoryService, configBuilder);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Full single-repo (GET /{repoType}/{repoKey}) response matrix, mirroring the local / federated / remote
    // decision tables. Columns: requested type | fedAttrOptIn | actual repo type | isInfoOrHidden | expected status.
    //   - local     : info-repo hiding applies only when NOT opted-in; opt-in unlocks federated-as-local.
    //   - federated : opted-in is rejected up front with 400; otherwise plain type-match.
    //   - remote    : plain type-match, fedAttrOptIn irrelevant.
    // -----------------------------------------------------------------------------------------------------------------
    @DataProvider(name = "singleRepoMatrix")
    public static Object[][] singleRepoMatrix() {
        return new Object[][] {
                // requested "local"
                {"local", false, "local", false, HttpStatus.SC_OK, "L1 non-opt-in normal local -> 200"},
                {"local", false, "local", true, HttpStatus.SC_NOT_FOUND, "L2 non-opt-in local info repo hidden -> 404"},
                {"local", false, "remote", false, HttpStatus.SC_NOT_FOUND, "L3 remote asked as local -> 404"},
                {"local", true, "local", false, HttpStatus.SC_OK, "L4 opt-in normal local -> 200"},
                {"local", true, "local", true, HttpStatus.SC_OK, "L5 opt-in shows local info repo -> 200"},
                {"local", true, "federated", false, HttpStatus.SC_OK, "L7 opt-in federated-as-local -> 200"},
                {"local", true, "federated", true, HttpStatus.SC_OK, "L6 opt-in federated info-as-local -> 200"},
                {"local", true, "remote", false, HttpStatus.SC_NOT_FOUND, "L8 opt-in remote asked as local -> 404"},
                {"local", false, "federated", false, HttpStatus.SC_NOT_FOUND, "L9 non-opt-in federated -> 404"},
                // requested "federated"
                {"federated", false, "federated", false, HttpStatus.SC_OK, "F1 federated, no opt-in -> 200"},
                {"federated", false, "federated", true, HttpStatus.SC_OK, "F8 federated info (info ignored) -> 200"},
                {"federated", false, "local", false, HttpStatus.SC_NOT_FOUND, "F2 local asked as federated -> 404"},
                {"federated", false, "remote", false, HttpStatus.SC_NOT_FOUND, "F4 remote asked as federated -> 404"},
                {"federated", true, "federated", false, HttpStatus.SC_BAD_REQUEST, "F5 opt-in + federated type -> 400"},
                {"federated", true, "local", false, HttpStatus.SC_BAD_REQUEST, "F6 opt-in + federated type -> 400"},
                {"federated", true, "remote", false, HttpStatus.SC_BAD_REQUEST, "F7 opt-in + federated type -> 400"},
                // requested "remote" (fedAttrOptIn irrelevant)
                {"remote", false, "remote", false, HttpStatus.SC_OK, "R1 remote, no opt-in -> 200"},
                {"remote", true, "remote", false, HttpStatus.SC_OK, "R2 remote, opt-in irrelevant -> 200"},
                {"remote", false, "local", false, HttpStatus.SC_NOT_FOUND, "R3 local asked as remote -> 404"},
                {"remote", false, "federated", false, HttpStatus.SC_NOT_FOUND, "R5 federated asked as remote -> 404"},
                {"remote", true, "federated", false, HttpStatus.SC_NOT_FOUND, "R6 federated asked as remote -> 404"},
        };
    }

    @Test(dataProvider = "singleRepoMatrix")
    public void testSingleRepoResponseMatrix(String requestedType, boolean fedAttrOptIn, String actualType,
            boolean infoOrHidden, int expectedStatus, String description) {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getPathParamByKey("repoType")).thenReturn(requestedType);
        when(request.getPathParamByKey("repoKey")).thenReturn(REPO_KEY);
        if (fedAttrOptIn) {
            when(ArtifactoryHome.get().getArtifactoryProperties()
                    .getBooleanProperty(ConstantValues.repositoriesFederationsAttributeEnabled)).thenReturn(true);
            when(request.getQueryParamByKey("isFedAttrSupported")).thenReturn("true");
        }
        when(repositoryService.getRepoConfigByKey(REPO_KEY)).thenReturn(configFor(actualType));
        when(repositoryService.isInfoOrHiddenRepo(any())).thenReturn(infoOrHidden);
        stubModelBuild();
        when(projectsService.getProjectKeyOfRepository(any())).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(projectsService.getProjectRepositoryEntity(any(), any())).thenReturn(null);

        service.execute(request, response);

        assertEquals(response.getResponseCode(), expectedStatus, description);
    }

    private static RepoConfig configFor(String actualType) {
        return switch (actualType) {
            case "remote" -> RemoteRepoConfig.create(REPO_KEY, RepoType.Maven);
            case "federated" -> FederatedRepoConfig.create(REPO_KEY, RepoType.Maven);
            case "virtual" -> VirtualRepoConfig.create(REPO_KEY, RepoType.Maven);
            default -> LocalRepoConfig.create(REPO_KEY, RepoType.Maven);
        };
    }

    private void stubModelBuild() {
        doAnswer(setGeneralModel()).when(configBuilder).populateLocalDescriptorValuesToModel(any(), any());
        doAnswer(setGeneralModel()).when(configBuilder).populateRemoteRepositoryConfigValuesToModel(any(), any());
        doAnswer(setGeneralModel()).when(configBuilder).populateVirtualRepositoryConfigValuesToModel(any(), any());
        doAnswer(setGeneralModel()).when(configBuilder).populateFederatedDescriptorValuesToModel(any(), any());
    }

    private static Answer<Void> setGeneralModel() {
        return invocation -> {
            ((RepositoryConfigModel<?, ?, ?>) invocation.getArgument(1)).setGeneral(new GeneralRepositoryConfigModel());
            return null;
        };
    }
}