package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.apache.commons.lang3.builder.DiffResult;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.smartrepo.EdgeSmartRepoAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.UpstreamRepoKind;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.NugetGeneralConfig;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.exception.InvalidOperationException;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.validator.virtual.VirtualRepoPackageValidator;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GenericTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NugetTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualSelectedRepository;
import org.artifactory.ui.rest.service.admin.configuration.repositories.replication.ReplicationConfigService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RemoteRepoPackageValidator;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.artifactory.util.DoesNotExistException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author prasannasudharsanr
 */
@ExtendWith(MockitoExtension.class)
class UpdateRepoConfigHelperTest {

    @InjectMocks
    private UpdateRepoConfigHelper updateRepoConfigHelper;

    @Mock
    private ProjectsService projectsService;

    @Mock
    private CentralConfigService configService;

    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ArtifactoryContext artifactoryContext;

    @Mock
    private ArtifactoryHome artifactoryHome;

    @Mock
    private RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor;

    @Mock
    private NugetGeneralConfig nugetGeneralConfig;

    @Mock
    private CentralConfigDescriptorImpl centralConfigDescriptor;

    @Mock
    private RepoConfigValidator repoValidator;

    @Mock
    private AddonsManager addonsManager;

    @Mock
    private RemoteRepoPackageValidator remoteRepoPackageValidator;

    @Mock
    private ReplicationConfigService replicationConfigService;

    @Mock
    private VirtualRepoPackageValidator virtualValidationService;

    @BeforeEach
    void setUp() {
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        ArtifactoryHome.bind(artifactoryHome);
        RepoConfigDescriptorBuilder repoBuilder = new RepoConfigDescriptorBuilder();
        repoBuilder.setCentralConfig(configService);
        repoBuilder.setRepositoryConfigService(repositoryConfigService);
        ReflectionTestUtils.setField(updateRepoConfigHelper, "validator", repoValidator);
        ReflectionTestUtils.setField(updateRepoConfigHelper, "builder", repoBuilder);

        lenient().when(projectsService.getProjectKeyOfRepository("repo")).thenReturn("project");
        lenient().when(projectsService.getProjectRepositoriesKeys(eq("project"), anyBoolean()))
                .thenReturn(Set.of("local-repo"));
        lenient().when(configService.getMutableDescriptor()).thenReturn(new CentralConfigDescriptorImpl());
    }

    // To test the below scenario
    // globalNormalisation flag is false and virtual repo normalisation flag is false
    // Expected: Throw exception when any of the child repo is normalised
    @Test
    void testUpdateRepoConfigForNugetPackageTypeValidationWhenNormalizationFlagIsNotEnabled() {

        when(configService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getRepositoriesGeneralConfig()).thenReturn(repositoriesGeneralConfigDescriptor);
        when(repositoriesGeneralConfigDescriptor.getNuget()).thenReturn(nugetGeneralConfig);
        when(nugetGeneralConfig.isEnableGlobalNormalization()).thenReturn(false);

        LocalRepoConfig repoConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        repoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");
        NugetTypeSpecificConfigModel typeSpecificConfigModel = new NugetTypeSpecificConfigModel();
        VirtualRepositoryConfigModel repositoryConfigModel =
                buildVirtualRepoConfig(typeSpecificConfigModel, repoConfig);
        when(repositoryConfigService.getRepoConfigByKey("local-repo")).thenReturn(repoConfig);
        when(repositoryConfigService.existsByRepoKey("local-repo")).thenReturn(true);

        InvalidOperationException invalidOperationException = assertThrows(InvalidOperationException.class,
                () -> updateRepoConfigHelper.handleVirtual(repositoryConfigModel, false));
        assertEquals("Nuget Repository repo must have only non-normalized repositories",
                invalidOperationException.getMessage());
    }

    // To test the below scenario
    // globalNormalisation flag is true and virtual repo normalisation flag is true
    // Expected: No exception thrown when all the child repos are normalised and update happens as expected
    @Test
    void testUpdateRepoConfigForNugetPackageTypeValidationWhenNormalizationFlagIsEnabled() throws RepoConfigException {

        when(configService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getRepositoriesGeneralConfig()).thenReturn(repositoriesGeneralConfigDescriptor);
        when(repositoriesGeneralConfigDescriptor.getNuget()).thenReturn(nugetGeneralConfig);
        when(nugetGeneralConfig.isEnableGlobalNormalization()).thenReturn(false);

        mockDiffResultOnUpdate();
        NugetTypeSpecificConfigModel typeSpecificConfigModel = new NugetTypeSpecificConfigModel();
        typeSpecificConfigModel.setEnableNormalizedVersion(true);
        LocalRepoConfig repoConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        repoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");

        VirtualRepositoryConfigModel repositoryConfigModel =
                buildVirtualRepoConfig(typeSpecificConfigModel, repoConfig);
        when(repositoryConfigService.getRepoConfigByKey("local-repo")).thenReturn(repoConfig);
        when(repositoryConfigService.existsByRepoKey("local-repo")).thenReturn(true);
        updateRepoConfigHelper.handleVirtual(repositoryConfigModel, false);

        verify(repositoryConfigService).getRepoConfigByKey("local-repo");
    }

    // To test the below scenario
    // globalNormalisation flag is true and virtual repo normalisation flag is true
    // Expected: Exception thrown when any of the child repos are non-normalised
    @Test
    void testUpdateRepoConfigForNugetPackageTypeValidationWhenNormalizationFlagIsDisabledAtSelectedRepos() {

        when(configService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getRepositoriesGeneralConfig()).thenReturn(repositoriesGeneralConfigDescriptor);
        when(repositoriesGeneralConfigDescriptor.getNuget()).thenReturn(nugetGeneralConfig);
        when(nugetGeneralConfig.isEnableGlobalNormalization()).thenReturn(false);

        LocalRepoConfig repoConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        NugetTypeSpecificConfigModel typeSpecificConfigModel = new NugetTypeSpecificConfigModel();
        typeSpecificConfigModel.setEnableNormalizedVersion(true);
        VirtualRepositoryConfigModel repositoryConfigModel =
                buildVirtualRepoConfig(typeSpecificConfigModel, repoConfig);
        when(repositoryConfigService.getRepoConfigByKey("local-repo")).thenReturn(repoConfig);
        when(repositoryConfigService.existsByRepoKey("local-repo")).thenReturn(true);

        InvalidOperationException invalidOperationException = assertThrows(InvalidOperationException.class,
                () -> updateRepoConfigHelper.handleVirtual(repositoryConfigModel, false));
        assertEquals("Nuget Repository repo must have only normalized repositories",
                invalidOperationException.getMessage());
        verify(repositoryConfigService).getRepoConfigByKey("local-repo");
    }

    @Test
    void testUpdateRepoConfigForNugetPackageTypeValidationWhenNormalizationFlagIsDisabled() {

        when(configService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getRepositoriesGeneralConfig()).thenReturn(repositoriesGeneralConfigDescriptor);
        when(repositoriesGeneralConfigDescriptor.getNuget()).thenReturn(nugetGeneralConfig);
        when(nugetGeneralConfig.isEnableGlobalNormalization()).thenReturn(false);

        LocalRepoConfig repoConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        NugetTypeSpecificConfigModel typeSpecificConfigModel = new NugetTypeSpecificConfigModel();
        typeSpecificConfigModel.setEnableNormalizedVersion(true);
        VirtualRepositoryConfigModel repositoryConfigModel =
                buildVirtualRepoConfig(typeSpecificConfigModel, repoConfig);
        when(repositoryConfigService.existsByRepoKey("local-repo")).thenReturn(true);

        DoesNotExistException doesNotExistException = assertThrows(DoesNotExistException.class,
                () -> updateRepoConfigHelper.handleVirtual(repositoryConfigModel, false));
        assertEquals("Selected Repo keylocal-repo not found", doesNotExistException.getMessage());
        verify(repositoryConfigService).getRepoConfigByKey("local-repo");
    }

    @Test
    void testHandleRemoteWhenReplicationIsRemoved() throws RepoConfigException {
        // Setup
        RemoteRepositoryConfigModel model = mock(RemoteRepositoryConfigModel.class);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        MutableCentralConfigDescriptor configDescriptor = mock(MutableCentralConfigDescriptor.class);
        ReverseProxyDescriptor reverseProxyDescriptor = mock(ReverseProxyDescriptor.class);
        EdgeSmartRepoAddon edgeSmartRepoAddon = mock(EdgeSmartRepoAddon.class);

        // Mock behavior
        when(configService.getMutableDescriptor()).thenReturn(configDescriptor);
        when(model.toRepoConfig(any(), any(), any())).thenReturn(remoteRepoConfig);
        when(model.getReverseProxyDescriptor(any(), any())).thenReturn(reverseProxyDescriptor);
        when(model.getReplicationDescriptor(any(), any())).thenReturn(null); // Replication is null (removed)
        when(remoteRepoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(remoteRepoConfig.getKey()).thenReturn("test-remote-repo");

        // Mock addon manager for smart repo validation
        when(addonsManager.addonByType(EdgeSmartRepoAddon.class)).thenReturn(edgeSmartRepoAddon);
        when(edgeSmartRepoAddon.getUpstreamRepoKind(any())).thenReturn(UpstreamRepoKind.SMART);

        when(remoteRepoPackageValidator.getRemoteRepoUpdateValidationEnabledPackages())
                .thenReturn(List.of("Generic"));

        // Mock diff result to simulate changes
        DiffResult diffResult = mock(DiffResult.class);
        when(diffResult.getNumberOfDiffs()).thenReturn(1);
        when(configService.findDiff(any(), any())).thenReturn(diffResult);

        // Execute
        updateRepoConfigHelper.handleRemote(model, false);

        // Verify
        verify(replicationConfigService).removeRemoteReplication(remoteRepoConfig, configDescriptor);
        verify(configService).saveEditedDescriptorAndReload(configDescriptor, reverseProxyDescriptor, null);
        verify(repositoryConfigService).upsertRepoConfig(remoteRepoConfig);
    }

    private VirtualRepositoryConfigModel buildVirtualRepoConfig(
            NugetTypeSpecificConfigModel typeSpecificConfigModel, LocalRepoConfig repoConfig) {
        VirtualRepositoryConfigModel configModel = new VirtualRepositoryConfigModel();
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        configModel.setTypeSpecific(typeSpecificConfigModel);
        basic.setSelectedRepositories(List.of(new VirtualSelectedRepository(repoConfig)));
        basic.setResolvedRepositories(List.of(new VirtualSelectedRepository(repoConfig)));
        configModel.setBasic(basic);
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("repo");
        general.setProjectKey("project");
        general.setEnvironments(Set.of("PROD"));
        configModel.setGeneral(general);
        configModel.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        return configModel;
    }

    private void mockDiffResultOnUpdate() {
        DiffResult mockedDiffResult = mock(DiffResult.class);
        when(mockedDiffResult.getNumberOfDiffs()).thenReturn(0);
        when(configService.findDiff(any(), any())).thenReturn(mockedDiffResult);
    }

    @Test
    void testAssertVirtualRepositoriesAreInProjectThrowsExceptionWithForceUpdate() throws RepoConfigException {
        VirtualRepositoryConfigModel configModel = createVirtualRepoConfigModel();
        mockDiffResultOnUpdate();
        updateRepoConfigHelper.handleVirtual(configModel, false);
        verify(projectsService, times(0)).getProjectRepositoriesKeys("project", false);
    }

    private VirtualRepositoryConfigModel createVirtualRepoConfigModel() {
        VirtualRepositoryConfigModel configModel = new VirtualRepositoryConfigModel();
        GeneralRepositoryConfigModel generalRepositoryConfigModel = new GeneralRepositoryConfigModel();
        generalRepositoryConfigModel.setRepoKey("repo");
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        VirtualSelectedRepository virtualSelectedRepository = new VirtualSelectedRepository();
        virtualSelectedRepository.setRepoName("repo");
        basic.setResolvedRepositories(List.of(virtualSelectedRepository));
        basic.setSelectedRepositories(List.of(virtualSelectedRepository));
        configModel.setBasic(basic);
        configModel.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        configModel.setTypeSpecific(new GenericTypeSpecificConfigModel());
        configModel.setGeneral(generalRepositoryConfigModel);
        return configModel;
    }

    @Test
    void testUpdateVirtualRepoWithFailsWhenValidatorFails() {
        VirtualRepositoryConfigModel configModel = createVirtualRepoConfigModel();
        doThrow(new InvalidOperationException("Validation Failed")).when(virtualValidationService).validate(any());
        assertThrows(InvalidOperationException.class, () -> updateRepoConfigHelper.handleVirtual(configModel, false));
    }
}