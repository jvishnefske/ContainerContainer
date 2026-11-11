package org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.validation.constraints.NotNull;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.NugetGeneralConfig;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.engine.DownloadRedirectHelper;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.ProxyRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.local.LocalRepoChecksumPolicyType;
import org.artifactory.repo.packagetype.SnapshotVersionBehavior;
import org.artifactory.repo.remote.ContentSynchronisation;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.artifactory.repo.vcs.VcsType;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.ui.rest.model.admin.configuration.repository.BasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.CdnRedirectRepoConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepoAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteCacheRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AnsibleTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AlpineTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CocoaPodsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.ConanTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CondaTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.DockerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GoTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HelmOciTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HelmTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HexTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HuggingFaceMLTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NimModelTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NixTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NpmTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NugetTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.OpkgTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TerraformBackendTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TerraformTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AIEditorExtensionsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.jfrog.common.JsonUtils;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.AdditionalAnswers;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_DOCKER_REMOTE_RETRIEVAL_CACHE_PERIOD;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_HUGGING_FACE_ML_REGISTRY_URL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_NIMMODEL_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.AUTH_URL;

import static org.artifactory.util.PackageTypeConfigFields.COCOA_PODS_CDN_URL;
import static org.artifactory.util.PackageTypeConfigFields.COCOA_PODS_SPECS_URL;
import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;
import static org.artifactory.util.PackageTypeConfigFields.EXTERNAL_DEPENDENCIES_ENABLED;
import static org.artifactory.util.PackageTypeConfigFields.EXTERNAL_DEPENDENCIES_PATTERNS;
import static org.artifactory.util.PackageTypeConfigFields.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF;
import static org.artifactory.util.PackageTypeConfigFields.GITLAB_RESOLVE_SUBGROUPS;
import static org.artifactory.util.PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY;
import static org.artifactory.util.PackageTypeConfigFields.SPECS_REPO_DOWNLOAD_PROVIDER;
import static org.artifactory.util.PackageTypeConfigFields.VCS_GIT_DOWNLOAD_URL;
import static org.artifactory.util.PackageTypeConfigFields.VCS_GIT_PROVIDER;
import static org.artifactory.util.PackageTypeConfigFields.VCS_TYPE;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Author: Ivan Perepelytsya
 * Date: 6/3/20.
 */
class RepoConfigBuilderTest {

    @Mock
    private DownloadRedirectHelper downloadRedirectHelper;
    @Mock
    private ArtifactoryBinaryService binaryService;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private MutableCentralConfigDescriptor mutableCentralConfigDescriptor;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor;
    @Mock
    private NugetGeneralConfig nugetGeneralConfig;
    @Mock
    private EncryptionWrapper encryptionWrapper;
    @InjectMocks
    private RepoConfigDescriptorBuilder repoConfigBuilder;
    private TypeSpecificConfigModel typeSpecificConfigModel;

    private AutoCloseable mockitoAutoCloseable;

    @BeforeEach
    public void initMocks() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        ArtifactoryHome.bind(artifactoryHome);
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        when(mutableCentralConfigDescriptor.getCurrentReverseProxy()).thenReturn(null);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(encryptionWrapper.encryptIfNeeded(any())).then(AdditionalAnswers.returnsFirstArg());
        when(artifactoryHome.getMasterEncryptionWrapper()).thenReturn(encryptionWrapper);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);
        when(downloadRedirectHelper.isRedirectEnableForThisRepoType(anyString())).thenReturn(true);
        when(binaryService.isCdnAllowed()).thenReturn(true);
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        mockitoAutoCloseable.close();
    }

    @Test
    void testBuildLocalDescriptorWithCdnRedirectEnabled() {
        typeSpecificConfigModel = new NpmTypeSpecificConfigModel();
        final LocalRepositoryConfigModel model = prepareLocalModel();
        LocalConfig localConfig = repoConfigBuilder.buildLocalConfig(model);
        assertNotNull(localConfig.getRepoTypeConfig().getCdnRedirectRepoConfig());
        assertTrue(localConfig.getRepoTypeConfig().getCdnRedirectRepoConfig().isEnabled());
    }

    @Test
    void testBuildLocalDescriptorTerraformBackend() {
        TerraformBackendTypeSpecificConfigModel configModel = new TerraformBackendTypeSpecificConfigModel();
        configModel.setEncryptStates(false);
        typeSpecificConfigModel = configModel;
        final LocalRepositoryConfigModel model = prepareLocalModel();
        LocalConfig localConfig = repoConfigBuilder.buildLocalConfig(model);
        assertEquals("false", localConfig.getPackageTypeConfig().get(ENCRYPT_STATES.getKey()));
    }

    @Test
    void testPopulateProxy() {
        typeSpecificConfigModel = new NugetTypeSpecificConfigModel();
        String proxyKey = "some-proxy-key";
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getRepositoriesGeneralConfig()).thenReturn(repositoriesGeneralConfigDescriptor);
        when(repositoriesGeneralConfigDescriptor.getNuget()).thenReturn(nugetGeneralConfig);
        when(nugetGeneralConfig.isEnableGlobalNormalization()).thenReturn(false);
        List.of(prepareFederatedRepoModel(), prepareRemoteModel()).forEach(model -> {
            ProxyRepoConfig repoConfig = buildRepoConfig(model);
            assertNotNull(repoConfig);
            //proxy does not exist
            assertNull(repoConfig.getProxyRef());
            // create default proxy
            ProxyDescriptor proxyDescriptor = new ProxyDescriptor();
            proxyDescriptor.setKey(proxyKey);
            proxyDescriptor.setPlatformDefault(true);
            when(descriptor.getProxy(proxyKey)).thenReturn(proxyDescriptor);

            setProxyKey(model, proxyKey);
            setDisableProxy(model, true);
            repoConfig = buildRepoConfig(model);
            assertNotNull(repoConfig);

            // proxy is disabled
            assertNull(repoConfig.getProxyRef());

            setDisableProxy(model, false);
            // default proxy should not be set as proxy ref
            assertNull(repoConfig.getProxyRef());

            proxyDescriptor.setPlatformDefault(false);
            repoConfig = buildRepoConfig(model);
            assertNotNull(repoConfig);
            // a non default proxy should be set as proxy ref
            assertNotNull(repoConfig.getProxyRef());
            assertEquals(repoConfig.getProxyRef(), proxyKey);
        });
    }

    private ProxyRepoConfig buildRepoConfig(RepositoryConfigModel model) {
        if (model instanceof RemoteRepositoryConfigModel remoteRepositoryConfigModel) {
            return repoConfigBuilder.buildRemoteRepoConfig(remoteRepositoryConfigModel);
        }
        if (model instanceof FederatedRepositoryConfigModel federatedRepositoryConfigModel) {
            return repoConfigBuilder.buildFederatedRepoConfig(federatedRepositoryConfigModel);
        }
        Assert.fail(String.format("failed to repo config from model '%s'", model.getClass().getSimpleName()));
        return null;
    }

    private void setProxyKey(RepositoryConfigModel model, String proxyKey) {
        if (model instanceof RemoteRepositoryConfigModel remoteRepositoryConfigModel) {
            remoteRepositoryConfigModel.getAdvanced().getNetwork().setProxy(proxyKey);
        } else if (model instanceof FederatedRepositoryConfigModel federatedRepositoryConfigModel) {
            federatedRepositoryConfigModel.getAdvanced().setProxy(proxyKey);
        } else {
            Assert.fail(String.format("failed to set proxy key to model '%s'", model.getClass().getSimpleName()));
        }
    }

    private void setDisableProxy(RepositoryConfigModel model, boolean disableProxy) {
        if (model instanceof RemoteRepositoryConfigModel remoteRepositoryConfigModel) {
            remoteRepositoryConfigModel.getAdvanced().getNetwork().setDisableProxy(disableProxy);
        } else if (model instanceof FederatedRepositoryConfigModel federatedRepositoryConfigModel) {
            federatedRepositoryConfigModel.getAdvanced().setDisableProxy(disableProxy);
        } else {
            Assert.fail(String.format("failed to set disableProxy '%b' to model '%s'", disableProxy,
                    model.getClass().getSimpleName()));
        }
    }

    @Test
    void testBuildRemoteDescriptor() {
        typeSpecificConfigModel = new NpmTypeSpecificConfigModel();
        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);
        assertNotNull(remoteRepoConfig.getRepoTypeConfig().getCdnRedirectRepoConfig());
        assertTrue(remoteRepoConfig.getRepoTypeConfig().getCdnRedirectRepoConfig().isEnabled());
        long metadataRetrievalTimeoutSecs = model.getAdvanced().getCache().getMetadataRetrievalTimeoutSecs();
        assertEquals(remoteRepoConfig.getRepoTypeConfig().getMetadataRetrievalTimeoutSecs(),
                metadataRetrievalTimeoutSecs);
    }

    @Test
    void testGoBuildRemoteDescriptor() {
        typeSpecificConfigModel = new GoTypeSpecificConfigModel();
        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);
        assertEquals(remoteRepoConfig.getRepoTypeConfig().getMissedRetrievalCachePeriodSecs(), 1800);
        assertNotNull(remoteRepoConfig.getPackageTypeConfig());
        assertEquals(remoteRepoConfig.getPackageTypeConfig().get(GITLAB_RESOLVE_SUBGROUPS.getKey()), "false");
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("huggingFaceRepoUrlProvider")
    void testHuggingFaceTypeSpecificDescriptorValues(String repoUrl, boolean expectedEnableTokenAuthentication) {
        typeSpecificConfigModel = new HuggingFaceMLTypeSpecificConfigModel();
        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        model.getBasic().setUrl(repoUrl);
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);
        assertFalse(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        assertEquals(expectedEnableTokenAuthentication,
                remoteRepoConfig.getRepoTypeConfig().isEnableTokenAuthentication());
    }

    private static Stream<Arguments> huggingFaceRepoUrlProvider() {
        return Stream.of(
                Arguments.of(DEFAULT_HUGGING_FACE_ML_REGISTRY_URL, true),
                Arguments.of("https://NotHuggingface.co/", false)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("nimModelRepoUrlProvider")
    void testNimModelTypeSpecificDescriptorValues(String repoUrl, boolean expectedEnableTokenAuthentication) {
        typeSpecificConfigModel = new NimModelTypeSpecificConfigModel();
        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        model.getBasic().setUrl(repoUrl);
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);
        assertFalse(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        assertEquals(expectedEnableTokenAuthentication,
                remoteRepoConfig.getRepoTypeConfig().isEnableTokenAuthentication());
    }

    private static Stream<Arguments> nimModelRepoUrlProvider() {
        return Stream.of(
                Arguments.of(DEFAULT_NIMMODEL_REGISTRY_URL, true),
                Arguments.of("https://NotNimModel.co/", false)
        );
    }

    @Test
    void testBuildDockerRemoteDescriptorWithNewCacheRetrievalPeriod() {
        typeSpecificConfigModel = new DockerTypeSpecificConfigModel();
        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);
        assertEquals(remoteRepoConfig.getRepoTypeConfig().getRetrievalCachePeriodSecs(),
                DEFAULT_DOCKER_REMOTE_RETRIEVAL_CACHE_PERIOD);
        assertNotNull(remoteRepoConfig.getPackageTypeConfig().get("dockerProjectId"));
    }

    @Test
    void testConanTypeSpecificDescriptorValues() {
        typeSpecificConfigModel = new ConanTypeSpecificConfigModel();
        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);
        assertFalse(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
    }

    @ParameterizedTest
    @MethodSource("provideExternalDependenciesPatternsForCocoapods")
    void testPopulateRemoteRepoCocoaPodsPackageTypeConfigValues(List<String> providedExternalDependenciesPatterns) {
        typeSpecificConfigModel = new CocoaPodsTypeSpecificConfigModel();
        CocoaPodsTypeSpecificConfigModel configModel = (CocoaPodsTypeSpecificConfigModel) typeSpecificConfigModel;
        configModel.setGitProvider(VcsGitProvider.ARTIFACTORY);
        configModel.setSpecsRepoUrl("http://localhost:8082/artifactory/api/pods/my-remote-repo");
        configModel.setEnableExternalDependencies(true);

        if (providedExternalDependenciesPatterns != null && !providedExternalDependenciesPatterns.isEmpty()) {
            configModel.setExternalPatterns(providedExternalDependenciesPatterns);
        }

        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);
        var packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        assertEquals(packageTypeConfig.get(VCS_GIT_PROVIDER.getKey()), VcsGitProvider.ARTIFACTORY.name());
        assertEquals(packageTypeConfig.get(VCS_TYPE.getKey()), VcsType.GIT.name());
        assertEquals(packageTypeConfig.get(SPECS_REPO_DOWNLOAD_PROVIDER.getKey()), VcsGitProvider.ARTIFACTORY.name());
        assertEquals(packageTypeConfig.get(COCOA_PODS_SPECS_URL.getKey()), configModel.getSpecsRepoUrl());
        assertEquals(packageTypeConfig.get(EXTERNAL_DEPENDENCIES_ENABLED.getKey()),
                String.valueOf(configModel.getEnableExternalDependencies()));
        assertEquals(JsonUtils.getInstance()
                .readValue(packageTypeConfig.get(EXTERNAL_DEPENDENCIES_PATTERNS.getKey()),
                        new TypeReference<ArrayList<String>>() {
                        }), configModel.getExternalPatterns());
        assertNull(packageTypeConfig.get(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF.getKey()));
    }

    @Test
    void testPopulateRemoteRepoHexPackageTypeConfigValues() {
        typeSpecificConfigModel = new HexTypeSpecificConfigModel();
        HexTypeSpecificConfigModel configModel = (HexTypeSpecificConfigModel) typeSpecificConfigModel;
        configModel.setUrl("http://localhost:8082/artifactory/api/hex/my-remote-repo");
        configModel.setHexPublicKey("mykey");

        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);
        var packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();

        assertEquals(packageTypeConfig.get(HEX_REGISTRY_PUBLIC_KEY.getKey()), configModel.getHexPublicKey());
    }

    @Test
    void testPopulateVirtualBasicDescriptorValues() {
        VirtualBasicRepositoryConfigModel model = mock(VirtualBasicRepositoryConfigModel.class);
        String defaultDeploymentRepoName = "defaultDeploymentRepoName";
        when(model.getDefaultDeploymentRepo()).thenReturn(defaultDeploymentRepoName);
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        FederatedRepoConfig federatedRepoConfig = createFederatedRepoConfig(defaultDeploymentRepoName);
        when(repositoryConfigService.getRepoConfigsByType(FEDERATED)).thenReturn(List.of(federatedRepoConfig));
        when(repositoryConfigService.getRepoConfigsByType(LOCAL)).thenReturn(List.of());
        repoConfigBuilder.populateVirtualBasicConfigValues(model, virtualRepoConfig);
        assertEquals(virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef(), defaultDeploymentRepoName);
    }

    @Test
    void testBuildNixVirtualRepoConfigWithVirtualRetrievalCachePeriod() {
        // Given
        Long expectedCachePeriod = 3600L;
        NixTypeSpecificConfigModel nixConfigModel = new NixTypeSpecificConfigModel();
        nixConfigModel.setVirtualRetrievalCachePeriodSecs(expectedCachePeriod);

        VirtualRepositoryConfigModel model = new VirtualRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("nix-virtual");
        model.setGeneral(general);

        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        basic.setSelectedRepositories(List.of());
        model.setBasic(basic);

        VirtualAdvancedRepositoryConfigModel advanced = new VirtualAdvancedRepositoryConfigModel();
        model.setAdvanced(advanced);

        model.setTypeSpecific(nixConfigModel);

        // When
        VirtualRepoConfig virtualRepoConfig = repoConfigBuilder.buildVirtualRepoConfig(model);

        // Then
        assertNotNull(virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig());
        assertEquals(expectedCachePeriod,
                virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig().getVirtualRetrievalCachePeriodSecs());
    }

    @Test
    void buildHelmRemoteConfigListRemoteFolderItemsFalse() {
        RemoteRepositoryConfigModel remoteConfigModel =
                mock(RemoteRepositoryConfigModel.class, RETURNS_DEEP_STUBS);
        HelmTypeSpecificConfigModel helmConfigModel =
                mock(HelmTypeSpecificConfigModel.class, RETURNS_DEEP_STUBS);
        when(remoteConfigModel.getTypeSpecific()).thenReturn(helmConfigModel);
        when(remoteConfigModel.getGeneral().getRepoKey()).thenReturn("key");
        when(remoteConfigModel.getTypeSpecific().getRepoType()).thenReturn(RepoType.Helm);
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(remoteConfigModel);
        Assertions.assertFalse(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
    }

    @Test
    void buildOpkgRemoteConfigListRemoteFolderItemsSetToTrue() {
        RemoteRepositoryConfigModel remoteConfigModel =
                mock(RemoteRepositoryConfigModel.class, RETURNS_DEEP_STUBS);
        OpkgTypeSpecificConfigModel opkgConfigModel =
                mock(OpkgTypeSpecificConfigModel.class, RETURNS_DEEP_STUBS);
        when(opkgConfigModel.isListRemoteFolderItems()).thenReturn(true);
        when(remoteConfigModel.getTypeSpecific()).thenReturn(opkgConfigModel);
        when(remoteConfigModel.getGeneral().getRepoKey()).thenReturn("key");
        when(remoteConfigModel.getTypeSpecific().getRepoType()).thenReturn(RepoType.Opkg);
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(remoteConfigModel);
        Assertions.assertTrue(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
    }

    @NotNull
    private FederatedRepoConfig createFederatedRepoConfig(String repoName) {
        FederatedRepoConfig federatedRepoConfig = mock(FederatedRepoConfig.class, RETURNS_DEEP_STUBS);
        when(federatedRepoConfig.getKey()).thenReturn(repoName);
        when(federatedRepoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(federatedRepoConfig.getRepoTypeConfig().getSnapshotVersionBehavior()).thenReturn(
                SnapshotVersionBehavior.DEPLOYER);
        when(federatedRepoConfig.getRepoTypeConfig().getChecksumPolicyType()).thenReturn(
                LocalRepoChecksumPolicyType.CLIENT);
        return federatedRepoConfig;
    }

    private static Stream<Arguments> providerTestWithoutStoreArtifactsLocally() {
        return Stream.of(
                Arguments.of(new DockerTypeSpecificConfigModel()),
                Arguments.of(new TerraformTypeSpecificConfigModel()),
                Arguments.of(new HelmOciTypeSpecificConfigModel())
        );
    }

    @ParameterizedTest
    @MethodSource("providerTestWithoutStoreArtifactsLocally")
    void testWithoutStoreArtifactsLocally(TypeSpecificConfigModel configModel) {
        typeSpecificConfigModel = configModel;
        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        model.getAdvanced().setStoreArtifactsLocally(false);
        try {
            repoConfigBuilder.buildRemoteRepoConfig(model);
            Assert.fail("Should throw an exception");
        } catch (IllegalArgumentException exception) {
            assertEquals(exception.getMessage(), typeSpecificConfigModel.getRepoType().getDisplayName() +
                    " prerequisite - artifacts must be stored locally in cache");
        }
    }

    private void fillBasic(BasicRepositoryConfigModel basic) {
        basic.setPublicDescription("");
        basic.setInternalDescription("");
        basic.setIncludesPattern("");
        basic.setExcludesPattern("");
    }

    private LocalRepositoryConfigModel prepareLocalModel() {
        final LocalRepositoryConfigModel model = new LocalRepositoryConfigModel();
        CdnRedirectRepoConfigModel cdnRedirectRepoConfig = new CdnRedirectRepoConfigModel();
        cdnRedirectRepoConfig.setEnabled(true);
        final LocalAdvancedRepositoryConfigModel advanced = new LocalAdvancedRepositoryConfigModel();
        final GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        final LocalBasicRepositoryConfigModel basic = new LocalBasicRepositoryConfigModel();
        fillBasic(basic);
        general.setRepoKey("repo");
        advanced.setCdnRedirectRepoConfig(cdnRedirectRepoConfig);
        model.setTypeSpecific(typeSpecificConfigModel);
        model.setAdvanced(advanced);
        model.setGeneral(general);
        model.setBasic(basic);

        return model;
    }

    private FederatedRepositoryConfigModel prepareFederatedRepoModel() {
        final FederatedRepositoryConfigModel model = new FederatedRepositoryConfigModel();
        final FederatedRepoAdvancedRepositoryConfigModel advanced = new FederatedRepoAdvancedRepositoryConfigModel();
        final GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        final LocalBasicRepositoryConfigModel basic = new LocalBasicRepositoryConfigModel();
        fillBasic(basic);
        general.setRepoKey("repo");
        model.setFederation(new FederationConfigModel());
        model.setTypeSpecific(typeSpecificConfigModel);
        model.setAdvanced(advanced);
        model.setGeneral(general);
        model.setBasic(basic);
        return model;
    }

    private RemoteRepositoryConfigModel prepareRemoteModel() {
        final RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        CdnRedirectRepoConfigModel cdnRedirectRepoConfig = new CdnRedirectRepoConfigModel();
        cdnRedirectRepoConfig.setEnabled(true);
        final RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        advanced.setCache(new RemoteCacheRepositoryConfigModel());
        advanced.setNetwork(new RemoteNetworkRepositoryConfigModel());
        final GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        final RemoteBasicRepositoryConfigModel basic = new RemoteBasicRepositoryConfigModel();
        fillBasic(basic);
        general.setRepoKey("repo");
        advanced.setCdnRedirectRepoConfig(cdnRedirectRepoConfig);
        model.setTypeSpecific(typeSpecificConfigModel);
        model.setAdvanced(advanced);
        model.setGeneral(general);
        model.setBasic(basic);
        when(artifactorySystemProperties.getProperty(any(ConstantValues.class)))
                .thenAnswer(invocationOnMock -> {
                    ConstantValues constantValue = invocationOnMock.getArgument(0);
                    if (constantValue == ConstantValues.debianDefaultArchitectures) {
                        return "deb";
                    }
                    return constantValue.getDefValue().trim();
                });

        return model;
    }

    @Test
    void testPopulateRemoteTerraformPackageTypeConfigValues() {
        typeSpecificConfigModel = new TerraformTypeSpecificConfigModel();
        TerraformTypeSpecificConfigModel configModel = (TerraformTypeSpecificConfigModel) typeSpecificConfigModel;
        configModel.setGitProvider(VcsGitProvider.ARTIFACTORY);
        configModel.setEnableExternalDependencies(true);
        configModel.setExternalPatterns(List.of("[\"**/*\"]"));
        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);
        assertExternalDependencyConfig(
                remoteRepoConfig.getPackageTypeConfig(),
                configModel.getEnableExternalDependencies(),
                configModel.getExternalPatterns()
        );
    }

    @Test
    void testPopulateRemoteVSCodePackageTypeConfigValues() {
        typeSpecificConfigModel = new AIEditorExtensionsTypeSpecificConfigModel();
        AIEditorExtensionsTypeSpecificConfigModel configModel =
                (AIEditorExtensionsTypeSpecificConfigModel) typeSpecificConfigModel;
        configModel.setEnableExternalDependencies(true);
        configModel.setExternalPatterns(List.of("[\"**/*\"]"));
        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);
        assertExternalDependencyConfig(remoteRepoConfig.getPackageTypeConfig(),
                configModel.getEnableExternalDependencies(), configModel.getExternalPatterns());
    }

    @Test
    void testBuildCocoaPodsRemoteDescriptorWithNonSmartRemote() {
        // Given
        prepareCocoaPodsRemoteModel();
        final RemoteRepositoryConfigModel model = prepareRemoteModel();

        // When
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);

        // Verify Specs URLs
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        assertEquals(packageTypeConfig.get(COCOA_PODS_CDN_URL.getKey()), "http://specs-cdn-url");
        assertEquals(packageTypeConfig.get(COCOA_PODS_SPECS_URL.getKey()), "http://specs-repo-url");

        // Verify VCS configuration for Smart Repo
        assertEquals(VcsType.GIT.name(), packageTypeConfig.get(VCS_TYPE.getKey()));
        assertEquals(VcsGitProvider.GITHUB.name(), packageTypeConfig.get(VCS_GIT_PROVIDER.getKey()));
        assertEquals(VcsGitProvider.GITHUB.name(), packageTypeConfig.get(SPECS_REPO_DOWNLOAD_PROVIDER.getKey()));
        assertEquals(packageTypeConfig.get(VCS_GIT_DOWNLOAD_URL.getKey()), "http://download-url");

        // Verify external dependencies configuration
        assertEquals(packageTypeConfig.get(EXTERNAL_DEPENDENCIES_ENABLED.getKey()), "true");
        List<String> patterns = JsonUtils.getInstance().readValue(
                packageTypeConfig.get(EXTERNAL_DEPENDENCIES_PATTERNS.getKey()),
                new TypeReference<ArrayList<String>>() {
                });
        assertEquals(patterns.size(), 2);
        assertTrue(patterns.contains("**/github.com/**"));
        assertTrue(patterns.contains("**/bitbucket.com/**"));
        assertNull(packageTypeConfig.get(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF.getKey()));

        // Verify CDN redirect configuration
        assertNotNull(remoteRepoConfig.getRepoTypeConfig().getCdnRedirectRepoConfig());
        assertTrue(remoteRepoConfig.getRepoTypeConfig().getCdnRedirectRepoConfig().isEnabled());

        // Verify other settings
        assertEquals(remoteRepoConfig.getRepoTypeConfig().getMaxUniqueSnapshots(), 10);
        assertFalse(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
    }

    @Test
    void testBuildRemoteRepoConfigForCocoaPodsWithSmartRepo() {
        // Given
        prepareCocoaPodsRemoteModel();
        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        model.getBasic().setContentSynchronisation(new ContentSynchronisation());
        model.getBasic().getContentSynchronisation().enable();

        // When
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);

        // Verify Specs URLs
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        assertEquals(packageTypeConfig.get(COCOA_PODS_CDN_URL.getKey()), StringUtils.EMPTY);
        assertEquals(packageTypeConfig.get(COCOA_PODS_SPECS_URL.getKey()), StringUtils.EMPTY);

        // Verify VCS configuration for Smart Repo
        assertEquals(VcsType.GIT.name(), packageTypeConfig.get(VCS_TYPE.getKey()));
        assertEquals(VcsGitProvider.ARTIFACTORY.name(), packageTypeConfig.get(VCS_GIT_PROVIDER.getKey()));
        assertEquals(VcsGitProvider.ARTIFACTORY.name(), packageTypeConfig.get(SPECS_REPO_DOWNLOAD_PROVIDER.getKey()));
        assertEquals(packageTypeConfig.get(VCS_GIT_DOWNLOAD_URL.getKey()), "http://download-url");

        // Verify external dependencies configuration
        assertEquals(packageTypeConfig.get(EXTERNAL_DEPENDENCIES_ENABLED.getKey()), "true");
        List<String> patterns = JsonUtils.getInstance().readValue(
                packageTypeConfig.get(EXTERNAL_DEPENDENCIES_PATTERNS.getKey()),
                new TypeReference<ArrayList<String>>() {
                });
        assertEquals(patterns.size(), 2);
        assertTrue(patterns.contains("**/github.com/**"));
        assertTrue(patterns.contains("**/bitbucket.com/**"));
        assertNull(packageTypeConfig.get(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF.getKey()));

        // Verify CDN redirect configuration
        assertNotNull(remoteRepoConfig.getRepoTypeConfig().getCdnRedirectRepoConfig());
        assertTrue(remoteRepoConfig.getRepoTypeConfig().getCdnRedirectRepoConfig().isEnabled());

        // Verify other settings
        assertEquals(remoteRepoConfig.getRepoTypeConfig().getMaxUniqueSnapshots(), 10);
        assertFalse(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
    }

    @Test
    void buildAlpineRemoteConfigRoundTrip_shouldPreserveListRemoteFolderItems() {
        // Arrange - set listRemoteFolderItems=true in the UI model
        AlpineTypeSpecificConfigModel alpineConfigModel =
                new AlpineTypeSpecificConfigModel();
        alpineConfigModel.setListRemoteFolderItems(true);
        typeSpecificConfigModel = alpineConfigModel;

        // Act - save path: build descriptor from UI model
        RemoteRepoConfig remoteRepoConfig =
                repoConfigBuilder.buildRemoteRepoConfig(prepareRemoteModel());

        // Act - read path: reconstruct UI model from descriptor
        RepoConfigModelBuilder modelBuilder = new RepoConfigModelBuilder();
        AlpineTypeSpecificConfigModel readBackModel =
                (AlpineTypeSpecificConfigModel) modelBuilder
                        .createRemoteTypeSpecific(
                                RepoType.Alpine, remoteRepoConfig);

        // Assert - value survives the round-trip
        Assertions.assertTrue(
                remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        Assertions.assertTrue(readBackModel.isListRemoteFolderItems());
    }

    @Test
    void buildAlpineRemoteConfigListRemoteFolderItemsDefault() {
        // Arrange
        typeSpecificConfigModel = new AlpineTypeSpecificConfigModel();

        // Act
        RemoteRepoConfig remoteRepoConfig =
                repoConfigBuilder.buildRemoteRepoConfig(prepareRemoteModel());

        // Assert
        Assertions.assertFalse(
                remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
    }

    @Test
    void buildAlpineRemoteConfigListRemoteFolderItemsSetToTrue() {
        // Arrange
        AlpineTypeSpecificConfigModel alpineConfigModel =
                new AlpineTypeSpecificConfigModel();
        alpineConfigModel.setListRemoteFolderItems(true);
        typeSpecificConfigModel = alpineConfigModel;

        // Act
        RemoteRepoConfig remoteRepoConfig =
                repoConfigBuilder.buildRemoteRepoConfig(prepareRemoteModel());

        // Assert
        Assertions.assertTrue(
                remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
    }

    @Test
    void testBuildCondaRemoteConfigListRemoteFolderItems() {
        typeSpecificConfigModel = new CondaTypeSpecificConfigModel();
        CondaTypeSpecificConfigModel condaConfigModel = (CondaTypeSpecificConfigModel) typeSpecificConfigModel;
        condaConfigModel.setListRemoteFolderItems(true);

        final RemoteRepositoryConfigModel model = prepareRemoteModel();
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);

        assertTrue(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
    }

    private void assertExternalDependencyConfig(Map<String, String> packageTypeConfig, boolean expectedEnabled,
            List<String> expectedPatterns) {
        assertEquals(String.valueOf(expectedEnabled), packageTypeConfig.get(EXTERNAL_DEPENDENCIES_ENABLED.getKey()));
        assertEquals(JsonUtils.getInstance().readValue(
                packageTypeConfig.get(EXTERNAL_DEPENDENCIES_PATTERNS.getKey()),
                new TypeReference<ArrayList<String>>() {
                }), expectedPatterns);
        assertNull(packageTypeConfig.get(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF.getKey()));
    }

    private static Stream<Arguments> provideExternalDependenciesPatternsForCocoapods() {
        return Stream.of(
                Arguments.of(List.of("")),
                Arguments.of(List.of("[**]")),
                Arguments.of(new ArrayList<>()),
                Arguments.of(List.of("**/github.com/**")),
                Arguments.of(List.of("**/example.com/**", "**/bitbucket.com/**"))
        );
    }

    private void prepareCocoaPodsRemoteModel() {
        typeSpecificConfigModel = new CocoaPodsTypeSpecificConfigModel();
        CocoaPodsTypeSpecificConfigModel configModel = (CocoaPodsTypeSpecificConfigModel) typeSpecificConfigModel;
        configModel.setGitProvider(VcsGitProvider.GITHUB);
        configModel.setSpecsRepoUrl("http://specs-repo-url");
        configModel.setSpecsCdnUrl("http://specs-cdn-url");
        configModel.setEnableExternalDependencies(true);
        configModel.setExternalPatterns(List.of("**/github.com/**", "**/bitbucket.com/**"));
        configModel.setVcsType(VcsType.GIT);
        configModel.setDownloadUrl("http://download-url");
        configModel.setMaxUniqueSnapshots(10);
        configModel.setListRemoteFolderItems(false);
    }

    @Test
    void testBuildAnsibleRemoteConfig() {
        AnsibleTypeSpecificConfigModel ansibleConfig = new AnsibleTypeSpecificConfigModel();
        ansibleConfig.setAuthUrl("https://sso.example.com/auth");
        typeSpecificConfigModel = ansibleConfig;

        RemoteRepositoryConfigModel model = prepareRemoteModel();
        model.getAdvanced().getNetwork().setPassword("my-refresh-token");
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);

        assertEquals("https://sso.example.com/auth",
                remoteRepoConfig.getPackageTypeConfig().get(AUTH_URL.getKey()));
    }

    @Test
    void testBuildAnsibleRemoteConfigRoundTrip() {
        AnsibleTypeSpecificConfigModel ansibleConfig = new AnsibleTypeSpecificConfigModel();
        ansibleConfig.setAuthUrl("https://sso.example.com/auth");
        typeSpecificConfigModel = ansibleConfig;

        RemoteRepositoryConfigModel model = prepareRemoteModel();
        model.getAdvanced().getNetwork().setPassword("my-refresh-token");
        RemoteRepoConfig remoteRepoConfig = repoConfigBuilder.buildRemoteRepoConfig(model);

        RepoConfigModelBuilder modelBuilder = new RepoConfigModelBuilder();
        AnsibleTypeSpecificConfigModel readBackModel = (AnsibleTypeSpecificConfigModel) modelBuilder
                .createRemoteTypeSpecific(RepoType.Ansible, remoteRepoConfig);

        assertEquals("https://sso.example.com/auth", readBackModel.getAuthUrl());
    }

    @Test
    void testBuildAnsibleRemoteConfigAuthUrlWithoutPasswordThrows() {
        AnsibleTypeSpecificConfigModel ansibleConfig = new AnsibleTypeSpecificConfigModel();
        ansibleConfig.setAuthUrl("https://sso.example.com/auth");
        typeSpecificConfigModel = ansibleConfig;

        RemoteRepositoryConfigModel model = prepareRemoteModel();
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> repoConfigBuilder.buildRemoteRepoConfig(model),
                "Ansible remote repository with authUrl configured must also have a password configured");
    }
}