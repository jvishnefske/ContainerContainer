package org.artifactory.repo;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.remote.CustomHttpHeader;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.stream.Stream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import static org.artifactory.descriptor.repo.XrayRepoConfigDescriptor.DEFAULT_DATA_TTL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_METADATA_RETRIEVAL_TIMEOUT_SECS;
import static org.artifactory.util.PackageTypeConfigFields.AUTH_URL;

import static org.artifactory.util.PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY;
import static org.artifactory.util.PackageTypeConfigFields.HUGGINGFACE_ML_REPO_LAYOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Alexei Vainshtein
 */
class HttpRepositoryConfigurationImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    public void testSetMetadataRetrievalTimeoutSecs() {
        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl();
        httpRepoDescriptor.setMetadataRetrievalTimeoutSecs(20);
        assertEquals(httpRepoDescriptor.getMetadataRetrievalTimeoutSecs(), 20);
    }

    @Test
    public void testDefaultValues() {
        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl();
        assertEquals(httpRepoDescriptor.getMetadataRetrievalTimeoutSecs(), DEFAULT_METADATA_RETRIEVAL_TIMEOUT_SECS);

        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost");
        remoteRepoConfig.getRepoTypeConfig().setMetadataRetrievalTimeoutSecs(20);

        httpRepoDescriptor = new HttpRepositoryConfigurationImpl(remoteRepoConfig);
        assertEquals(httpRepoDescriptor.getMetadataRetrievalTimeoutSecs(),
                remoteRepoConfig.getRepoTypeConfig().getMetadataRetrievalTimeoutSecs());
    }

    /**
     * A brand-new {@link HttpRepositoryConfigurationImpl} (e.g. a create/update request that never touches
     * Cargo-specific fields) must report the sparse HTTP index as enabled - not the legacy Git index - matching
     * {@link org.artifactory.repo.packagetype.configuration.CargoConfiguration}'s own default.
     */
    @Test
    public void isCargoInternalIndexDefaultsToTrueWhenUnset() {
        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl();

        assertTrue(httpRepoDescriptor.isCargoInternalIndex());
    }

    @Test
    public void isCargoInternalIndexReflectsExplicitlySetValue() {
        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl();

        httpRepoDescriptor.setCargoInternalIndex(false);

        assertFalse(httpRepoDescriptor.isCargoInternalIndex());
    }

    @Test
    public void isCargoInternalIndexReflectsExplicitlySetTrueValue() {
        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl();

        httpRepoDescriptor.setCargoInternalIndex(true);

        assertTrue(httpRepoDescriptor.isCargoInternalIndex());
    }

    @Test
    public void testSetHexFieldsDefault() {
        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl();
        assertEquals(httpRepoDescriptor.getHexPublicKey(), HEX_REGISTRY_PUBLIC_KEY.getDefaultValue());
    }

    @Test
    public void testSetHexFields() {
        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl();
        httpRepoDescriptor.setHexPublicKey("dummykey");
        assertEquals(httpRepoDescriptor.getHexPublicKey(), "dummykey");
    }

    @Test
    public void testHuggingFaceRepoLayoutDefaultValueWithRepoConfig() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.HuggingFaceML);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost");
        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl(remoteRepoConfig);

        assertEquals(httpRepoDescriptor.isMlRepoLayout(),
                Boolean.valueOf(HUGGINGFACE_ML_REPO_LAYOUT.getDefaultValue()));
    }

    @Test
    public void testSetHuggingFaceRepoLayoutWithRepoConfig() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.HuggingFaceML);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost");
        remoteRepoConfig.getPackageTypeConfig().put("isMlRepoLayout", "false");
        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl(remoteRepoConfig);

        httpRepoDescriptor.setMlRepoLayout(true);
        assertTrue(httpRepoDescriptor.isMlRepoLayout());
    }

    @Test
    public void testHuggingFaceRepoLayoutDefaultValueWithoutRepoConfig() {
        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl();

        assertEquals(httpRepoDescriptor.isMlRepoLayout(),
                Boolean.valueOf(HUGGINGFACE_ML_REPO_LAYOUT.getDefaultValue()));
    }

    @Test
    public void testSetHuggingFaceRepoLayoutWithoutRepoConfig() {
        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl();

        httpRepoDescriptor.setMlRepoLayout(true);
        assertTrue(httpRepoDescriptor.isMlRepoLayout());
    }

    @Test
    public void testNugetTypeSpecificConfigWhenNormalizationFlagIsPresent() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.NuGet);
        remoteRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost");
        remoteRepoConfig.getRepoTypeConfig().setMetadataRetrievalTimeoutSecs(20);

        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl(remoteRepoConfig);
        assertTrue(httpRepoDescriptor.isEnableNormalizedVersion());
    }

    @Test
    public void testNugetTypeSpecificConfigWhenNormalizationFlagIsNotPresent() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.NuGet);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost");
        remoteRepoConfig.getRepoTypeConfig().setMetadataRetrievalTimeoutSecs(20);

        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl(remoteRepoConfig);
        assertFalse(httpRepoDescriptor.isEnableNormalizedVersion());
    }

    @Test
    void testSetXrayDataTtl() {
        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl();
        assertEquals(DEFAULT_DATA_TTL, config.getXrayDataTtl());
        config.setXrayDataTtl(10);
        assertEquals(10, config.getXrayDataTtl());
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_FailClose() {
        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("xrayNotAvailableMode", "fail_close");
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertEquals(XrayNotAvailableMode.FAIL_CLOSE.getValue(), config.getXrayNotAvailableMode());
        assertFalse(repoConfig.containsKey("xrayNotAvailableMode"));
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_FailOpen() {
        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("xrayNotAvailableMode", "fail_open");
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertEquals(XrayNotAvailableMode.FAIL_OPEN.getValue(), config.getXrayNotAvailableMode());
        assertFalse(repoConfig.containsKey("xrayNotAvailableMode"));
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_EmptyString() {
        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("xrayNotAvailableMode", "");
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertEquals(XrayNotAvailableMode.USE_SYSTEM_PROPERTY.getValue(), config.getXrayNotAvailableMode());
        assertFalse(repoConfig.containsKey("xrayNotAvailableMode"));
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_UnknownValue() {
        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("xrayNotAvailableMode", "unknown");
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertEquals(XrayNotAvailableMode.USE_SYSTEM_PROPERTY.getValue(), config.getXrayNotAvailableMode());
        assertFalse(repoConfig.containsKey("xrayNotAvailableMode"));
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_NoKey() {
        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertNull(config.getXrayNotAvailableMode());
    }

    @Test
    void testWhenXrayConfigIsNotNull() {
        HttpRepositoryConfigurationImpl configuration = new HttpRepositoryConfigurationImpl();
        configuration.setXrayIndex(true);
        assertNull(configuration.getXrayNotAvailableMode());
    }

    @Test
    void testSetFederationConfig_shouldSetMembers_whenFederationMembersExist() {
        homeStub.setProperty(ConstantValues.remoteRepoFederationEnabled, "true");
        // Arrange - mock ContextHelper for FederatedMemberUtils.resolveUrl()
        ArtifactoryContext mockContext = mock(ArtifactoryContext.class);
        CentralConfigService mockConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor mockDescriptor = mock(CentralConfigDescriptor.class);
        when(mockContext.getCentralConfig()).thenReturn(mockConfigService);
        when(mockConfigService.getDescriptor()).thenReturn(mockDescriptor);
        when(mockDescriptor.getFederatedRepoUrlBaseOrDefault()).thenReturn("http://localhost:8080/artifactory");

        try (MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
            contextHelperMock.when(ContextHelper::get).thenReturn(mockContext);

            RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
            remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost");

            FederatedMember member = new FederatedMember();
            member.setUrlOrTemplate("http://federated-server/repo");
            member.setMode("READ_WRITE");
            member.setEnabled(true);
            remoteRepoConfig.getBaseConfig().getFederationConfig().setMembers(List.of(member));

            // Act
            HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl(remoteRepoConfig);

            // Assert
            List<FederatedMemberConfiguration> members = httpRepoDescriptor.getMembers();
            assertEquals(1, members.size());
            assertEquals("READ_WRITE", members.get(0).getMode());
            assertTrue(members.get(0).getEnabled());
        }
    }

    @Test
    void testSetFederationConfig_shouldNotSetMembers_whenFederationMembersEmpty() {
        // Arrange
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost");
        // Federation config exists but members list is empty (default)

        // Act
        HttpRepositoryConfigurationImpl httpRepoDescriptor = new HttpRepositoryConfigurationImpl(remoteRepoConfig);

        // Assert
        assertNull(httpRepoDescriptor.getMembers());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void constructor_federationOnGrid_reflectsValueSetInFederationConfig(boolean federationOnGrid) {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost");
        remoteRepoConfig.getBaseConfig().getFederationConfig().setFederationOnGrid(federationOnGrid);

        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl(remoteRepoConfig);

        assertEquals(federationOnGrid, config.isFederationOnGrid());
    }

    @Test
    public void testAnsibleDefaultValues() {
        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl();
        assertEquals("", config.getAuthUrl());
    }

    @Test
    public void testAnsibleFieldsFromRepoConfig() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Ansible);
        remoteRepoConfig.getRepoTypeConfig().setUrl("https://galaxy.ansible.com");
        remoteRepoConfig.getPackageTypeConfig().put(AUTH_URL.getKey(), "https://sso.example.com/auth");

        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl(remoteRepoConfig);
        assertEquals("https://sso.example.com/auth", config.getAuthUrl());
    }

    @Test
    public void testAnsibleFieldsDefaultWhenNotInRepoConfig() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Ansible);
        remoteRepoConfig.getRepoTypeConfig().setUrl("https://galaxy.ansible.com");

        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl(remoteRepoConfig);
        assertEquals("", config.getAuthUrl());
    }

    @Test
    public void testSetAnsibleFieldsWithoutRepoConfig() {
        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl();
        config.setAuthUrl("https://sso.example.com/auth");
        assertEquals("https://sso.example.com/auth", config.getAuthUrl());
    }

    @Test
    public void testSetAnsibleFieldsUpdatesPackageTypeConfig() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Ansible);
        remoteRepoConfig.getRepoTypeConfig().setUrl("https://galaxy.ansible.com");
        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl(remoteRepoConfig);

        config.setAuthUrl("https://sso.example.com/auth");
        assertEquals("https://sso.example.com/auth",
                remoteRepoConfig.getPackageTypeConfig().get(AUTH_URL.getKey()));
    }

    @Test
    void getCustomHttpHeaders_shouldReturnEmptyList_whenRepoConfigHasNullHeaders() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost");
        remoteRepoConfig.getRepoTypeConfig().setCustomHttpHeaders(null);

        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl(remoteRepoConfig);

        List<CustomHttpHeader> result = config.getCustomHttpHeaders();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCustomHttpHeaders_shouldReturnEmptyList_whenRepoConfigHasEmptyHeaders() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost");
        remoteRepoConfig.getRepoTypeConfig().setCustomHttpHeaders(Collections.emptyList());

        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl(remoteRepoConfig);

        List<CustomHttpHeader> result = config.getCustomHttpHeaders();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("customHeadersFromRepoConfigProvider")
    void getCustomHttpHeaders_shouldReturnStoredValues_whenBuiltFromRepoConfig(
            List<CustomHttpHeader> storedHeaders, String firstExpectedValue, String secondExpectedValue,
            boolean firstSensitive, boolean secondSensitive) {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost");
        remoteRepoConfig.getRepoTypeConfig().setCustomHttpHeaders(storedHeaders);

        HttpRepositoryConfigurationImpl config = new HttpRepositoryConfigurationImpl(remoteRepoConfig);

        List<CustomHttpHeader> forResponse = config.getCustomHttpHeaders();
        assertEquals(2, forResponse.size());
        assertEquals("x-api-key", forResponse.get(0).getName());
        assertEquals(firstExpectedValue, forResponse.get(0).getValue());
        assertEquals(firstSensitive, forResponse.get(0).isSensitive());
        assertEquals("authorization", forResponse.get(1).getName());
        assertEquals(secondExpectedValue, forResponse.get(1).getValue());
        assertEquals(secondSensitive, forResponse.get(1).isSensitive());
    }

    static Stream<Arguments> customHeadersFromRepoConfigProvider() {
        return Stream.of(
                // Mix: non-sensitive and sensitive; both returned as stored (sensitive may be encrypted at rest)
                Arguments.of(
                        List.of(
                                new CustomHttpHeader("X-Api-Key", "visible-value", false),
                                new CustomHttpHeader("Authorization", "Bearer secret-token", true)),
                        "visible-value", "Bearer secret-token", false, true),
                // All non-sensitive: both values passed
                Arguments.of(
                        List.of(
                                new CustomHttpHeader("X-Api-Key", "v1", false),
                                new CustomHttpHeader("Authorization", "Bearer public", false)),
                        "v1", "Bearer public", false, false),
                // All sensitive: both returned as stored (encrypted at rest in production)
                Arguments.of(
                        List.of(
                                new CustomHttpHeader("X-Api-Key", "secret1", true),
                                new CustomHttpHeader("Authorization", "Bearer secret-token", true)),
                        "secret1", "Bearer secret-token", true, true)
        );
    }
}