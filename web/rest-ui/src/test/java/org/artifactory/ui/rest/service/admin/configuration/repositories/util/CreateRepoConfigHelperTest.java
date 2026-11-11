package org.artifactory.ui.rest.service.admin.configuration.repositories.util;


import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.smartrepo.EdgeSmartRepoAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.NugetGeneralConfig;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.exception.InvalidOperationException;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.validator.virtual.VirtualRepoPackageValidator;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.service.ConfigValidationService;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.MirrorMemberConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GenericTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.replication.remote.RemoteReplicationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CargoTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HelmTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HuggingFaceMLTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NpmTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NugetTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualSelectedRepository;
import org.artifactory.ui.rest.service.admin.configuration.repositories.replication.ReplicationConfigService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.ReplicationConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RemoteRepoPackageValidator;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.ReplicationConfigValidator;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.artifactory.util.DoesNotExistException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.artifactory.api.repo.UpstreamRepoKind.SMART;
import static org.artifactory.util.NuGetPackageTypeConfigMapper.ENABLE_NORMALIZED_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.HUGGINGFACE_ML_REPO_LAYOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateRepoConfigHelperTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ReplicationConfigDescriptorBuilder replicationBuilder;
    @Mock
    private ReplicationConfigService replicationConfigService;
    @Mock
    private ReplicationConfigValidator replicationValidator;
    @Mock
    private RepoConfigValidator repoValidator;
    @Mock
    private ConfigValidationService configValidationService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private CentralConfigService configService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RepositoryConfigService repositoryConfigService;

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;

    @Mock
    private RemoteRepoPackageValidator remoteRepoPackageValidator;

    @Mock
    private VirtualRepoPackageValidator virtualRepoPackageValidator;

    private AutoCloseable mocks;

    CreateRepoConfigHelper helper;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);

        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        ArtifactoryHome.bind(artifactoryHome);

        helper = new CreateRepoConfigHelper();
        helper.setAddonsManager(addonsManager);
        helper.setReplicationBuilder(replicationBuilder);
        helper.setReplicationConfigService(replicationConfigService);
        helper.setReplicationValidator(replicationValidator);
        helper.setRepoValidator(repoValidator);
        helper.setConfigValidationService(configValidationService);
        helper.setProjectsService(projectsService);
        helper.setRepositoryConfigService(repositoryConfigService);
        helper.setRemoteRepoPackageValidator(remoteRepoPackageValidator);
        helper.setVirtualRepoPackageValidator(virtualRepoPackageValidator);

        RepoConfigDescriptorBuilder repoBuilder = new RepoConfigDescriptorBuilder();
        repoBuilder.setCentralConfig(configService);
        repoBuilder.setRepositoryConfigService(repositoryConfigService);
        helper.setRepoBuilder(repoBuilder);

        when(configService.getMutableDescriptor()).thenReturn(new CentralConfigDescriptorImpl());
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    void testHandleVirtual() throws RepoConfigException {
        VirtualRepositoryConfigModel configModel = new VirtualRepositoryConfigModel();
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        LocalRepoConfig repoConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        basic.setSelectedRepositories(List.of(new VirtualSelectedRepository(repoConfig)));
        configModel.setBasic(basic);
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("repo");
        general.setProjectKey("project");
        general.setEnvironments(Set.of("PROD"));
        when(projectsService.getProjectKeyOfRepository("repo")).thenReturn("project");
        configModel.setGeneral(general);
        configModel.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        configModel.setTypeSpecific(new HelmTypeSpecificConfigModel());
        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        when(repoDescriptor.getKey()).thenReturn("local-repo");
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        RepoConfig repoConfigHandled = helper.handleVirtual(configModel, configDescriptor, repositoryService, false);
        assertEquals("repo", repoConfigHandled.getKey());
    }

    @Test
    void testHandleVirtualForNugetPackageType() throws RepoConfigException {
        VirtualRepositoryConfigModel configModel = new VirtualRepositoryConfigModel();
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.NuGet);
        localRepoConfig.getPackageTypeConfig().put(ENABLE_NORMALIZED_VERSION, "true");
        basic.setSelectedRepositories(List.of(new VirtualSelectedRepository(localRepoConfig)));
        configModel.setBasic(basic);
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("repo");
        general.setProjectKey("project");
        general.setEnvironments(Set.of("PROD"));
        configModel.setGeneral(general);
        configModel.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        configModel.setTypeSpecific(buildNugetTypeConfigModel());
        when(repositoryConfigService.getRepoConfigByKey("local-repo")).thenReturn(localRepoConfig);
        when(repositoryConfigService.existsByRepoKey("local-repo")).thenReturn(true);
        when(projectsService.getProjectKeyOfRepository("repo")).thenReturn("project");
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor =
                new RepositoriesGeneralConfigDescriptor();
        NugetGeneralConfig nugetGeneralConfig = new NugetGeneralConfig();
        nugetGeneralConfig.setEnableGlobalNormalization(false);
        repositoriesGeneralConfigDescriptor.setNuget(nugetGeneralConfig);
        configDescriptor.setRepositoriesGeneralConfig(repositoriesGeneralConfigDescriptor);
        when(configService.getDescriptor()).thenReturn(configDescriptor);

        when(projectsService.getProjectRepositoriesKeys(eq("project"), anyBoolean())).thenReturn(Set.of("local-repo"));
        RepoConfig repoConfig = helper.handleVirtual(configModel, configDescriptor, repositoryService, false);

        assertNugetSpecificConfig(repoConfig);
    }

    @Test
    void testHandleVirtualForNugetThrowsInvalidNameExceptionWhenAddingNonNormalizedRepo() {
        VirtualRepositoryConfigModel configModel = new VirtualRepositoryConfigModel();
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.NuGet);
        basic.setSelectedRepositories(List.of(new VirtualSelectedRepository(localRepoConfig)));
        configModel.setBasic(basic);
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("repo");
        general.setProjectKey("project");
        general.setEnvironments(Set.of("PROD"));
        configModel.setGeneral(general);
        configModel.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        configModel.setTypeSpecific(buildNugetTypeConfigModel());
        when(repositoryConfigService.getRepoConfigByKey("local-repo")).thenReturn(localRepoConfig);
        when(repositoryConfigService.existsByRepoKey("local-repo")).thenReturn(true);
        when(projectsService.getProjectKeyOfRepository("repo")).thenReturn("project");
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor =
                new RepositoriesGeneralConfigDescriptor();
        NugetGeneralConfig nugetGeneralConfig = new NugetGeneralConfig();
        nugetGeneralConfig.setEnableGlobalNormalization(false);
        repositoriesGeneralConfigDescriptor.setNuget(nugetGeneralConfig);
        configDescriptor.setRepositoriesGeneralConfig(repositoriesGeneralConfigDescriptor);
        when(configService.getDescriptor()).thenReturn(configDescriptor);
        when(projectsService.getProjectRepositoriesKeys(eq("project"), anyBoolean())).thenReturn(Set.of("local-repo"));

        InvalidOperationException invalidOperationException = assertThrows(InvalidOperationException.class,
                () -> helper.handleVirtual(configModel, configDescriptor, repositoryService, false));
        assertEquals("Nuget Repository repo must have only normalized repositories",
                invalidOperationException.getMessage());
    }

    @Test
    void testHandleVirtualForNugetThrowsDoesNotExistExceptionWhenSelectedRepoNotFound() {
        VirtualRepositoryConfigModel configModel = new VirtualRepositoryConfigModel();
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.NuGet);
        basic.setSelectedRepositories(List.of(new VirtualSelectedRepository(localRepoConfig)));
        configModel.setBasic(basic);
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("repo");
        general.setProjectKey("project");
        general.setEnvironments(Set.of("PROD"));
        configModel.setGeneral(general);
        configModel.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        configModel.setTypeSpecific(buildNugetTypeConfigModel());
        when(repositoryConfigService.existsByRepoKey("local-repo")).thenReturn(true);
        when(projectsService.getProjectKeyOfRepository("repo")).thenReturn("project");
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor =
                new RepositoriesGeneralConfigDescriptor();
        NugetGeneralConfig nugetGeneralConfig = new NugetGeneralConfig();
        nugetGeneralConfig.setEnableGlobalNormalization(false);
        repositoriesGeneralConfigDescriptor.setNuget(nugetGeneralConfig);
        configDescriptor.setRepositoriesGeneralConfig(repositoriesGeneralConfigDescriptor);
        when(configService.getDescriptor()).thenReturn(configDescriptor);
        when(projectsService.getProjectRepositoriesKeys(eq("project"), anyBoolean())).thenReturn(Set.of("local-repo"));

        DoesNotExistException doesNotExistException = assertThrows(DoesNotExistException.class,
                () -> helper.handleVirtual(configModel, configDescriptor, repositoryService, false));
        assertEquals("Selected Repo keylocal-repo not found", doesNotExistException.getMessage());
    }


    @ParameterizedTest
    @MethodSource("getTypeSpecific")
    void testHandleRemote(RepoType repoType, TypeSpecificConfigModel typeSpecificConfig) throws RepoConfigException {

        String REPO_KEY = "repo";

        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);

        RemoteRepositoryConfigModel configModel = new RemoteRepositoryConfigModel();
        RemoteBasicRepositoryConfigModel basic = new RemoteBasicRepositoryConfigModel();

        configModel.setBasic(basic);
        configModel.setAdvanced(new RemoteAdvancedRepositoryConfigModel());

        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey(REPO_KEY);
        general.setProjectKey("project");
        general.setEnvironments(Set.of("PROD"));
        configModel.setGeneral(general);

        RemoteReplicationConfigModel remoteReplicationConfig = new RemoteReplicationConfigModel();
        remoteReplicationConfig.setCronExp("0 0/5 * * * ?");
        configModel.setReplications(List.of(remoteReplicationConfig));

        configModel.setTypeSpecific(typeSpecificConfig);
        when(projectsService.getProjectKeyOfRepository(REPO_KEY)).thenReturn("project");
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor =
                new RepositoriesGeneralConfigDescriptor();
        NugetGeneralConfig nugetGeneralConfig = new NugetGeneralConfig();
        nugetGeneralConfig.setEnableGlobalNormalization(false);
        repositoriesGeneralConfigDescriptor.setNuget(nugetGeneralConfig);
        configDescriptor.setRepositoriesGeneralConfig(repositoriesGeneralConfigDescriptor);
        when(configService.getDescriptor()).thenReturn(configDescriptor);
        configDescriptor.setUrlBase("http://localhost:9001");

        when(projectsService.getProjectRepositoriesKeys(eq("project"), anyBoolean())).thenReturn(Set.of("local-repo"));
        EdgeSmartRepoAddon edgeSmartRepoAddon = mock(EdgeSmartRepoAddon.class);
        when(edgeSmartRepoAddon.getUpstreamRepoKind(any())).thenReturn(SMART);
        when(addonsManager.addonByType(EdgeSmartRepoAddon.class)).thenReturn(edgeSmartRepoAddon);

        List<String> packagesListWithNuget = new ArrayList<>(Arrays.asList("nuget"));
        when(remoteRepoPackageValidator.getRemoteRepoCreateValidationEnabledPackages()).thenReturn(
                packagesListWithNuget);
        doNothing().when(remoteRepoPackageValidator)
                .validate(any(RemoteRepoConfig.class), any(MutableCentralConfigDescriptor.class),
                        any(RemoteRepositoryConfigModel.class));

        RepoConfig repoConfig = helper.handleRemote(configModel, configDescriptor, false);

        assertTrue(repoConfig instanceof RemoteRepoConfig);
        assertEquals(repoType, repoConfig.getPackageType());
        assertTypeSpecific(repoConfig.getPackageType(), repoConfig);
    }

    private void assertTypeSpecific(RepoType packageType, RepoConfig repoConfig) {
        if (requireNonNull(packageType) == RepoType.NuGet) {
            assertNugetSpecificConfig(repoConfig);
        }
    }

    private void assertNugetSpecificConfig(RepoConfig repoConfig) {
        assertTrue(Boolean.parseBoolean(repoConfig.getPackageTypeConfig().get("enableNormalizedVersion")));
    }

    private static Stream<Arguments> getTypeSpecific() {
        return Stream.of(Arguments.of(RepoType.Npm, new NpmTypeSpecificConfigModel()),
                Arguments.of(RepoType.NuGet, buildNugetTypeConfigModel()),
                Arguments.of(RepoType.Cargo, new CargoTypeSpecificConfigModel()));
    }

    private static NugetTypeSpecificConfigModel buildNugetTypeConfigModel() {
        NugetTypeSpecificConfigModel configModel = new NugetTypeSpecificConfigModel();
        configModel.setEnableNormalizedVersion(true);
        configModel.setForceNugetAuthentication(true);
        return configModel;
    }

    @Test
    void testHandleVirtualWhenVirtualValidatorThrowsInvalidOperationException() {
        VirtualRepositoryConfigModel configModel = new VirtualRepositoryConfigModel();
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        String localRepoKey = "local-hf-repo";
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(localRepoKey, RepoType.HuggingFaceML);
        basic.setSelectedRepositories(List.of(new VirtualSelectedRepository(localRepoConfig)));
        configModel.setBasic(basic);
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("virtual-hf-repo");
        general.setProjectKey("project");
        general.setEnvironments(Set.of("PROD"));
        configModel.setGeneral(general);
        configModel.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        configModel.setTypeSpecific(new HuggingFaceMLTypeSpecificConfigModel());
        when(repositoryConfigService.getRepoConfigByKey(localRepoKey)).thenReturn(localRepoConfig);
        when(repositoryConfigService.existsByRepoKey(localRepoKey)).thenReturn(true);
        // Make the validator throw the expected exception
        doThrow(new InvalidOperationException("Old layout not allowed")).when(virtualRepoPackageValidator)
                .validate(any());
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        assertThrows(InvalidOperationException.class,
                () -> helper.handleVirtual(configModel, configDescriptor, repositoryService, false));
    }

    @Test
    void testHandleLocalWithFedAttrOptIn_shouldPopulateFederationMembers() throws RepoConfigException {
        LocalRepositoryConfigModel model = new LocalRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("my-local-fed");
        model.setGeneral(general);
        model.setBasic(new LocalBasicRepositoryConfigModel());
        model.setAdvanced(new LocalAdvancedRepositoryConfigModel());
        model.setTypeSpecific(new GenericTypeSpecificConfigModel());

        MirrorMemberConfigModel mirror1 = new MirrorMemberConfigModel();
        mirror1.setUrl("https://remote1.example.com/artifactory/my-local-fed");
        mirror1.setEnabled(true);
        MirrorMemberConfigModel mirror2 = new MirrorMemberConfigModel();
        mirror2.setUrl("https://remote2.example.com/artifactory/my-local-fed");
        mirror2.setEnabled(true);
        FederationConfigModel federationConfigModel = new FederationConfigModel();
        federationConfigModel.setMirrors(List.of(mirror1, mirror2));
        model.setFederation(federationConfigModel);

        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();

        RepoConfig result = helper.handleLocal(model, configDescriptor, true);

        assertNotNull(result);
        assertTrue(result.getBaseConfig().isFederated());
        assertEquals(2, result.getBaseConfig().getFederationConfig().getMembers().size());
        assertEquals("https://remote1.example.com/artifactory/my-local-fed",
                result.getBaseConfig().getFederationConfig().getMembers().get(0).getUrlOrTemplate());
        assertEquals("https://remote2.example.com/artifactory/my-local-fed",
                result.getBaseConfig().getFederationConfig().getMembers().get(1).getUrlOrTemplate());
    }

    @Test
    void testHandleLocalWithFedAttrOptInFalse_shouldNotPopulateFederationMembers() throws RepoConfigException {
        LocalRepositoryConfigModel model = new LocalRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("my-local-fed");
        model.setGeneral(general);
        model.setBasic(new LocalBasicRepositoryConfigModel());
        model.setAdvanced(new LocalAdvancedRepositoryConfigModel());
        model.setTypeSpecific(new GenericTypeSpecificConfigModel());

        MirrorMemberConfigModel mirror = new MirrorMemberConfigModel();
        mirror.setUrl("https://remote1.example.com/artifactory/my-local-fed");
        mirror.setEnabled(true);
        FederationConfigModel federationConfigModel = new FederationConfigModel();
        federationConfigModel.setMirrors(List.of(mirror));
        model.setFederation(federationConfigModel);

        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();

        RepoConfig result = helper.handleLocal(model, configDescriptor, false);

        assertNotNull(result);
        assertFalse(result.getBaseConfig().isFederated());
    }

    @Test
    void testHandleLocalWithFedAttrOptIn_nullFederation_shouldNotFail() throws RepoConfigException {
        LocalRepositoryConfigModel model = new LocalRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("my-local-no-fed");
        model.setGeneral(general);
        model.setBasic(new LocalBasicRepositoryConfigModel());
        model.setAdvanced(new LocalAdvancedRepositoryConfigModel());
        model.setTypeSpecific(new GenericTypeSpecificConfigModel());
        // no federation set

        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();

        RepoConfig result = helper.handleLocal(model, configDescriptor, true);

        assertNotNull(result);
        assertFalse(result.getBaseConfig().isFederated());
    }

    @Test
    void testHandleVirtualForHuggingFaceWithMixedLayoutLocals_shouldFail() {
        VirtualRepositoryConfigModel configModel = new VirtualRepositoryConfigModel();
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        LocalRepoConfig local1 = LocalRepoConfig.create("local-hf-1", RepoType.HuggingFaceML);
        local1.getPackageTypeConfig().put(HUGGINGFACE_ML_REPO_LAYOUT.getKey(), "true");
        LocalRepoConfig local2 = LocalRepoConfig.create("local-hf-2", RepoType.HuggingFaceML);
        local2.getPackageTypeConfig().put(HUGGINGFACE_ML_REPO_LAYOUT.getKey(), "false");
        basic.setSelectedRepositories(
                List.of(new VirtualSelectedRepository(local1), new VirtualSelectedRepository(local2)));
        configModel.setBasic(basic);
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("virtual-hf-repo");
        general.setProjectKey("project");
        general.setEnvironments(Set.of("PROD"));
        configModel.setGeneral(general);
        configModel.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        configModel.setTypeSpecific(new HuggingFaceMLTypeSpecificConfigModel());
        when(repositoryConfigService.getRepoConfigByKey("local-hf-1")).thenReturn(local1);
        when(repositoryConfigService.getRepoConfigByKey("local-hf-2")).thenReturn(local2);
        when(repositoryConfigService.existsByRepoKey("local-hf-1")).thenReturn(true);
        when(repositoryConfigService.existsByRepoKey("local-hf-2")).thenReturn(true);
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        doThrow(new InvalidOperationException("Old layout not allowed")).when(virtualRepoPackageValidator)
                .validate(any());
        assertThrows(InvalidOperationException.class,
                () -> helper.handleVirtual(configModel, configDescriptor, repositoryService, false));
    }

    @Test
    void testHandleRemoteWithFedAttrOptIn_shouldPopulateFederationMembers() throws RepoConfigException {
        setupRemoteRepoMocks();
        RemoteRepositoryConfigModel model = buildRemoteFederatedModel("my-remote-fed",
                "https://remote1.example.com/artifactory/my-remote-fed",
                "https://remote2.example.com/artifactory/my-remote-fed");

        RepoConfig result = helper.handleRemote(model, new CentralConfigDescriptorImpl(), true);

        assertNotNull(result);
        assertTrue(result.getBaseConfig().isFederated());
        assertEquals(2, result.getBaseConfig().getFederationConfig().getMembers().size());
        assertEquals("https://remote1.example.com/artifactory/my-remote-fed",
                result.getBaseConfig().getFederationConfig().getMembers().get(0).getUrlOrTemplate());
    }

    @Test
    void testHandleRemoteWithFedAttrOptInFalse_shouldNotPopulateFederationMembers() throws RepoConfigException {
        setupRemoteRepoMocks();
        RemoteRepositoryConfigModel model = buildRemoteFederatedModel("my-remote-fed",
                "https://remote1.example.com/artifactory/my-remote-fed");

        RepoConfig result = helper.handleRemote(model, new CentralConfigDescriptorImpl(), false);

        assertNotNull(result);
        assertFalse(result.getBaseConfig().isFederated());
    }

    private void setupRemoteRepoMocks() throws RepoConfigException {
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        EdgeSmartRepoAddon edgeSmartRepoAddon = mock(EdgeSmartRepoAddon.class);
        when(edgeSmartRepoAddon.getUpstreamRepoKind(any())).thenReturn(SMART);
        when(addonsManager.addonByType(EdgeSmartRepoAddon.class)).thenReturn(edgeSmartRepoAddon);
        doNothing().when(remoteRepoPackageValidator)
                .validate(any(RemoteRepoConfig.class), any(MutableCentralConfigDescriptor.class),
                        any(RemoteRepositoryConfigModel.class));
    }

    private RemoteRepositoryConfigModel buildRemoteFederatedModel(String repoKey, String... mirrorUrls) {
        RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey(repoKey);
        model.setGeneral(general);
        model.setBasic(new RemoteBasicRepositoryConfigModel());
        model.setAdvanced(new RemoteAdvancedRepositoryConfigModel());
        model.setTypeSpecific(new NpmTypeSpecificConfigModel());
        FederationConfigModel federationConfigModel = new FederationConfigModel();
        List<MirrorMemberConfigModel> mirrors = new ArrayList<>();
        for (String url : mirrorUrls) {
            MirrorMemberConfigModel mirror = new MirrorMemberConfigModel();
            mirror.setUrl(url);
            mirror.setEnabled(true);
            mirrors.add(mirror);
        }
        federationConfigModel.setMirrors(mirrors);
        model.setFederation(federationConfigModel);
        return model;
    }

    @Test
    void testHandleVirtualWithFedAttrOptIn_shouldPopulateFederationMembers() throws RepoConfigException {
        VirtualRepositoryConfigModel model = new VirtualRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("my-virtual-fed");
        model.setGeneral(general);
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        basic.setSelectedRepositories(List.of());
        model.setBasic(basic);
        model.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        model.setTypeSpecific(new NpmTypeSpecificConfigModel());

        MirrorMemberConfigModel mirror1 = new MirrorMemberConfigModel();
        mirror1.setUrl("https://remote1.example.com/artifactory/my-virtual-fed");
        mirror1.setEnabled(true);
        MirrorMemberConfigModel mirror2 = new MirrorMemberConfigModel();
        mirror2.setUrl("https://remote2.example.com/artifactory/my-virtual-fed");
        mirror2.setEnabled(true);
        FederationConfigModel federationConfigModel = new FederationConfigModel();
        federationConfigModel.setMirrors(List.of(mirror1, mirror2));
        model.setFederation(federationConfigModel);

        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();

        RepoConfig result = helper.handleVirtual(model, configDescriptor, repositoryService, true);

        assertNotNull(result);
        assertTrue(result.getBaseConfig().isFederated());
        assertEquals(2, result.getBaseConfig().getFederationConfig().getMembers().size());
        assertEquals("https://remote1.example.com/artifactory/my-virtual-fed",
                result.getBaseConfig().getFederationConfig().getMembers().get(0).getUrlOrTemplate());
    }

    @Test
    void testHandleVirtualWithFedAttrOptInFalse_shouldNotPopulateFederationMembers() throws RepoConfigException {
        VirtualRepositoryConfigModel model = new VirtualRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("my-virtual-fed");
        model.setGeneral(general);
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        basic.setSelectedRepositories(List.of());
        model.setBasic(basic);
        model.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        model.setTypeSpecific(new NpmTypeSpecificConfigModel());

        MirrorMemberConfigModel mirror = new MirrorMemberConfigModel();
        mirror.setUrl("https://remote1.example.com/artifactory/my-virtual-fed");
        mirror.setEnabled(true);
        FederationConfigModel federationConfigModel = new FederationConfigModel();
        federationConfigModel.setMirrors(List.of(mirror));
        model.setFederation(federationConfigModel);

        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();

        RepoConfig result = helper.handleVirtual(model, configDescriptor, repositoryService, false);

        assertNotNull(result);
        assertFalse(result.getBaseConfig().isFederated());
    }
}
