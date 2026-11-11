package org.artifactory.ui.utils;

import com.google.common.collect.Maps;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GenericTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GradleTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.IvyTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.LuaRocksTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.MavenTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SbtTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AgentPackagesTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SkillsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualSelectedRepository;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;
import org.artifactory.ui.rest.model.onboarding.QuickRepositoryModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.CreateRepositoryConfigService;
import org.artifactory.ui.rest.service.onboarding.CreateDefaultReposResponseModel;
import org.artifactory.util.RepoLayoutUtils;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_LUAROCKS_REGISTRY_URL;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType.LOCAL;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType.REMOTE;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType.VIRTUAL;
import static org.artifactory.util.RepoLayoutUtils.IVY_DEFAULT_NAME;
import static org.artifactory.util.RepoLayoutUtils.MAVEN_2_DEFAULT_NAME;
import static org.artifactory.util.RepoLayoutUtils.SBT_DEFAULT_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class DefaultRepoCreatorTest extends ArtifactoryHomeBoundTest {

    public static String REPO_NAME = "repo";

    @BeforeMethod
    public void setUp() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        Map<String, LocalRepoDescriptor> reposMap = Maps.newLinkedHashMap();
        reposMap.put(REPO_NAME, new FederatedRepoDescriptor());
        CentralConfigService service = mock(CentralConfigService.class);
        when(service.getMutableDescriptor()).thenReturn(descriptor);
        when(context.getCentralConfig()).thenReturn(service);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        when(repositoryConfigService.getLocalOrFederatedRepoConfigByKey(any())).thenReturn(mock(LocalConfig.class));
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testIsDefaultDeploymentRepoValid() {
        DefaultRepoCreator repoCreator = new DefaultRepoCreator();
        QuickRepositoryModel model = new QuickRepositoryModel();
        model.setDefaultDeploymentRepo(REPO_NAME);
        assertTrue(repoCreator.isDefaultDeploymentRepoValid(model));
    }

    @Test
    public void testLocalQuickModel() throws RepoConfigException {
        DefaultRepoCreator repoCreator = new DefaultRepoCreator();
        Set<String> envs = Set.of("prod");
        QuickRepositoryModel model = createModel("repo2", LOCAL, RepoType.Generic, "jfrog1", envs);
        CreateDefaultReposResponseModel responseModel = new CreateDefaultReposResponseModel();
        CreateRepositoryConfigService createRepoService = mock(CreateRepositoryConfigService.class);
        RestResponse restResponse = mock(RestResponse.class);
        var repoConfigCaptor = ArgumentCaptor.forClass(RepositoryConfigModel.class);
        when(createRepoService.createRepoConfig(
                eq(restResponse), repoConfigCaptor.capture(), any(), any(), anyBoolean()))
                .thenReturn(mock(RepoConfig.class));

        repoCreator.createQuickRepo(createRepoService, restResponse, model, responseModel, false);

        assertTrue(repoConfigCaptor.getValue() instanceof LocalRepositoryConfigModel);
        var localModel = (LocalRepositoryConfigModel) repoConfigCaptor.getValue();
        var general = localModel.getGeneral();
        assertEquals(general.getRepoKey(), "repo2");
        assertEquals(envs, general.getEnvironments());
        assertEquals(general.getProjectKey(), "jfrog1");
        var basic = localModel.getBasic();
        assertEquals(basic.getLayout(), "simple-default");
        assertNull(basic.getXrayConfig());
        assertNull(basic.getKeypair());
        assertNull(basic.getPrimaryKeyPair());
        assertNull(basic.getSecondaryKeyPair());
        TypeSpecificConfigModel typeSpecific = localModel.getTypeSpecific();
        assertTrue(typeSpecific instanceof GenericTypeSpecificConfigModel);
        assertFalse(((GenericTypeSpecificConfigModel) typeSpecific).isListRemoteFolderItems());
    }

    @Test
    public void testRemoteQuickModel() throws RepoConfigException {
        DefaultRepoCreator repoCreator = new DefaultRepoCreator();
        Set<String> envs = Set.of("prod");
        QuickRepositoryModel model = createModel("repo2", REMOTE, RepoType.Maven, "jfrog1", envs);
        CreateDefaultReposResponseModel responseModel = new CreateDefaultReposResponseModel();
        CreateRepositoryConfigService createRepoService = mock(CreateRepositoryConfigService.class);
        RestResponse restResponse = mock(RestResponse.class);
        var repoConfigCaptor = ArgumentCaptor.forClass(RepositoryConfigModel.class);
        when(createRepoService.createRepoConfig(
                eq(restResponse), repoConfigCaptor.capture(), any(), any(), anyBoolean()))
                .thenReturn(mock(RepoConfig.class));

        repoCreator.createQuickRepo(createRepoService, restResponse, model, responseModel, true);

        assertTrue(repoConfigCaptor.getValue() instanceof RemoteRepositoryConfigModel);
        var remoteModel = (RemoteRepositoryConfigModel) repoConfigCaptor.getValue();
        var general = remoteModel.getGeneral();
        assertEquals(general.getRepoKey(), "repo2");
        assertEquals(envs, general.getEnvironments());
        assertEquals(general.getProjectKey(), "jfrog1");
        var basic = remoteModel.getBasic();
        assertEquals(basic.getLayout(), MAVEN_2_DEFAULT_NAME);
        assertEquals(basic.getUrl(), "https://repo1.maven.org/maven2/");
        assertNull(basic.getXrayConfig());
        assertNull(basic.getKeypair());
        assertNull(basic.getPrimaryKeyPair());
        assertNull(basic.getSecondaryKeyPair());
        TypeSpecificConfigModel typeSpecific = remoteModel.getTypeSpecific();
        assertTrue(typeSpecific instanceof MavenTypeSpecificConfigModel);
        assertFalse(((MavenTypeSpecificConfigModel) typeSpecific).isListRemoteFolderItems());
    }

    @DataProvider
    public static Object[][] repoTypes() {
        return new Object[][] {{RepoType.Maven, MAVEN_2_DEFAULT_NAME, MavenTypeSpecificConfigModel.class},
                {RepoType.Gradle, MAVEN_2_DEFAULT_NAME, GradleTypeSpecificConfigModel.class},
                {RepoType.Ivy, IVY_DEFAULT_NAME, IvyTypeSpecificConfigModel.class},
                {RepoType.SBT, SBT_DEFAULT_NAME, SbtTypeSpecificConfigModel.class}};
    }

    @Test(dataProvider = "repoTypes")
    public void testVirtualQuickModel(RepoType repoType, String layout,
            Class<? extends TypeSpecificConfigModel> typeSpecificClass)
            throws RepoConfigException {
        DefaultRepoCreator repoCreator = new DefaultRepoCreator();
        Set<String> envs = Set.of("prod");
        QuickRepositoryModel model = createModel("repo2", VIRTUAL, repoType, "jfrog1", envs);
        model.setIncludedLocalRepositories(List.of(REPO_NAME));
        CreateDefaultReposResponseModel responseModel = new CreateDefaultReposResponseModel();
        CreateRepositoryConfigService createRepoService = mock(CreateRepositoryConfigService.class);
        RestResponse restResponse = mock(RestResponse.class);
        var repoConfigCaptor = ArgumentCaptor.forClass(RepositoryConfigModel.class);
        when(createRepoService.createRepoConfig(
                eq(restResponse), repoConfigCaptor.capture(), any(), any(), anyBoolean()))
                .thenReturn(mock(RepoConfig.class));

        repoCreator.createQuickRepo(createRepoService, restResponse, model, responseModel, true);

        assertTrue(repoConfigCaptor.getValue() instanceof VirtualRepositoryConfigModel);
        var virtualModel = (VirtualRepositoryConfigModel) repoConfigCaptor.getValue();
        var general = virtualModel.getGeneral();
        assertEquals(general.getRepoKey(), "repo2");
        assertEquals(envs, general.getEnvironments());
        assertEquals(general.getProjectKey(), "jfrog1");
        var basic = virtualModel.getBasic();
        assertEquals(basic.getLayout(), layout);
        assertEquals(basic.getSelectedRepositories().size(), 1);
        VirtualSelectedRepository selectedRepo = basic.getSelectedRepositories().get(0);
        assertEquals(selectedRepo.getRepoName(), REPO_NAME);
        assertEquals(selectedRepo.getType(), "local");
        assertNull(basic.getXrayConfig());
        assertNull(basic.getKeypair());
        assertNull(basic.getPrimaryKeyPair());
        assertNull(basic.getSecondaryKeyPair());
        TypeSpecificConfigModel typeSpecific = virtualModel.getTypeSpecific();
        assertTrue(typeSpecificClass.isInstance(typeSpecific),
                "Expected instance of " + typeSpecificClass.getName() + " but got " +
                        typeSpecific.getClass().getName());
        assertFalse(((MavenTypeSpecificConfigModel) typeSpecific).isListRemoteFolderItems());
    }

    @Test
    public void testJemLocalQuickModel_featureIsDisabled() throws RepoConfigException {
        DefaultRepoCreator repoCreator = new DefaultRepoCreator();
        Set<String> envs = Set.of("prod");
        QuickRepositoryModel model = createModel("repo2", LOCAL, RepoType.Jem, "jfrog1", envs);
        CreateDefaultReposResponseModel responseModel = new CreateDefaultReposResponseModel();
        CreateRepositoryConfigService createRepoService = mock(CreateRepositoryConfigService.class);
        RestResponse restResponse = mock(RestResponse.class);
        var repoConfigCaptor = ArgumentCaptor.forClass(RepositoryConfigModel.class);
        when(createRepoService.createRepoConfig(
                eq(restResponse), repoConfigCaptor.capture(), any(), any(), anyBoolean()))
                .thenReturn(mock(RepoConfig.class));

        repoCreator.createQuickRepo(createRepoService, restResponse, model, responseModel, false);

        assertTrue(repoConfigCaptor.getValue() instanceof LocalRepositoryConfigModel);
        var localModel = (LocalRepositoryConfigModel) repoConfigCaptor.getValue();
        var general = localModel.getGeneral();
        assertEquals(general.getRepoKey(), "repo2");
        assertEquals(envs, general.getEnvironments());
        assertEquals(general.getProjectKey(), "jfrog1");
        var basic = localModel.getBasic();
        assertEquals(basic.getLayout(), "simple-default");
        assertNull(basic.getXrayConfig());
        assertNull(basic.getKeypair());
        assertNull(basic.getPrimaryKeyPair());
        assertNull(basic.getSecondaryKeyPair());
        TypeSpecificConfigModel typeSpecific = localModel.getTypeSpecific();
        assertTrue(typeSpecific instanceof GenericTypeSpecificConfigModel);
        assertFalse(((GenericTypeSpecificConfigModel) typeSpecific).isListRemoteFolderItems());
    }

    @Test
    public void testLuaRocksLocalQuickModel() throws RepoConfigException {
        DefaultRepoCreator repoCreator = new DefaultRepoCreator();
        QuickRepositoryModel model = createModel("lxs-luarocks-local", LOCAL, RepoType.LuaRocks, "default", null);
        CreateDefaultReposResponseModel responseModel = new CreateDefaultReposResponseModel();
        CreateRepositoryConfigService createRepoService = mock(CreateRepositoryConfigService.class);
        RestResponse restResponse = mock(RestResponse.class);
        var repoConfigCaptor = ArgumentCaptor.forClass(RepositoryConfigModel.class);
        when(createRepoService.createRepoConfig(
                eq(restResponse), repoConfigCaptor.capture(), any(), any(), anyBoolean()))
                .thenReturn(mock(RepoConfig.class));

        repoCreator.createQuickRepo(createRepoService, restResponse, model, responseModel, true);

        assertTrue(repoConfigCaptor.getValue() instanceof LocalRepositoryConfigModel);
        var localModel = (LocalRepositoryConfigModel) repoConfigCaptor.getValue();
        var general = localModel.getGeneral();
        assertEquals(general.getRepoKey(), "lxs-luarocks-local");
        assertEquals(general.getProjectKey(), "default");
        assertTrue(general.getEnvironments().isEmpty());
        var basic = localModel.getBasic();
        assertEquals(basic.getLayout(), RepoLayoutUtils.LUAROCKS_DEFAULT_NAME);
        assertNull(basic.getXrayConfig());
        TypeSpecificConfigModel typeSpecific = localModel.getTypeSpecific();
        assertTrue(typeSpecific instanceof LuaRocksTypeSpecificConfigModel);
        assertEquals(typeSpecific.getRepoType(), RepoType.LuaRocks);
    }

    @Test
    public void testLuaRocksRemoteQuickModel() throws RepoConfigException {
        DefaultRepoCreator repoCreator = new DefaultRepoCreator();
        QuickRepositoryModel model = createModel("lxs-luarocks-remote", REMOTE, RepoType.LuaRocks, "default", null);
        CreateDefaultReposResponseModel responseModel = new CreateDefaultReposResponseModel();
        CreateRepositoryConfigService createRepoService = mock(CreateRepositoryConfigService.class);
        RestResponse restResponse = mock(RestResponse.class);
        var repoConfigCaptor = ArgumentCaptor.forClass(RepositoryConfigModel.class);
        when(createRepoService.createRepoConfig(
                eq(restResponse), repoConfigCaptor.capture(), any(), any(), anyBoolean()))
                .thenReturn(mock(RepoConfig.class));

        repoCreator.createQuickRepo(createRepoService, restResponse, model, responseModel, true);

        assertTrue(repoConfigCaptor.getValue() instanceof RemoteRepositoryConfigModel);
        var remoteModel = (RemoteRepositoryConfigModel) repoConfigCaptor.getValue();
        var general = remoteModel.getGeneral();
        assertEquals(general.getRepoKey(), "lxs-luarocks-remote");
        assertEquals(general.getProjectKey(), "default");
        assertTrue(general.getEnvironments().isEmpty());
        var basic = remoteModel.getBasic();
        assertEquals(basic.getLayout(), RepoLayoutUtils.LUAROCKS_DEFAULT_NAME);
        assertEquals(basic.getUrl(), DEFAULT_LUAROCKS_REGISTRY_URL);
        assertNull(basic.getXrayConfig());
        TypeSpecificConfigModel typeSpecific = remoteModel.getTypeSpecific();
        assertTrue(typeSpecific instanceof LuaRocksTypeSpecificConfigModel);
        assertEquals(typeSpecific.getRepoType(), RepoType.LuaRocks);
        assertEquals(typeSpecific.getUrl(), DEFAULT_LUAROCKS_REGISTRY_URL);
    }

    @Test
    public void testLuaRocksVirtualQuickModel() throws RepoConfigException {
        DefaultRepoCreator repoCreator = new DefaultRepoCreator();
        QuickRepositoryModel model = createModel("lxs-luarocks", VIRTUAL, RepoType.LuaRocks, "default", null);
        model.setIncludedLocalRepositories(List.of("lxs-luarocks-local"));
        model.setIncludedRemoteRepositories(List.of("lxs-luarocks-remote"));
        model.setDefaultDeploymentRepo(REPO_NAME);
        CreateDefaultReposResponseModel responseModel = new CreateDefaultReposResponseModel();
        CreateRepositoryConfigService createRepoService = mock(CreateRepositoryConfigService.class);
        RestResponse restResponse = mock(RestResponse.class);
        var repoConfigCaptor = ArgumentCaptor.forClass(RepositoryConfigModel.class);
        when(createRepoService.createRepoConfig(
                eq(restResponse), repoConfigCaptor.capture(), any(), any(), anyBoolean()))
                .thenReturn(mock(RepoConfig.class));

        repoCreator.createQuickRepo(createRepoService, restResponse, model, responseModel, true);

        assertTrue(repoConfigCaptor.getValue() instanceof VirtualRepositoryConfigModel);
        var virtualModel = (VirtualRepositoryConfigModel) repoConfigCaptor.getValue();
        var general = virtualModel.getGeneral();
        assertEquals(general.getRepoKey(), "lxs-luarocks");
        assertEquals(general.getProjectKey(), "default");
        assertTrue(general.getEnvironments().isEmpty());
        var basic = virtualModel.getBasic();
        assertEquals(basic.getLayout(), RepoLayoutUtils.LUAROCKS_DEFAULT_NAME);
        assertEquals(basic.getSelectedRepositories().size(), 2);
        assertEquals(basic.getSelectedRepositories().get(0).getRepoName(), "lxs-luarocks-local");
        assertEquals(basic.getSelectedRepositories().get(0).getType(), "local");
        assertEquals(basic.getSelectedRepositories().get(1).getRepoName(), "lxs-luarocks-remote");
        assertEquals(basic.getSelectedRepositories().get(1).getType(), "remote");
        TypeSpecificConfigModel typeSpecific = virtualModel.getTypeSpecific();
        assertTrue(typeSpecific instanceof LuaRocksTypeSpecificConfigModel);
        assertEquals(typeSpecific.getRepoType(), RepoType.LuaRocks);
    }

    @Test
    public void testSkillsLocalQuickModel() throws RepoConfigException {
        DefaultRepoCreator repoCreator = new DefaultRepoCreator();
        Set<String> envs = Set.of("prod");
        QuickRepositoryModel model = createModel("repo2", LOCAL, RepoType.Skills, "jfrog1", envs);
        CreateDefaultReposResponseModel responseModel = new CreateDefaultReposResponseModel();
        CreateRepositoryConfigService createRepoService = mock(CreateRepositoryConfigService.class);
        RestResponse restResponse = mock(RestResponse.class);
        var repoConfigCaptor = ArgumentCaptor.forClass(RepositoryConfigModel.class);
        when(createRepoService.createRepoConfig(
                eq(restResponse), repoConfigCaptor.capture(), any(), any(), anyBoolean()))
                .thenReturn(mock(RepoConfig.class));

        repoCreator.createQuickRepo(createRepoService, restResponse, model, responseModel, false);

        assertTrue(repoConfigCaptor.getValue() instanceof LocalRepositoryConfigModel);
        var localModel = (LocalRepositoryConfigModel) repoConfigCaptor.getValue();
        var general = localModel.getGeneral();
        assertEquals(general.getRepoKey(), "repo2");
        assertEquals(envs, general.getEnvironments());
        assertEquals(general.getProjectKey(), "jfrog1");
        var basic = localModel.getBasic();
        assertEquals(basic.getLayout(), RepoLayoutUtils.SKILLS_DEFAULT_NAME);
        assertNull(basic.getXrayConfig());
        assertNull(basic.getKeypair());
        assertNull(basic.getPrimaryKeyPair());
        assertNull(basic.getSecondaryKeyPair());
        TypeSpecificConfigModel typeSpecific = localModel.getTypeSpecific();
        assertTrue(typeSpecific instanceof SkillsTypeSpecificConfigModel);
        assertEquals(typeSpecific.getRepoType(), RepoType.Skills);
    }

    @Test
    public void testAgentPackagesLocalQuickModel() throws RepoConfigException {
        DefaultRepoCreator repoCreator = new DefaultRepoCreator();
        Set<String> envs = Set.of("prod");
        QuickRepositoryModel model = createModel("repo2", LOCAL, RepoType.AgentPackages, "jfrog1", envs);
        CreateDefaultReposResponseModel responseModel = new CreateDefaultReposResponseModel();
        CreateRepositoryConfigService createRepoService = mock(CreateRepositoryConfigService.class);
        RestResponse restResponse = mock(RestResponse.class);
        var repoConfigCaptor = ArgumentCaptor.forClass(RepositoryConfigModel.class);
        when(createRepoService.createRepoConfig(
                eq(restResponse), repoConfigCaptor.capture(), any(), any(), anyBoolean()))
                .thenReturn(mock(RepoConfig.class));

        repoCreator.createQuickRepo(createRepoService, restResponse, model, responseModel, false);

        assertTrue(repoConfigCaptor.getValue() instanceof LocalRepositoryConfigModel);
        var localModel = (LocalRepositoryConfigModel) repoConfigCaptor.getValue();
        var general = localModel.getGeneral();
        assertEquals(general.getRepoKey(), "repo2");
        assertEquals(envs, general.getEnvironments());
        assertEquals(general.getProjectKey(), "jfrog1");
        var basic = localModel.getBasic();
        assertEquals(basic.getLayout(), RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME);
        assertNull(basic.getXrayConfig());
        assertNull(basic.getKeypair());
        assertNull(basic.getPrimaryKeyPair());
        assertNull(basic.getSecondaryKeyPair());
        TypeSpecificConfigModel typeSpecific = localModel.getTypeSpecific();
        assertTrue(typeSpecific instanceof AgentPackagesTypeSpecificConfigModel);
        assertEquals(typeSpecific.getRepoType(), RepoType.AgentPackages);
    }

    private static QuickRepositoryModel createModel(String repoName, RepositoryType type, RepoType packageType,
            String project, Set<String> envs) {
        QuickRepositoryModel model = new QuickRepositoryModel();
        model.setRepoName(repoName);
        model.setRepoType(type);
        model.setPackageType(packageType);
        model.setProject(project);
        model.setEnvs(envs);
        return model;
    }

}