package org.artifactory.repo;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_METADATA_NAME_VERSION;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_NON_DUPLICATE_CHART;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
class FederatedRepositoryConfigurationImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getFederatedRepoUrlBaseOrDefault()).thenReturn("http://localhost:8081");
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testDefaultType() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("repoKey", RepoType.Generic);
        FederatedRepositoryConfiguration configuration =
                new FederatedRepositoryConfigurationImpl(federatedRepoConfig);
        assertEquals(configuration.getType(), FederatedRepositoryConfigurationImpl.TYPE);
        assertTrue(StringUtils.isBlank(configuration.getProxy()));
    }

    @Test
    void constructor_shouldSetMembers_whenFederatedRepoConfigHasMembers() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("repoKey", RepoType.Generic);
        FederatedMember member1 = new FederatedMember();
        member1.setUrlOrTemplate("http://site1.example.com/artifactory/repoKey");
        member1.setEnabled(true);
        FederatedMember member2 = new FederatedMember();
        member2.setUrlOrTemplate("http://site2.example.com/artifactory/repoKey");
        member2.setEnabled(false);
        federatedRepoConfig.getRepoTypeConfig().setFederatedMembers(List.of(member1, member2));

        FederatedRepositoryConfigurationImpl configuration =
                new FederatedRepositoryConfigurationImpl(federatedRepoConfig);

        assertEquals(2, configuration.getMembers().size());
        assertEquals("http://site1.example.com/artifactory/repoKey", configuration.getMembers().get(0).getUrl());
        assertTrue(configuration.getMembers().get(0).getEnabled());
        assertFalse(configuration.getMembers().get(1).getEnabled());
    }

    @Test
    void testAddingProxy() {
        FederatedRepoDescriptor federatedRepoDescriptor = new FederatedRepoDescriptor();
        federatedRepoDescriptor.setProxy(new ProxyDescriptor());
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("repoKey", RepoType.Generic);
        federatedRepoConfig.getRepoTypeConfig().setProxyRef("key");
        FederatedRepositoryConfigurationImpl configuration =
                new FederatedRepositoryConfigurationImpl(federatedRepoConfig);
        assertEquals(configuration.getType(), FederatedRepositoryConfigurationImpl.TYPE);
        assertEquals(configuration.getProxy(), "key");

        configuration.setProxy("newKey");
        assertEquals(configuration.getProxy(), "newKey");
    }

    @Test
    void testHelmEnforceLayoutAppliedOnFederatedRepo() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("repoKey", RepoType.Helm);
        federatedRepoConfig.getPackageTypeConfig().put(FORCE_METADATA_NAME_VERSION, "true");
        federatedRepoConfig.getPackageTypeConfig().put(FORCE_NON_DUPLICATE_CHART, "true");
        FederatedRepositoryConfigurationImpl configuration =
                new FederatedRepositoryConfigurationImpl(federatedRepoConfig);
        assertTrue(configuration.isForceMetadataNameVersion());
        assertTrue(configuration.isForceNonDuplicateChart());
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void testUseHFIsMLRepoLayout(String boolValue) {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("repoKey", RepoType.HuggingFaceML);
        federatedRepoConfig.getPackageTypeConfig().put(IS_ML_REPO_LAYOUT, boolValue);
        FederatedRepositoryConfigurationImpl configuration =
                new FederatedRepositoryConfigurationImpl(federatedRepoConfig);
        assertEquals(Boolean.parseBoolean(boolValue), configuration.isMlRepoLayout());
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_FailClose() {
        FederatedRepositoryConfigurationImpl config = new FederatedRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("xrayNotAvailableMode", "fail_close");
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertEquals(XrayNotAvailableMode.FAIL_CLOSE.getValue(), config.getXrayNotAvailableMode());
        assertFalse(repoConfig.containsKey("xrayNotAvailableMode"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fail_open", "FAIL_OPEN"})
    void testSetXrayNotAvailableModeFromConfig_FailOpen(String xrayNotAvailableMode) {
        FederatedRepositoryConfigurationImpl config = new FederatedRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("xrayNotAvailableMode", xrayNotAvailableMode);
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertEquals(XrayNotAvailableMode.FAIL_OPEN.getValue(), config.getXrayNotAvailableMode());
        assertFalse(repoConfig.containsKey("xrayNotAvailableMode"));
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_EmptyString() {
        FederatedRepositoryConfigurationImpl config = new FederatedRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("xrayNotAvailableMode", "");
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertEquals(XrayNotAvailableMode.USE_SYSTEM_PROPERTY.getValue(), config.getXrayNotAvailableMode());
        assertFalse(repoConfig.containsKey("xrayNotAvailableMode"));
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_UnknownValue() {
        FederatedRepositoryConfigurationImpl config = new FederatedRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("xrayNotAvailableMode", "unknown");
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertEquals(XrayNotAvailableMode.USE_SYSTEM_PROPERTY.getValue(), config.getXrayNotAvailableMode());
        assertFalse(repoConfig.containsKey("xrayNotAvailableMode"));
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_NoKey() {
        FederatedRepositoryConfigurationImpl config = new FederatedRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertNull(config.getXrayNotAvailableMode());
    }

    @Test
    void setFederatedMembers_shouldDelegateToSetMembers() {
        FederatedRepositoryConfigurationImpl config = new FederatedRepositoryConfigurationImpl();
        List<FederatedMemberConfiguration> members = List.of(
                new FederatedMemberConfiguration("http://site1/repo", null, true),
                new FederatedMemberConfiguration("http://site2/repo", null, true)
        );

        config.setFederatedMembers(members);

        assertEquals(members, config.getMembers());
    }

    @Test
    void setFederatedMembers_shouldBeEquivalentToSetMembers() {
        FederatedRepositoryConfigurationImpl config1 = new FederatedRepositoryConfigurationImpl();
        FederatedRepositoryConfigurationImpl config2 = new FederatedRepositoryConfigurationImpl();
        List<FederatedMemberConfiguration> members = List.of(
                new FederatedMemberConfiguration("http://site1/repo", null, true)
        );

        config1.setMembers(members);
        config2.setFederatedMembers(members);

        assertEquals(config1.getMembers(), config2.getMembers());
    }

    @Test
    void setFederatedMembers_shouldHandleNull() {
        FederatedRepositoryConfigurationImpl config = new FederatedRepositoryConfigurationImpl();

        config.setFederatedMembers(null);

        assertNull(config.getMembers());
    }

    @Test
    void setFederatedMembers_shouldHandleEmptyList() {
        FederatedRepositoryConfigurationImpl config = new FederatedRepositoryConfigurationImpl();

        config.setFederatedMembers(List.of());

        assertTrue(config.getMembers().isEmpty());
    }
}