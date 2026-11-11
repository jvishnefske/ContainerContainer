package org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder;

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.p2.P2Repo;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.ProxyRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.CdnRedirectRepoConfig;
import org.artifactory.repo.config.RepoConfigDefaultValues;
import org.artifactory.repo.packagetype.DockerApiVersion;
import org.artifactory.repo.packagetype.PomCleanupPolicy;
import org.artifactory.repo.virtual.VirtualCacheConfig;
import org.artifactory.repo.virtual.VirtualRepositoryConfigResolver;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteCacheRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AIEditorExtensionsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AgentPluginsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AgentPackagesTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AlpineTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AnsibleTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.BazelModulesTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CargoTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CocoaPodsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.ConanTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CondaTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.DockerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GoTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HelmTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HexTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.JetBrainsPluginsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.MavenTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NixTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NugetTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SkillsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TerraformBackendTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TerraformTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualSelectedRepository;
import org.artifactory.util.PackageTypeConfigFields;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_AI_EDITOR_EXTENSIONS_EXTERNAL_DEPENDENCIES_PATTERNS;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_METADATA_NAME_VERSION;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_NON_DUPLICATE_CHART;
import static org.artifactory.util.PackageTypeConfigFields.AUTH_URL;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_NUGET_NORMALIZED_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_CONAN_AUTHENTICATION;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_MAVEN_AUTHENTICATION;
import static org.artifactory.util.PackageTypeConfigFields.POM_REPOSITORY_REFERENCES_CLEANUP_POLICY;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Author: Ivan Perepelytsya
 * Date: 6/2/20.
 */
class RepoConfigModelBuilderTest {

    private CentralConfigService centralConfigService;
    private RepoConfigModelBuilder repoConfigModelBuilder;
    private ProjectsService projectsService;

    @BeforeEach
    void bindArtifactoryHome() {
        InternalArtifactoryContext context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        centralConfigService = mock(CentralConfigService.class);
        projectsService = mock(ProjectsService.class);
        repoConfigModelBuilder = getRepoConfigModelBuilder();
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
        final ArtifactorySystemProperties artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        final ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getProperty(any())).thenReturn("");
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    void unbindArtifactoryHome() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testPopulateLocalDescriptorCdn() {
        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final LocalConfig localRepoDescriptor = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoDescriptor.getRepoTypeConfig().setCdnRedirectRepoConfig(getEnabledCdnConfig());

        final LocalRepositoryConfigModel localRepositoryConfigModel =
                new LocalRepositoryConfigModel();
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(localRepoDescriptor,
                localRepositoryConfigModel);

        assertNotNull(localRepositoryConfigModel.getAdvanced().getCdnRedirectRepoConfig());
        assertTrue(localRepositoryConfigModel.getAdvanced().getCdnRedirectRepoConfig().isEnabled());
    }

    @Test
    void testPopulateLocalDescriptorReleaseBundleV2() {
        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final LocalConfig localRepoDescriptor = LocalRepoConfig.create(DEFAULT_RBV2_REPO, RepoType.ReleaseBundles);
        localRepoDescriptor.getRepoTypeConfig().setCdnRedirectRepoConfig(getEnabledCdnConfig());

        final LocalRepositoryConfigModel localRepositoryConfigModel =
                new LocalRepositoryConfigModel();
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(localRepoDescriptor,
                localRepositoryConfigModel);

        assertNull(localRepositoryConfigModel.getAdvanced().getCdnRedirectRepoConfig());
        assertFalse(localRepositoryConfigModel.getAdvanced().getPriorityResolution());
    }

    @Test
    void testPopulateRemoteDescriptorCdn() {
        final RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setCdnRedirectRepoConfig(getEnabledCdnConfig());

        final RemoteRepositoryConfigModel remoteRepositoryConfigModel = new RemoteRepositoryConfigModel();
        repoConfigModelBuilder
                .populateRemoteRepositoryConfigValuesToModel(remoteRepoConfig, remoteRepositoryConfigModel);

        assertNotNull(remoteRepositoryConfigModel.getAdvanced().getCdnRedirectRepoConfig());
        assertTrue(remoteRepositoryConfigModel.getAdvanced().getCdnRedirectRepoConfig().isEnabled());
    }

    @Test
    void testPopulateRemoteDescriptorForNugetPackageType() {
        final RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.NuGet);
        remoteRepoConfig.getPackageTypeConfig().put(ENABLE_NUGET_NORMALIZED_VERSION.getKey(), "true");

        final RemoteRepositoryConfigModel remoteRepositoryConfigModel = new RemoteRepositoryConfigModel();
        repoConfigModelBuilder
                .populateRemoteRepositoryConfigValuesToModel(remoteRepoConfig, remoteRepositoryConfigModel);

        TypeSpecificConfigModel typeSpecific = remoteRepositoryConfigModel.getTypeSpecific();
        assertInstanceOf(NugetTypeSpecificConfigModel.class, typeSpecific);
        assertTrue(((NugetTypeSpecificConfigModel) typeSpecific).isEnableNormalizedVersion());
    }

    @Test
    void testPopulateLocalDescriptorNullCdn() {
        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final LocalConfig localConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localConfig.getRepoTypeConfig().setCdnRedirectRepoConfig(null);

        final LocalRepositoryConfigModel localRepositoryConfigModel =
                new LocalRepositoryConfigModel();
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(localConfig,
                localRepositoryConfigModel);
        assertNull(localRepositoryConfigModel.getAdvanced().getCdnRedirectRepoConfig());
    }


    @Test
    void testPopulateRemoteDescriptorNullCdn() {
        final RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setCdnRedirectRepoConfig(null);

        final RemoteRepositoryConfigModel remoteRepositoryConfigModel = new RemoteRepositoryConfigModel();
        repoConfigModelBuilder
                .populateRemoteRepositoryConfigValuesToModel(remoteRepoConfig, remoteRepositoryConfigModel);

        assertNull(remoteRepositoryConfigModel.getAdvanced().getCdnRedirectRepoConfig());
    }

    /**
     * Tests repository stored RetrievalCachePeriod, UI gets this for the Advanced -> Cache section
     * and displays this on RetrievalCachePeriod field.
     */
    @ParameterizedTest
    @EnumSource(value = RepoType.class, names = {"Docker", "OCI", "HelmOCI"})
    void testPopulateRemoteRepositoryDockerConfigValuesToModel(RepoType repoType) {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setRetrievalCachePeriodSecs(500L);

        DockerTypeSpecificConfigModel remoteTypeSpecific = (DockerTypeSpecificConfigModel) repoConfigModelBuilder
                .createRemoteTypeSpecific(repoType, remoteRepoConfig);

        assertEquals(500L, (long) remoteTypeSpecific.getRetrievalCachePeriodSecs());
        assertEquals(StringUtils.EMPTY, remoteTypeSpecific.getDockerProjectId());
        assertEquals(repoType, remoteTypeSpecific.getRepoType());
        assertFalse(remoteTypeSpecific.getListRemoteFolderItems());
    }

    @Test
    void testPopulateRemoteRepositoryHelmConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Helm);

        HelmTypeSpecificConfigModel remoteTypeSpecific = (HelmTypeSpecificConfigModel) repoConfigModelBuilder
                .createRemoteTypeSpecific(RepoType.Helm, remoteRepoConfig);

        assertEquals(RepoType.Helm, remoteTypeSpecific.getRepoType());
        assertFalse(remoteTypeSpecific.isListRemoteFolderItems());
    }

    @Test
    void testPopulateRemoteRepositoryAgentPluginsConfigValuesToModel() {
        RepoConfigModelBuilder repoConfigModelBuilder = new RepoConfigModelBuilder();
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.AgentPlugins);

        AgentPluginsTypeSpecificConfigModel remoteTypeSpecific =
                (AgentPluginsTypeSpecificConfigModel) repoConfigModelBuilder
                                                              .createRemoteTypeSpecific(RepoType.AgentPlugins,
                                                                                        remoteRepoConfig);

        assertEquals(RepoType.AgentPlugins, remoteTypeSpecific.getRepoType());
    }

    @Test
    void testPopulateRemoteRepositoryVSCodeConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.AIEditorExtensions);

        AIEditorExtensionsTypeSpecificConfigModel remoteTypeSpecific =
                (AIEditorExtensionsTypeSpecificConfigModel) repoConfigModelBuilder
                        .createRemoteTypeSpecific(RepoType.AIEditorExtensions, remoteRepoConfig);

        assertEquals(RepoType.AIEditorExtensions, remoteTypeSpecific.getRepoType());
        assertFalse(remoteTypeSpecific.isListRemoteFolderItems());
        assertTrue(remoteTypeSpecific.getEnableExternalDependencies());
        assertEquals(List.of(DEFAULT_AI_EDITOR_EXTENSIONS_EXTERNAL_DEPENDENCIES_PATTERNS).toString(),
                remoteTypeSpecific.getExternalPatterns().toString());
    }

    @Test
    void testPopulateRemoteRepositoryJetBrainsConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.JetBrainsPlugins);

        JetBrainsPluginsTypeSpecificConfigModel remoteTypeSpecific =
                (JetBrainsPluginsTypeSpecificConfigModel) repoConfigModelBuilder.createRemoteTypeSpecific(
                        RepoType.JetBrainsPlugins, remoteRepoConfig);

        assertEquals(RepoType.JetBrainsPlugins, remoteTypeSpecific.getRepoType());
    }

    @Test
    void testPopulateRemoteRepositoryBazelModulesConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.BazelModules);

        BazelModulesTypeSpecificConfigModel remoteTypeSpecific =
                (BazelModulesTypeSpecificConfigModel) repoConfigModelBuilder
                        .createRemoteTypeSpecific(RepoType.BazelModules, remoteRepoConfig);

        assertEquals(RepoType.BazelModules, remoteTypeSpecific.getRepoType());
    }

    @Test
    void testPopulateRemoteRepositoryNixConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Nix);

        NixTypeSpecificConfigModel remoteTypeSpecific = (NixTypeSpecificConfigModel) repoConfigModelBuilder
                .createRemoteTypeSpecific(RepoType.Nix, remoteRepoConfig);

        assertEquals(RepoType.Nix, remoteTypeSpecific.getRepoType());
    }

    @Test
    void testPopulateLocalRepositoryNixConfigValuesToModel() {
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());
        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Nix);

        final LocalRepositoryConfigModel localRepositoryConfigModel = new LocalRepositoryConfigModel();
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(localRepoConfig, localRepositoryConfigModel);

        assertInstanceOf(NixTypeSpecificConfigModel.class, localRepositoryConfigModel.getTypeSpecific());
        assertEquals(RepoType.Nix, localRepositoryConfigModel.getTypeSpecific().getRepoType());
    }

    @Test
    void testPopulateVirtualRepositoryNixConfigValuesToModel() {
        VirtualRepositoryConfigResolver virtualRepositoryConfigResolver = mock(VirtualRepositoryConfigResolver.class);
        when(virtualRepositoryConfigResolver.getOrderedRepos("repoKey")).thenReturn(List.of());
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());
        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Nix);

        final VirtualRepositoryConfigModel virtualRepositoryConfigModel = new VirtualRepositoryConfigModel();
        repoConfigModelBuilder.setResolver(virtualRepositoryConfigResolver);
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.setProjectsService(projectsService);
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig,
                virtualRepositoryConfigModel);

        assertInstanceOf(NixTypeSpecificConfigModel.class, virtualRepositoryConfigModel.getTypeSpecific());
        assertEquals(RepoType.Nix, virtualRepositoryConfigModel.getTypeSpecific().getRepoType());
    }

    @Test
    void testPopulateRemoteRepositoryLuaRockConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.LuaRocks);

        TypeSpecificConfigModel remoteTypeSpecific = repoConfigModelBuilder
                .createRemoteTypeSpecific(RepoType.LuaRocks, remoteRepoConfig);

        assertEquals(RepoType.LuaRocks, remoteTypeSpecific.getRepoType());
        assertEquals(RepoConfigDefaultValues.DefaultUrl.LUAROCKS.getUrl(), remoteTypeSpecific.getUrl());
    }

    @Test
    void testPopulateLocalRepositorySkillsConfigValuesToModel() {
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());
        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final LocalRepoConfig localRepoConfig =
                LocalRepoConfig.create("repoKey", RepoType.Skills);

        final LocalRepositoryConfigModel localRepositoryConfigModel =
                new LocalRepositoryConfigModel();
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(
                localRepoConfig, localRepositoryConfigModel);

        assertInstanceOf(SkillsTypeSpecificConfigModel.class,
                localRepositoryConfigModel.getTypeSpecific());
        assertEquals(RepoType.Skills,
                localRepositoryConfigModel.getTypeSpecific().getRepoType());
    }

    @Test
    void testPopulateLocalRepositoryAgentPluginsConfigValuesToModel() {
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());
        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final LocalRepoConfig localRepoConfig =
                LocalRepoConfig.create("repoKey", RepoType.AgentPlugins);

        final LocalRepositoryConfigModel localRepositoryConfigModel =
                new LocalRepositoryConfigModel();
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(
                localRepoConfig, localRepositoryConfigModel);

        assertInstanceOf(AgentPluginsTypeSpecificConfigModel.class,
                localRepositoryConfigModel.getTypeSpecific());
        assertEquals(RepoType.AgentPlugins,
                localRepositoryConfigModel.getTypeSpecific().getRepoType());
    }

    @Test
    void testPopulateLocalRepositoryAgentPackagesConfigValuesToModel() {
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());
        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final LocalRepoConfig localRepoConfig =
                LocalRepoConfig.create("repoKey", RepoType.AgentPackages);

        final LocalRepositoryConfigModel localRepositoryConfigModel =
                new LocalRepositoryConfigModel();
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(
                localRepoConfig, localRepositoryConfigModel);

        assertInstanceOf(AgentPackagesTypeSpecificConfigModel.class,
                localRepositoryConfigModel.getTypeSpecific());
        assertEquals(RepoType.AgentPackages,
                localRepositoryConfigModel.getTypeSpecific().getRepoType());
    }

    @Test
    void testPopulateVirtualRepositoryNixConfigWithVirtualRetrievalCachePeriod() {
        // Given
        Long expectedCachePeriod = 7200L;
        VirtualRepositoryConfigResolver virtualRepositoryConfigResolver = mock(VirtualRepositoryConfigResolver.class);
        when(virtualRepositoryConfigResolver.getOrderedRepos("repoKey")).thenReturn(List.of());
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());
        final RepoConfigModelBuilder repoConfigModelBuilder = new RepoConfigModelBuilder();
        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Nix);
        // Set up VirtualCacheConfig with a custom virtualRetrievalCachePeriodSecs
        VirtualCacheConfig cacheConfig = new VirtualCacheConfig();
        cacheConfig.setVirtualRetrievalCachePeriodSecs(expectedCachePeriod);
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(cacheConfig);

        final VirtualRepositoryConfigModel virtualRepositoryConfigModel = new VirtualRepositoryConfigModel();
        repoConfigModelBuilder.setResolver(virtualRepositoryConfigResolver);
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.setProjectsService(projectsService);

        // When
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig,
                virtualRepositoryConfigModel);

        // Then
        assertInstanceOf(NixTypeSpecificConfigModel.class, virtualRepositoryConfigModel.getTypeSpecific());
        NixTypeSpecificConfigModel nixTypeSpecific =
                (NixTypeSpecificConfigModel) virtualRepositoryConfigModel.getTypeSpecific();
        assertEquals(expectedCachePeriod, nixTypeSpecific.getVirtualRetrievalCachePeriodSecs());
    }

    @Test
    void testPopulateRemoteRepositoryTerraformConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Terraform);

        TerraformTypeSpecificConfigModel remoteTypeSpecific = (TerraformTypeSpecificConfigModel) repoConfigModelBuilder
                .createRemoteTypeSpecific(RepoType.Terraform, remoteRepoConfig);

        assertEquals(RepoType.Terraform, remoteTypeSpecific.getRepoType());
        assertFalse(remoteTypeSpecific.isListRemoteFolderItems());
        assertFalse(remoteTypeSpecific.getEnableExternalDependencies());
    }

    @Test
    void testPopulateLocalRepositoryHelmConfigValuesToModel() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Helm);
        localRepoConfig.getPackageTypeConfig().put(FORCE_METADATA_NAME_VERSION, "true");
        localRepoConfig.getPackageTypeConfig().put(FORCE_NON_DUPLICATE_CHART, "true");
        final LocalRepositoryConfigModel localRepositoryConfigModel =
                new LocalRepositoryConfigModel();
        repoConfigModelBuilder.setCentralConfig(centralConfigService);
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(localRepoConfig, localRepositoryConfigModel);
        assertEquals(RepoType.Helm, localRepositoryConfigModel.getTypeSpecific().getRepoType());
        assertInstanceOf(HelmTypeSpecificConfigModel.class, localRepositoryConfigModel.getTypeSpecific());
        assertTrue(((HelmTypeSpecificConfigModel) localRepositoryConfigModel.getTypeSpecific())
                .isForceMetadataNameVersion());
        assertTrue(((HelmTypeSpecificConfigModel) localRepositoryConfigModel.getTypeSpecific())
                .isForceNonDuplicateChart());
    }

    @Test
    void testPopulateRemoteRepositoryHexConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Hex);

        HexTypeSpecificConfigModel remoteTypeSpecific = (HexTypeSpecificConfigModel) repoConfigModelBuilder
                .createRemoteTypeSpecific(RepoType.Hex, remoteRepoConfig);

        assertEquals(RepoType.Hex, remoteTypeSpecific.getRepoType());
        assertEquals(PackageTypeConfigFields.HEX_REGISTRY_URL.getDefaultValue(), remoteTypeSpecific.getUrl());
        assertFalse(remoteTypeSpecific.getUrl().isEmpty());
        assertEquals(PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY.getDefaultValue(),
                remoteTypeSpecific.getHexPublicKey());
    }

    @Test
    void testUpdateHexPubKeyRemoteRepositoryHexConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Hex);
        remoteRepoConfig.getPackageTypeConfig().put("hexPublicKey", "newKey");

        HexTypeSpecificConfigModel remoteTypeSpecific = (HexTypeSpecificConfigModel) repoConfigModelBuilder
                .createRemoteTypeSpecific(RepoType.Hex, remoteRepoConfig);


        assertEquals(RepoType.Hex, remoteTypeSpecific.getRepoType());
        assertEquals(PackageTypeConfigFields.HEX_REGISTRY_URL.getDefaultValue(), remoteTypeSpecific.getUrl());
        assertFalse(remoteTypeSpecific.getUrl().isEmpty());
        assertEquals("newKey", remoteTypeSpecific.getHexPublicKey());
    }

    @Test
    void testPopulateRemoteRepositoryGoConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setMissedRetrievalCachePeriodSecs(300L);

        GoTypeSpecificConfigModel remoteTypeSpecific = (GoTypeSpecificConfigModel) repoConfigModelBuilder
                .createRemoteTypeSpecific(RepoType.Go, remoteRepoConfig);

        assertEquals(300L, (long) remoteTypeSpecific.getMissedRetrievalCachePeriodSecs());
    }

    @Test
    void testPopulateVirtualRepositoryCocoaPodConfigValuesToModel() {
        RepositoryConfigService repositoryConfigService = Mockito.mock(RepositoryConfigService.class);
        CentralConfigService centralConfig = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        VirtualRepositoryConfigResolver virtualRepositoryConfigResolver =
                Mockito.mock(VirtualRepositoryConfigResolver.class);
        repoConfigModelBuilder.setResolver(virtualRepositoryConfigResolver);
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.setCentralConfig(centralConfig);
        Mockito.when(virtualRepositoryConfigResolver.getOrderedRepos(anyString()))
                .thenReturn(new ArrayList<>());
        Mockito.when(repositoryConfigService.getRepoConfigsByKeys(new HashSet<>()))
                .thenReturn(new HashSet<>());
        Mockito.when(centralConfig.getDescriptor()).thenReturn(configDescriptor);
        Mockito.when(configDescriptor.getCurrentReverseProxy()).thenReturn(null);

        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.CocoaPods);
        VirtualRepositoryConfigModel model = new VirtualRepositoryConfigModel();
        repoConfigModelBuilder.setProjectsService(projectsService);

        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig, model);
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig, model);
        assertTrue(model.getBasic().getIneligibleRepositories().isEmpty());
        assertInstanceOf(CocoaPodsTypeSpecificConfigModel.class, model.getTypeSpecific());
        assertEquals(RepoType.CocoaPods, model.getTypeSpecific().getRepoType());

        CocoaPodsTypeSpecificConfigModel typeSpecific =
                (CocoaPodsTypeSpecificConfigModel) model.getTypeSpecific();

        assertFalse(typeSpecific.getEnableExternalDependencies());
        assertFalse(typeSpecific.getForceModifyIndex());
    }

    @Test
    void testPopulateRemoteRepositoryConanConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        ConanTypeSpecificConfigModel remoteTypeSpecific = (ConanTypeSpecificConfigModel) repoConfigModelBuilder
                .createRemoteTypeSpecific(RepoType.Conan, remoteRepoConfig);
        assertFalse(remoteTypeSpecific.getForceConanAuthentication());
        remoteRepoConfig.getPackageTypeConfig().put(FORCE_CONAN_AUTHENTICATION.getKey(), Boolean.TRUE.toString());
        remoteTypeSpecific = (ConanTypeSpecificConfigModel) repoConfigModelBuilder
                .createRemoteTypeSpecific(RepoType.Conan, remoteRepoConfig);
        assertTrue(remoteTypeSpecific.getForceConanAuthentication());
    }

    @Test
    void testPopulateRemoteRepositoryAlpineConfigValuesToModel() {
        // Arrange
        RepoConfigModelBuilder repoConfigModelBuilder = new RepoConfigModelBuilder();
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Alpine);

        // Act - default value
        AlpineTypeSpecificConfigModel remoteTypeSpecific =
                (AlpineTypeSpecificConfigModel) repoConfigModelBuilder
                        .createRemoteTypeSpecific(RepoType.Alpine, remoteRepoConfig);

        // Assert - default listRemoteFolderItems is false
        assertFalse(remoteTypeSpecific.isListRemoteFolderItems());
        assertEquals(RepoType.Alpine, remoteTypeSpecific.getRepoType());

        // Act - set listRemoteFolderItems to true on the descriptor
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(true);
        remoteTypeSpecific = (AlpineTypeSpecificConfigModel) repoConfigModelBuilder
                .createRemoteTypeSpecific(RepoType.Alpine, remoteRepoConfig);

        // Assert - listRemoteFolderItems is correctly read from the descriptor
        assertTrue(remoteTypeSpecific.isListRemoteFolderItems());
    }

    @Test
    void testPopulateRemoteRepositoryCondaConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Conda);

        // Test default listRemoteFolderItems value
        CondaTypeSpecificConfigModel remoteTypeSpecific =
                (CondaTypeSpecificConfigModel) repoConfigModelBuilder
                        .createRemoteTypeSpecific(RepoType.Conda, remoteRepoConfig);
        assertFalse(remoteTypeSpecific.getListRemoteFolderItems());
        assertEquals(RepoType.Conda, remoteTypeSpecific.getRepoType());

        // Test that listRemoteFolderItems is correctly populated from descriptor
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(true);
        remoteTypeSpecific = (CondaTypeSpecificConfigModel) repoConfigModelBuilder
                .createRemoteTypeSpecific(RepoType.Conda, remoteRepoConfig);
        assertTrue(remoteTypeSpecific.getListRemoteFolderItems());
    }

    @Test
    void testPopulateVirtualRepositoryWithForceAuthentication() {
        RepositoryConfigService repositoryConfigService = Mockito.mock(RepositoryConfigService.class);
        CentralConfigService centralConfig = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        VirtualRepositoryConfigResolver virtualRepositoryConfigResolver =
                Mockito.mock(VirtualRepositoryConfigResolver.class);
        repoConfigModelBuilder.setResolver(virtualRepositoryConfigResolver);
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.setCentralConfig(centralConfig);
        Mockito.when(virtualRepositoryConfigResolver.getOrderedRepos(anyString()))
                .thenReturn(new ArrayList<>());
        Mockito.when(repositoryConfigService.getRepoConfigsByKeys(new HashSet<>()))
                .thenReturn(new HashSet<>());
        Mockito.when(centralConfig.getDescriptor()).thenReturn(configDescriptor);
        Mockito.when(configDescriptor.getCurrentReverseProxy()).thenReturn(null);
        repoConfigModelBuilder.setProjectsService(projectsService);

        testPopulateVirtualRepositoryConanForceAuthentication(repoConfigModelBuilder);
        testPopulateVirtualRepositoryMavenForceAuthentication(repoConfigModelBuilder);
    }

    @ParameterizedTest
    @EnumSource(value = RepoType.class, names = {"Docker", "OCI", "HelmOCI"})
    void testPopulateVirtualRepositoryDocker(RepoType repoType) {
        RepositoryConfigService repositoryConfigService = Mockito.mock(RepositoryConfigService.class);
        CentralConfigService centralConfig = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        VirtualRepositoryConfigResolver virtualRepositoryConfigResolver =
                Mockito.mock(VirtualRepositoryConfigResolver.class);
        repoConfigModelBuilder.setResolver(virtualRepositoryConfigResolver);
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.setCentralConfig(centralConfig);
        Mockito.when(virtualRepositoryConfigResolver.getOrderedRepos(anyString()))
                .thenReturn(new ArrayList<>());
        Mockito.when(repositoryConfigService.getRepoConfigsByKeys(new HashSet<>()))
                .thenReturn(new HashSet<>());
        Mockito.when(centralConfig.getDescriptor()).thenReturn(configDescriptor);
        Mockito.when(configDescriptor.getCurrentReverseProxy()).thenReturn(null);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", repoType);
        VirtualRepositoryConfigModel model = new VirtualRepositoryConfigModel();
        repoConfigModelBuilder.setProjectsService(projectsService);
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig, model);
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig, model);
        assertInstanceOf(DockerTypeSpecificConfigModel.class, model.getTypeSpecific());
        assertEquals(repoType, model.getTypeSpecific().getRepoType());
        DockerTypeSpecificConfigModel typeSpecific =
                (DockerTypeSpecificConfigModel) model.getTypeSpecific();
        assertEquals(DockerApiVersion.V2, typeSpecific.getDockerApiVersion());
        assertEquals(0, typeSpecific.getMaxUniqueTags());
        assertTrue(typeSpecific.getBlockPushingSchema1());
        assertEquals(1, typeSpecific.getDockerTagRetention());
        assertTrue(typeSpecific.getEnableTokenAuthentication());
        assertFalse(typeSpecific.getListRemoteFolderItems());
        assertFalse(typeSpecific.getEnableForeignLayersCaching());
        assertEquals("", typeSpecific.getDockerProjectId());
        assertFalse(typeSpecific.getResolveDockerTagsByTimestamp());
        assertFalse(typeSpecific.getCachingLocalForeignLayersEnabled());
        assertEquals("https://registry-1.docker.io/", typeSpecific.getUrl());
    }

    @ParameterizedTest
    @MethodSource("proxyRepoConfigsAndModels")
    void testPopulateProxy(ProxyRepoConfig repoConfig, RepositoryConfigModel<?, ?, ?> repositoryConfigModel) {
        populateRepoConfigToModel(repoConfig, repositoryConfigModel);
        // proxy does not exist
        assertNull(getProxyKey(repositoryConfigModel));

        // create default proxy
        ProxyDescriptor defaultProxy = new ProxyDescriptor();
        defaultProxy.setKey("my-proxy");
        defaultProxy.setPlatformDefault(true);
        when(centralConfigService.getDescriptor().getDefaultProxy()).thenReturn(defaultProxy);

        repoConfig.setDisableProxy(true);
        populateRepoConfigToModel(repoConfig, repositoryConfigModel);

        // proxy is disabled
        assertNull(getProxyKey(repositoryConfigModel));

        repoConfig.setDisableProxy(false);
        populateRepoConfigToModel(repoConfig, repositoryConfigModel);

        // proxy should be the default
        assertEquals(getProxyKey(repositoryConfigModel), defaultProxy.getKey());

        String proxyRefKey = "some proxy ref";
        repoConfig.setProxyRef(proxyRefKey);
        when(centralConfigService.getDescriptor().getProxy(proxyRefKey)).thenReturn(new ProxyDescriptor());
        populateRepoConfigToModel(repoConfig, repositoryConfigModel);
        // proxy ref should override the default
        assertEquals(proxyRefKey, getProxyKey(repositoryConfigModel));
    }

    @Test
    void testResolveLocalP2RepoFromUrl() {
        String repoName = "repo";
        String p2RepoUrl = "local://" + repoName;

        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(repoName, RepoType.Generic);
        when(repositoryConfigService.getLocalOrFederatedRepoConfigByKey(repoName)).thenReturn(localRepoConfig);
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);

        P2Repo p2Repo = repoConfigModelBuilder.resolveLocalP2RepoFromUrl(p2RepoUrl);

        assertNotNull(p2Repo);
        assertEquals(repoName, p2Repo.getRepoKey());
        assertEquals(p2RepoUrl, p2Repo.getRepoUrl());
    }

    @Test
    void testCreateCacheConfig() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        RemoteCacheRepositoryConfigModel cacheConfig = repoConfigModelBuilder.createCacheConfig(remoteRepoConfig);
        long metadataRetrievalTimeoutSecsCache = cacheConfig.getMetadataRetrievalTimeoutSecs();
        long metadataRetrievalTimeoutSecsDescriptor =
                remoteRepoConfig.getRepoTypeConfig().getMetadataRetrievalTimeoutSecs();
        assertEquals(metadataRetrievalTimeoutSecsCache, metadataRetrievalTimeoutSecsDescriptor);

        remoteRepoConfig.getRepoTypeConfig().setMetadataRetrievalTimeoutSecs(20);
        cacheConfig = repoConfigModelBuilder.createCacheConfig(remoteRepoConfig);
        metadataRetrievalTimeoutSecsCache = cacheConfig.getMetadataRetrievalTimeoutSecs();
        metadataRetrievalTimeoutSecsDescriptor = remoteRepoConfig.getRepoTypeConfig().getMetadataRetrievalTimeoutSecs();
        assertEquals(metadataRetrievalTimeoutSecsCache, metadataRetrievalTimeoutSecsDescriptor);
    }

    @Test
    void testPopulateVirtualDescriptorCargo() {
        VirtualRepositoryConfigResolver virtualRepositoryConfigResolver = mock(VirtualRepositoryConfigResolver.class);
        when(virtualRepositoryConfigResolver.getOrderedRepos("repoKey")).thenReturn(List.of());
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());
        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Cargo);

        final VirtualRepositoryConfigModel virtualRepositoryConfigModel =
                new VirtualRepositoryConfigModel();
        repoConfigModelBuilder.setResolver(virtualRepositoryConfigResolver);
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.setProjectsService(projectsService);
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig,
                virtualRepositoryConfigModel);

        assertInstanceOf(CargoTypeSpecificConfigModel.class, virtualRepositoryConfigModel.getTypeSpecific());
        assertFalse(
                ((CargoTypeSpecificConfigModel) virtualRepositoryConfigModel.getTypeSpecific()).isAnonymousAccess());
        assertTrue(((CargoTypeSpecificConfigModel) virtualRepositoryConfigModel.getTypeSpecific()).isInternalIndex());
    }

    @Test
    void testPopulateVirtualDescriptorHex() {
        VirtualRepositoryConfigResolver virtualRepositoryConfigResolver = mock(VirtualRepositoryConfigResolver.class);
        when(virtualRepositoryConfigResolver.getOrderedRepos("repoKey")).thenReturn(List.of());
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());

        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Hex);

        final VirtualRepositoryConfigModel virtualRepositoryConfigModel =
                new VirtualRepositoryConfigModel();
        repoConfigModelBuilder.setResolver(virtualRepositoryConfigResolver);
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.setProjectsService(projectsService);
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig,
                virtualRepositoryConfigModel);

        assertInstanceOf(HexTypeSpecificConfigModel.class, virtualRepositoryConfigModel.getTypeSpecific());
        assertEquals(RepoType.Hex, virtualRepositoryConfigModel.getTypeSpecific().getRepoType());
    }

    @Test
    void testVirtualWithIneligibleRepositories() {
        VirtualRepositoryConfigResolver virtualRepositoryConfigResolver = mock(VirtualRepositoryConfigResolver.class);
        when(virtualRepositoryConfigResolver.getOrderedRepos("repoKey")).thenReturn(List.of());
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());
        repoConfigModelBuilder.setCentralConfig(centralConfigService);
        final VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Maven);
        final VirtualRepositoryConfigModel virtualRepositoryConfigModel =
                new VirtualRepositoryConfigModel();
        repoConfigModelBuilder.setResolver(virtualRepositoryConfigResolver);
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.setProjectsService(projectsService);
        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        when(repoDescriptor.getKey()).thenReturn("repo1");
        when(repoDescriptor.getType()).thenReturn(RepoType.Generic);
        when(projectsService.getIneligibleRepos(any(), any())).thenReturn(Set.of(repoDescriptor));
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig,
                virtualRepositoryConfigModel);
        assertEquals("repo1", virtualRepositoryConfigModel.getBasic().getIneligibleRepositories().stream()
                .toList().getFirst().getRepoName());
        assertEquals("local", virtualRepositoryConfigModel.getBasic().getIneligibleRepositories().stream()
                .toList().getFirst().getType());
    }

    @Test
    void testPopulateLocalDefaultDescriptorCargo() {
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());
        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Cargo);

        final LocalRepositoryConfigModel localRepositoryConfigModel = new LocalRepositoryConfigModel();
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(localRepoConfig, localRepositoryConfigModel);

        assertInstanceOf(CargoTypeSpecificConfigModel.class, localRepositoryConfigModel.getTypeSpecific());
        assertFalse(
                ((CargoTypeSpecificConfigModel) localRepositoryConfigModel.getTypeSpecific()).isAnonymousAccess());
        assertTrue(((CargoTypeSpecificConfigModel) localRepositoryConfigModel.getTypeSpecific()).isInternalIndex());
    }

    @ParameterizedTest
    @EnumSource(value = RepoType.class, names = {"Docker", "OCI", "HelmOCI"})
    void testPopulateLocalDefaultDescriptorDocker(RepoType repoType) {
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());
        repoConfigModelBuilder.setCentralConfig(centralConfigService);
        final LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", repoType);
        final LocalRepositoryConfigModel localRepositoryConfigModel = new LocalRepositoryConfigModel();
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(localRepoConfig, localRepositoryConfigModel);

        assertInstanceOf(DockerTypeSpecificConfigModel.class, localRepositoryConfigModel.getTypeSpecific());
        assertEquals(repoType, localRepositoryConfigModel.getTypeSpecific().getRepoType());
        DockerTypeSpecificConfigModel typeSpecific =
                (DockerTypeSpecificConfigModel) localRepositoryConfigModel.getTypeSpecific();
        assertEquals(DockerApiVersion.V2, typeSpecific.getDockerApiVersion());
        assertEquals(0, typeSpecific.getMaxUniqueTags());
        assertTrue(typeSpecific.getBlockPushingSchema1());
        assertEquals(1, typeSpecific.getDockerTagRetention());
        assertTrue(typeSpecific.getEnableTokenAuthentication());
        assertFalse(typeSpecific.getListRemoteFolderItems());
        assertFalse(typeSpecific.getEnableForeignLayersCaching());
        assertEquals("", typeSpecific.getDockerProjectId());
        assertFalse(typeSpecific.getResolveDockerTagsByTimestamp());
        assertFalse(typeSpecific.getCachingLocalForeignLayersEnabled());
        assertEquals("https://registry-1.docker.io/", typeSpecific.getUrl());
    }

    @Test
    void testPopulateVirtualRepositoryConfigSaveRepoOrder() {
        VirtualRepoConfig virtualRepo = VirtualRepoConfig.create("v1-cool-repo", RepoType.Generic);
        VirtualRepoConfig virtualRepo2 = VirtualRepoConfig.create("v2-cool-repo", RepoType.Generic);
        RemoteRepoConfig remoteRepo = RemoteRepoConfig.create("r1-cool-repo", RepoType.Generic);
        RemoteRepoConfig remoteRepo2 = RemoteRepoConfig.create("r2-cool-repo", RepoType.Generic);
        LocalRepoConfig localRepo = LocalRepoConfig.create("l1-cool-repo", RepoType.Generic);
        LocalRepoConfig localRepo2 = LocalRepoConfig.create("l2-cool-repo", RepoType.Generic);

        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        VirtualRepositoryConfigResolver virtualRepositoryConfigResolver = mock(VirtualRepositoryConfigResolver.class);
        VirtualRepositoryConfigModel virtualRepositoryConfigModel = new VirtualRepositoryConfigModel();

        repoConfigModelBuilder.setCentralConfig(centralConfigService);
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.setResolver(virtualRepositoryConfigResolver);
        repoConfigModelBuilder.setProjectsService(projectsService);

        when(virtualRepositoryConfigResolver.getOrderedRepos("v1-cool-repo")).thenReturn(
                List.of(localRepo, localRepo2, remoteRepo, remoteRepo2));
        when(repositoryConfigService.getRepoConfigsByKeysInSpecificOrder(virtualRepo)).thenReturn(
                new LinkedHashSet<>(Arrays.asList(localRepo, remoteRepo, virtualRepo2)));

        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepo,
                virtualRepositoryConfigModel);

        assertEquals(virtualRepositoryConfigModel.getBasic().getResolvedRepositories().stream()
                        .map(VirtualSelectedRepository::getRepoName).toList(),
                Stream.of(localRepo, localRepo2, remoteRepo, remoteRepo2)
                        .map(RepoConfig::getKey).toList());

        assertEquals(virtualRepositoryConfigModel.getBasic().getSelectedRepositories().stream()
                        .map(VirtualSelectedRepository::getRepoName).toList(),
                Stream.of(localRepo, remoteRepo, virtualRepo2)
                        .map(RepoConfig::getKey).toList());
    }

    @Test
    void testPopulateMavenVirtualRepositoryCleanupPolicy() {
        RepoConfigModelBuilder mavenConfigBuilder = mockConfigModelBuilder();
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Maven);
        mavenConfigBuilder.setProjectsService(projectsService);
        VirtualRepositoryConfigModel model = new VirtualRepositoryConfigModel();
        mavenConfigBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig, model);
        MavenTypeSpecificConfigModel virtualTypeSpecific;
        virtualRepoConfig.getPackageTypeConfig().put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(),
                PomCleanupPolicy.discard_any_reference.getType());
        mavenConfigBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig, model);
        virtualTypeSpecific = (MavenTypeSpecificConfigModel) model.getTypeSpecific();
        assertEquals(PomCleanupPolicy.discard_any_reference, virtualTypeSpecific.getPomCleanupPolicy());
    }

    @Test
    void testPopulateLocalDescriptorTerraformBackend() {
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());
        repoConfigModelBuilder.setCentralConfig(centralConfigService);

        final LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.TerraformBackend);

        final LocalRepositoryConfigModel localRepositoryConfigModel = new LocalRepositoryConfigModel();
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(localRepoConfig, localRepositoryConfigModel);
        assertInstanceOf(TerraformBackendTypeSpecificConfigModel.class, localRepositoryConfigModel.getTypeSpecific());
        assertTrue(((TerraformBackendTypeSpecificConfigModel) localRepositoryConfigModel.getTypeSpecific())
                .isEncryptStates());
        localRepoConfig.getPackageTypeConfig().put(ENCRYPT_STATES.getKey(), "false");
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(localRepoConfig, localRepositoryConfigModel);
        assertInstanceOf(TerraformBackendTypeSpecificConfigModel.class, localRepositoryConfigModel.getTypeSpecific());
        assertFalse(((TerraformBackendTypeSpecificConfigModel) localRepositoryConfigModel.getTypeSpecific())
                .isEncryptStates());
    }

    /**
     * To test package type specific config enableNormalizedVersion getting added for nuget package type
     * should return true when it is enabled
     * should return false when it is disabled.
     */
    @Test
    void testPopulateLocalDescriptorNuget() {
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getRepoConfigsByKeys(any())).thenReturn(Set.of());
        repoConfigModelBuilder.setCentralConfig(centralConfigService);
        final LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.NuGet);
        localRepoConfig.getPackageTypeConfig().put(ENABLE_NUGET_NORMALIZED_VERSION.getKey(), "true");
        final LocalRepositoryConfigModel localRepositoryConfigModel = new LocalRepositoryConfigModel();
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);

        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(localRepoConfig, localRepositoryConfigModel);

        assertInstanceOf(NugetTypeSpecificConfigModel.class, localRepositoryConfigModel.getTypeSpecific());
        assertTrue(((NugetTypeSpecificConfigModel) localRepositoryConfigModel.getTypeSpecific())
                .isEnableNormalizedVersion());
        localRepoConfig.getPackageTypeConfig().put(ENABLE_NUGET_NORMALIZED_VERSION.getKey(), "false");
        repoConfigModelBuilder.populateLocalDescriptorValuesToModel(localRepoConfig, localRepositoryConfigModel);
        assertInstanceOf(NugetTypeSpecificConfigModel.class, localRepositoryConfigModel.getTypeSpecific());
        assertFalse(((NugetTypeSpecificConfigModel) localRepositoryConfigModel.getTypeSpecific())
                .isEnableNormalizedVersion());
    }

    @Nonnull
    private static RepoConfigModelBuilder mockConfigModelBuilder() {
        RepoConfigModelBuilder repoConfigModelBuilder = new RepoConfigModelBuilder();
        RepositoryConfigService repositoryConfigService = Mockito.mock(RepositoryConfigService.class);
        CentralConfigService centralConfig = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        VirtualRepositoryConfigResolver virtualRepositoryConfigResolver =
                Mockito.mock(VirtualRepositoryConfigResolver.class);
        repoConfigModelBuilder.setResolver(virtualRepositoryConfigResolver);
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.setCentralConfig(centralConfig);
        Mockito.when(virtualRepositoryConfigResolver.getOrderedRepos(anyString()))
                .thenReturn(new ArrayList<>());
        Mockito.when(repositoryConfigService.getRepoConfigsByKeys(new HashSet<>()))
                .thenReturn(new HashSet<>());
        Mockito.when(centralConfig.getDescriptor()).thenReturn(configDescriptor);
        return repoConfigModelBuilder;
    }

    private void testPopulateVirtualRepositoryConanForceAuthentication(
            RepoConfigModelBuilder repoConfigModelBuilder) {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Conan);
        VirtualRepositoryConfigModel model = new VirtualRepositoryConfigModel();
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig, model);
        ConanTypeSpecificConfigModel virtualTypeSpecific = (ConanTypeSpecificConfigModel) model.getTypeSpecific();
        assertFalse(virtualTypeSpecific.getForceConanAuthentication());
        virtualRepoConfig.getPackageTypeConfig().put(FORCE_CONAN_AUTHENTICATION.getKey(), Boolean.TRUE.toString());
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig, model);
        virtualTypeSpecific = (ConanTypeSpecificConfigModel) model.getTypeSpecific();
        assertTrue(virtualTypeSpecific.getForceConanAuthentication());
    }

    private void testPopulateVirtualRepositoryMavenForceAuthentication(
            RepoConfigModelBuilder repoConfigModelBuilder) {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Maven);
        VirtualRepositoryConfigModel model = new VirtualRepositoryConfigModel();
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig, model);
        MavenTypeSpecificConfigModel virtualTypeSpecific = (MavenTypeSpecificConfigModel) model.getTypeSpecific();
        assertFalse(virtualTypeSpecific.isForceMavenAuthentication());
        virtualRepoConfig.getPackageTypeConfig().put(FORCE_MAVEN_AUTHENTICATION.getKey(), Boolean.TRUE.toString());
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig, model);
        virtualTypeSpecific = (MavenTypeSpecificConfigModel) model.getTypeSpecific();
        assertTrue(virtualTypeSpecific.isForceMavenAuthentication());
    }

    @Test
    void testPopulateVirtualRepositoryNugetValuesToModel() {
        RepositoryConfigService repositoryConfigService = Mockito.mock(RepositoryConfigService.class);
        CentralConfigService centralConfig = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        VirtualRepositoryConfigResolver virtualRepositoryConfigResolver =
                Mockito.mock(VirtualRepositoryConfigResolver.class);
        repoConfigModelBuilder.setResolver(virtualRepositoryConfigResolver);
        repoConfigModelBuilder.setRepositoryConfigService(repositoryConfigService);
        repoConfigModelBuilder.setCentralConfig(centralConfig);
        Mockito.when(virtualRepositoryConfigResolver.getOrderedRepos(anyString()))
                .thenReturn(new ArrayList<>());
        Mockito.when(repositoryConfigService.getRepoConfigsByKeys(new HashSet<>()))
                .thenReturn(new HashSet<>());
        Mockito.when(centralConfig.getDescriptor()).thenReturn(configDescriptor);
        Mockito.when(configDescriptor.getCurrentReverseProxy()).thenReturn(null);

        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.NuGet);
        virtualRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");
        VirtualRepositoryConfigModel model = new VirtualRepositoryConfigModel();
        repoConfigModelBuilder.setProjectsService(projectsService);
        repoConfigModelBuilder.populateVirtualRepositoryConfigValuesToModel(virtualRepoConfig, model);
        assertInstanceOf(NugetTypeSpecificConfigModel.class, model.getTypeSpecific());
        assertEquals(RepoType.NuGet, model.getTypeSpecific().getRepoType());

        NugetTypeSpecificConfigModel typeSpecific =
                (NugetTypeSpecificConfigModel) model.getTypeSpecific();

        assertFalse(typeSpecific.isForceNugetAuthentication());
        assertTrue(typeSpecific.isEnableNormalizedVersion());
    }

    private String getProxyKey(RepositoryConfigModel<?, ?, ?> model) {
        if (model instanceof RemoteRepositoryConfigModel remoteRepositoryConfigModel) {
            return remoteRepositoryConfigModel.getAdvanced().getNetwork().getProxy();
        }
        if (model instanceof FederatedRepositoryConfigModel federatedRepositoryConfigModel) {
            return federatedRepositoryConfigModel.getAdvanced().getProxy();
        }
        fail(String.format("failed to get proxy key from model '%s'", model.getClass().getSimpleName()));
        return null;
    }

    @Test
    void testCreateFederatedConfig_nullFederatedMembers_returnsEmptyMirrors() {
        // Regression guard for NPE when getFederatedMembers() returns null (unmigrated repo)
        FederatedRepoConfig config = mock(FederatedRepoConfig.class);
        when(config.getFederatedMembers()).thenReturn(null);

        FederationConfigModel result = repoConfigModelBuilder.createFederatedConfig(config);

        assertNotNull(result);
        assertNull(result.getMirrors(), "mirrors should be null/unset when members list is null");
    }

    @Test
    void testCreateFederatedConfig_emptyFederatedMembers_returnsEmptyMirrors() {
        FederatedRepoConfig config = FederatedRepoConfig.create("my-fed-repo", RepoType.Generic);

        FederationConfigModel result = repoConfigModelBuilder.createFederatedConfig(config);

        assertNotNull(result);
        assertTrue(result.getMirrors() == null || result.getMirrors().isEmpty(),
                "mirrors should be empty when repo has no members");
    }

    @Test
    void testCreateFederationConfigFromBaseConfig_nullFederationConfig_returnsNull() {
        RemoteRepoConfig config = RemoteRepoConfig.create("my-remote-repo", RepoType.Generic);
        config.getBaseConfig().setFederationConfig(null);

        assertNull(repoConfigModelBuilder.createFederationConfigFromBaseConfig(config));
    }

    @Test
    void testCreateFederationConfigFromBaseConfig_emptyMembers_returnsNull() {
        RemoteRepoConfig config = RemoteRepoConfig.create("my-remote-repo", RepoType.Generic);

        assertNull(repoConfigModelBuilder.createFederationConfigFromBaseConfig(config));
    }

    @Test
    void testCreateFederationConfigFromBaseConfig_withRemoteMember_returnsModel() {
        RemoteRepoConfig config = RemoteRepoConfig.create("my-remote-repo", RepoType.Generic);
        org.artifactory.repo.federated.FederatedMember member = new org.artifactory.repo.federated.FederatedMember();
        member.setUrlOrTemplate("http://remote-site:8081/artifactory/my-remote-repo");
        member.setEnabled(true);
        config.getBaseConfig().getFederationConfig().setMembers(
                com.google.common.collect.Lists.newArrayList(member));

        FederationConfigModel result = repoConfigModelBuilder.createFederationConfigFromBaseConfig(config);

        assertNotNull(result);
        assertEquals(1, result.getMirrors().size());
    }

    private void populateRepoConfigToModel(ProxyRepoConfig repoConfig, RepositoryConfigModel<?, ?, ?> model) {
        if (repoConfig instanceof RemoteRepoConfig remoteRepoConfig) {
            repoConfigModelBuilder.populateRemoteRepositoryConfigValuesToModel(remoteRepoConfig,
                    (RemoteRepositoryConfigModel) model);
        } else if (repoConfig instanceof FederatedRepoConfig federatedRepoConfig) {
            repoConfigModelBuilder.populateFederatedDescriptorValuesToModel(federatedRepoConfig,
                    (FederatedRepositoryConfigModel) model);
        } else {
            fail(String.format("failed to populate repo config '%s' to model '%s'",
                    repoConfig.getClass().getSimpleName(), model.getClass().getSimpleName()));
        }
    }

    private static Stream<Arguments> proxyRepoConfigsAndModels() {
        return Stream.of(
                Arguments.of(RemoteRepoConfig.create("remote-repo", RepoType.Generic),
                        new RemoteRepositoryConfigModel()),
                Arguments.of(FederatedRepoConfig.create("federated-repo", RepoType.Generic),
                        new FederatedRepositoryConfigModel()));
    }

    private CdnRedirectRepoConfig getEnabledCdnConfig() {
        CdnRedirectRepoConfig cdnRedirectRepoConfig = new CdnRedirectRepoConfig();
        cdnRedirectRepoConfig.setEnabled(true);
        return cdnRedirectRepoConfig;
    }


    @Test
    void testPopulateRemoteRepositoryAnsibleConfigValuesToModel() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Ansible);
        remoteRepoConfig.getPackageTypeConfig().put(AUTH_URL.getKey(), "https://sso.example.com/auth");

        AnsibleTypeSpecificConfigModel remoteTypeSpecific =
                (AnsibleTypeSpecificConfigModel) repoConfigModelBuilder
                        .createRemoteTypeSpecific(RepoType.Ansible, remoteRepoConfig);

        assertEquals(RepoType.Ansible, remoteTypeSpecific.getRepoType());
        assertEquals("https://sso.example.com/auth", remoteTypeSpecific.getAuthUrl());
    }

    @Test
    void testPopulateRemoteRepositoryAnsibleConfigDefaultValues() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Ansible);

        AnsibleTypeSpecificConfigModel remoteTypeSpecific =
                (AnsibleTypeSpecificConfigModel) repoConfigModelBuilder
                        .createRemoteTypeSpecific(RepoType.Ansible, remoteRepoConfig);

        assertEquals(RepoType.Ansible, remoteTypeSpecific.getRepoType());
        assertEquals(StringUtils.EMPTY, remoteTypeSpecific.getAuthUrl());
    }

    private RepoConfigModelBuilder getRepoConfigModelBuilder() {
        repoConfigModelBuilder = new RepoConfigModelBuilder();
        repoConfigModelBuilder.setCentralConfig(centralConfigService);
        repoConfigModelBuilder.setProjectsService(projectsService);
        when(projectsService.getProjectKeyOfRepository(any())).thenReturn("projectKey");
        final AddonsManager addonsManager = mock(AddonsManager.class);
        final ArtifactWebstartAddon artifactWebstartAddon = mock(ArtifactWebstartAddon.class);
        when(artifactWebstartAddon.getSslCertNames()).thenReturn(List.of());
        when(addonsManager.addonByType(ArtifactWebstartAddon.class)).thenReturn(artifactWebstartAddon);
        repoConfigModelBuilder.setAddonsManager(addonsManager);
        return repoConfigModelBuilder;
    }
}