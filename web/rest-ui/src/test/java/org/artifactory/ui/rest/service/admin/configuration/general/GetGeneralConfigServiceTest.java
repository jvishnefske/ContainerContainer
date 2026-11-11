package org.artifactory.ui.rest.service.admin.configuration.general;

import java.util.stream.Stream;
import java.util.function.BiFunction;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.addon.AddonSettings;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.distribution.ReleaseBundlesConfig;
import org.artifactory.descriptor.download.DownloadRedirectConfigDescriptor;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.replication.GlobalReplicationsConfigDescriptor;
import org.artifactory.descriptor.repo.DockerGeneralConfig;
import org.artifactory.descriptor.repo.DockerConfigResolver;
import org.artifactory.descriptor.repo.HuggingFaceMlGeneralConfig;
import org.artifactory.descriptor.repo.PypiGeneralConfig;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.descriptor.repo.RpmEnableRecommendTagsConfig;
import org.artifactory.descriptor.repo.RpmEnableSha256Config;
import org.artifactory.descriptor.repo.RpmGeneralConfig;
import org.artifactory.descriptor.rlm.RlmConfig;
import org.artifactory.descriptor.signature.SignedUrlConfig;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.platform.PlatformGeneralConfig;
import org.artifactory.platform.PlatformSystemMessageConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.configuration.generalconfig.BasicGeneralConfig;
import org.artifactory.ui.rest.model.admin.configuration.generalconfig.GeneralConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetGeneralConfigServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private GetGeneralConfigService getGeneralConfigService;
    private AutoCloseable autoCloseable;
    @Mock
    CentralConfigService centralConfigService;
    @Mock
    SecurityService securityService;
    @Mock
    ArtifactoryApplicationContext applicationContext;
    @Mock
    AddonsManager addonsManager;
    @Mock
    RepositoryService repositoryService;
    @Mock
    PlatformConfigService platformConfigService;

    @BeforeEach
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        getGeneralConfigService = Mockito.spy(new GetGeneralConfigService());
        getGeneralConfigService.setCentralConfigService(centralConfigService);
        getGeneralConfigService.setSecurityService(securityService);
        getGeneralConfigService.setRepositoryService(repositoryService);
        getGeneralConfigService.setDockerConfigResolver(createDockerConfigMock());
        createConfigMock();
        when(applicationContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(applicationContext.beanForType(PlatformConfigService.class)).thenReturn(platformConfigService);
        when(platformConfigService.getPlatformGeneralConfig()).thenReturn(PlatformGeneralConfig.builder().build());
        when(platformConfigService.getPlatformSystemMessageConfig()).thenReturn(
                PlatformSystemMessageConfig.builder().build());
        ArtifactoryContextThreadBinder.bind(applicationContext);
    }

    private DockerConfigResolver createDockerConfigMock() {
        DockerConfigResolver dockerConfigResolver = Mockito.mock(DockerConfigResolver.class);
        Mockito.when(dockerConfigResolver.resolveCompleteManifestOverrideEnabled(Mockito.any()))
                .thenReturn(false);
        Mockito.when(dockerConfigResolver.createDockerConfigBySystemProperty())
                .thenReturn(DockerGeneralConfig.builder()
                        .completeListManifestOverwrite(false)
                        .explicitConfig(false)
                        .build());
        return dockerConfigResolver;
    }

    @Test
    public void testProjectAdmin() {
        Mockito.when(securityService.isAdmin()).thenReturn(false);
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        getGeneralConfigService.execute(restRequest, response);
        Object result = response.getIModel();
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof BasicGeneralConfig);
    }

    @Test
    public void testSuperAdmin() {
        Mockito.when(securityService.isAdmin()).thenReturn(true);
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        getGeneralConfigService.execute(restRequest, response);
        Object result = response.getIModel();
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof GeneralConfig);
    }

    @ParameterizedTest
    @MethodSource("applyRpmConfigFilteringData")
    public void testApplyRpmConfigFiltering(RpmGeneralConfig input, RpmGeneralConfig expected) {
        // Prepare GeneralConfig with real repository config descriptor
        GeneralConfig generalConfig = new GeneralConfig();
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        repoConfig.setRpm(input);
        generalConfig.setRepositoriesConfig(repoConfig);

        // Call the method under test
        getGeneralConfigService.applyRpmConfigFiltering(generalConfig);

        RpmGeneralConfig actual = generalConfig.getRepositoriesConfig().getRpm();
        Assertions.assertEquals(expected, actual);
    }

    /**
     * Provides diverse combinations of visibility/enabled flags for both SHA256 and RecommendTags, ensuring that
     * the applyRpmConfigFiltering method forces the flags to be visible when the corresponding system properties are
     * NOT set (the default case in tests).
     */
    private static Stream<Arguments> applyRpmConfigFilteringData() {
        // Helper to create a config pair (input, expectedAfterFilter)
        BiFunction<Boolean, Boolean, RpmEnableSha256Config> sha256Config =
                (visible, enabled) -> new RpmEnableSha256Config(visible, enabled);
        BiFunction<Boolean, Boolean, RpmEnableRecommendTagsConfig> recommendConfig =
                (visible, enabled) -> new RpmEnableRecommendTagsConfig(visible, enabled);

        // Case 1: both originally visible, enabled = true
        Arguments c1 = buildArgs(sha256Config.apply(true, true), recommendConfig.apply(true, true));

        // Case 2: SHA256 invisible but enabled, RecommendTags visible enabled
        Arguments c2 = buildArgs(sha256Config.apply(false, true), recommendConfig.apply(true, true));

        // Case 3: SHA256 visible disabled, RecommendTags invisible disabled
        Arguments c3 = buildArgs(sha256Config.apply(true, false), recommendConfig.apply(false, false));

        // Case 4: both invisible & disabled
        Arguments c4 = buildArgs(sha256Config.apply(false, false), recommendConfig.apply(false, false));

        return Stream.of(c1, c2, c3, c4);
    }

    private static Arguments buildArgs(RpmEnableSha256Config sha256Input, RpmEnableRecommendTagsConfig recommendInput) {
        // Input config (will be mutated by the filter)
        RpmGeneralConfig input = new RpmGeneralConfig();
        input.setEnableSha256(new RpmEnableSha256Config(sha256Input.isVisibleOnUi(), sha256Input.isEnabled()));
        input.setEnableRecommendTags(new RpmEnableRecommendTagsConfig(recommendInput.isVisibleOnUi(),
                recommendInput.isEnabled()));

        // Expected config – visibility must be TRUE and enabled must remain as the original for each field
        RpmGeneralConfig expected = new RpmGeneralConfig();
        expected.setEnableSha256(new RpmEnableSha256Config(true, sha256Input.isEnabled()));
        expected.setEnableRecommendTags(new RpmEnableRecommendTagsConfig(true, recommendInput.isEnabled()));

        return Arguments.of(input, expected);
    }

    @Test
    public void testApplyDockerConfigFiltering() {
        // Prepare GeneralConfig with Docker configuration
        GeneralConfig generalConfig = new GeneralConfig();
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        DockerGeneralConfig dockerConfig = new DockerGeneralConfig();
        dockerConfig.setCompleteListManifestOverwrite(true);
        repoConfig.setDocker(dockerConfig);
        generalConfig.setRepositoriesConfig(repoConfig);

        // Call the method under test
        getGeneralConfigService.applyDockerConfigFiltering(generalConfig);

        // Verify the Docker config was processed (not null)
        DockerGeneralConfig actual = generalConfig.getRepositoriesConfig().getDocker();
        Assertions.assertNotNull(actual, "Docker config should not be null after filtering");
    }

    @Test
    public void testApplyDockerConfigFilteringWithNullConfig() {
        // Prepare GeneralConfig with null Docker configuration
        GeneralConfig generalConfig = new GeneralConfig();
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        repoConfig.setDocker(null);
        generalConfig.setRepositoriesConfig(repoConfig);

        // Call the method under test
        getGeneralConfigService.applyDockerConfigFiltering(generalConfig);

        // Verify a new Docker config was created
        DockerGeneralConfig actual = generalConfig.getRepositoriesConfig().getDocker();
        Assertions.assertNotNull(actual, "Docker config should be created when null");
        Assertions.assertFalse(actual.isCompleteListManifestOverwrite(), 
                "Default value should be false");
    }

    private void createConfigMock() {
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        AddonSettings addonSettings = Mockito.mock(AddonSettings.class);
        Mockito.when(configDescriptor.getAddons()).thenReturn(addonSettings);
        FolderDownloadConfigDescriptor folderDownloadDescriptor = Mockito.mock(FolderDownloadConfigDescriptor.class);
        Mockito.when(configDescriptor.getFolderDownloadConfig()).thenReturn(folderDownloadDescriptor);
        TrashcanConfigDescriptor trashcanConfigDescriptor = Mockito.mock(TrashcanConfigDescriptor.class);
        Mockito.when(configDescriptor.getTrashcanConfig()).thenReturn(trashcanConfigDescriptor);
        GlobalReplicationsConfigDescriptor replicationsConfig = Mockito.mock(GlobalReplicationsConfigDescriptor.class);
        Mockito.when(configDescriptor.getReplicationsConfig()).thenReturn(replicationsConfig);
        SumoLogicConfigDescriptor sumoLogicConfig = Mockito.mock(SumoLogicConfigDescriptor.class);
        Mockito.when(configDescriptor.getSumoLogicConfig()).thenReturn(sumoLogicConfig);
        ReleaseBundlesConfig releaseBundlesConfig = Mockito.mock(ReleaseBundlesConfig.class);
        Mockito.when(configDescriptor.getReleaseBundlesConfig()).thenReturn(releaseBundlesConfig);
        RlmConfig rlmConfig = Mockito.mock(RlmConfig.class);
        Mockito.when(configDescriptor.getRlmConfig()).thenReturn(rlmConfig);
        SignedUrlConfig signedUrlConfig = Mockito.mock(SignedUrlConfig.class);
        Mockito.when(configDescriptor.getSignedUrlConfig()).thenReturn(signedUrlConfig);
        DownloadRedirectConfigDescriptor downloadRedirectConfig = Mockito.mock(DownloadRedirectConfigDescriptor.class);
        Mockito.when(configDescriptor.getDownloadRedirectConfig()).thenReturn(downloadRedirectConfig);
        when(configDescriptor.getSubscriptionConfig()).thenReturn(null);
        Mockito.when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
    }

    @Test
    public void testApplyHuggingFaceConfigFiltering_whenConfigIsNull() {
        GeneralConfig generalConfig = new GeneralConfig();
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        repoConfig.setHuggingfaceml(null);
        generalConfig.setRepositoriesConfig(repoConfig);

        getGeneralConfigService.applyHuggingFaceConfigFiltering(generalConfig);

        HuggingFaceMlGeneralConfig actual = generalConfig.getRepositoriesConfig().getHuggingfaceml();
        Assertions.assertNotNull(actual, "HuggingFace config should be created when null");
    }

    @Test
    public void testApplyHuggingFaceConfigFiltering_whenConfigIsNotExplicit() {
        GeneralConfig generalConfig = new GeneralConfig();
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        HuggingFaceMlGeneralConfig hfConfig = new HuggingFaceMlGeneralConfig(true, false);
        repoConfig.setHuggingfaceml(hfConfig);
        generalConfig.setRepositoriesConfig(repoConfig);

        getGeneralConfigService.applyHuggingFaceConfigFiltering(generalConfig);

        HuggingFaceMlGeneralConfig actual = generalConfig.getRepositoriesConfig().getHuggingfaceml();
        Assertions.assertNotNull(actual, "HuggingFace config should not be null after filtering");
    }

    @Test
    public void testApplyHuggingFaceConfigFiltering_whenConfigIsExplicit() {
        GeneralConfig generalConfig = new GeneralConfig();
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        HuggingFaceMlGeneralConfig hfConfig = new HuggingFaceMlGeneralConfig(true, true);
        repoConfig.setHuggingfaceml(hfConfig);
        generalConfig.setRepositoriesConfig(repoConfig);

        getGeneralConfigService.applyHuggingFaceConfigFiltering(generalConfig);

        HuggingFaceMlGeneralConfig actual = generalConfig.getRepositoriesConfig().getHuggingfaceml();
        Assertions.assertNotNull(actual, "HuggingFace config should not be null after filtering");
        Assertions.assertTrue(actual.isXetEnabled(), "Explicit enabled value should be surfaced");
        Assertions.assertTrue(actual.isXetEnabledExplicit(), "Explicit flag should be preserved");
    }

    @Test
    public void testApplyPypiConfigFiltering_whenConfigIsNull() {
        GeneralConfig generalConfig = new GeneralConfig();
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        repoConfig.setPypi(null);
        generalConfig.setRepositoriesConfig(repoConfig);

        getGeneralConfigService.applyPypiConfigFiltering(generalConfig);

        PypiGeneralConfig actual = generalConfig.getRepositoriesConfig().getPypi();
        Assertions.assertNotNull(actual, "PyPI config should be created when null");
    }

    @Test
    public void testApplyPypiConfigFiltering_whenConfigIsNotExplicit() {
        GeneralConfig generalConfig = new GeneralConfig();
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        PypiGeneralConfig pypiConfig = new PypiGeneralConfig();
        pypiConfig.setSimpleJsonFormatEnabled(true);
        pypiConfig.setSimpleJsonFormatEnabledExplicit(false);
        repoConfig.setPypi(pypiConfig);
        generalConfig.setRepositoriesConfig(repoConfig);

        getGeneralConfigService.applyPypiConfigFiltering(generalConfig);

        PypiGeneralConfig actual = generalConfig.getRepositoriesConfig().getPypi();
        Assertions.assertNotNull(actual, "PyPI config should not be null after filtering");
        Assertions.assertFalse(actual.isSimpleJsonFormatEnabledExplicit(),
                "simpleJsonFormatEnabledExplicit should be false when not explicit");
    }

    @Test
    public void testApplyPypiConfigFiltering_whenConfigIsExplicit() {
        GeneralConfig generalConfig = new GeneralConfig();
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        PypiGeneralConfig pypiConfig = new PypiGeneralConfig();
        pypiConfig.setSimpleJsonFormatEnabled(true);
        pypiConfig.setSimpleJsonFormatEnabledExplicit(true);
        repoConfig.setPypi(pypiConfig);
        generalConfig.setRepositoriesConfig(repoConfig);

        getGeneralConfigService.applyPypiConfigFiltering(generalConfig);

        PypiGeneralConfig actual = generalConfig.getRepositoriesConfig().getPypi();
        Assertions.assertNotNull(actual, "PyPI config should not be null after filtering");
        Assertions.assertSame(pypiConfig, actual, "PyPI config should remain unchanged when explicit");
        Assertions.assertTrue(actual.isSimpleJsonFormatEnabledExplicit(),
                "simpleJsonFormatEnabledExplicit should be true when explicit");
        Assertions.assertTrue(actual.isSimpleJsonFormatEnabled(), "simpleJsonFormatEnabled should be true");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (autoCloseable != null) {
            autoCloseable.close();
            autoCloseable = null;
        }
        ArtifactoryContextThreadBinder.unbind();
    }
}