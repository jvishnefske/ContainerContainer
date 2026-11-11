package org.artifactory.repo;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.SnapshotVersionBehaviorDescriptor;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.descriptor.repo.XrayRepoConfigDescriptor.DEFAULT_DATA_TTL;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_METADATA_NAME_VERSION;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_NON_DUPLICATE_CHART;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;
import static org.artifactory.util.PackageTypeConfigFields.DDEB_SUPPORTED;
import static org.artifactory.util.PackageTypeConfigFields.DEBIAN_TRIVIAL_LAYOUT;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_NUGET_NORMALIZED_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.OPTIONAL_INDEX_COMPRESSION_FORMATS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Alexei Vainshtein
 */
class LocalRepositoryConfigurationImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void testDefaultType() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        LocalRepositoryConfigurationImpl configuration = new LocalRepositoryConfigurationImpl(localRepoConfig);
        assertEquals(configuration.getType(), LocalRepositoryConfigurationImpl.TYPE);
        assertEquals(configuration.getSnapshotVersionBehavior(),
                SnapshotVersionBehaviorDescriptor.UNIQUE.getDisplayName().toLowerCase());
    }

    @Test
    void testTerraformBackendType() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.TerraformBackend);
        LocalRepositoryConfigurationImpl configuration = new LocalRepositoryConfigurationImpl(localRepoConfig);
        Assertions.assertTrue(configuration.isEncryptStates());
    }

    @Test
    void testHelmType_defaultValues() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Helm);
        LocalRepositoryConfigurationImpl configuration = new LocalRepositoryConfigurationImpl(localRepoConfig);
        assertFalse(configuration.isForceMetadataNameVersion());
        assertFalse(configuration.isForceNonDuplicateChart());
    }

    @Test
    void testHelmType() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Helm);
        localRepoConfig.getPackageTypeConfig().put(FORCE_NON_DUPLICATE_CHART, "true");
        localRepoConfig.getPackageTypeConfig().put(FORCE_METADATA_NAME_VERSION, "true");
        LocalRepositoryConfigurationImpl configuration = new LocalRepositoryConfigurationImpl(localRepoConfig);
        Assertions.assertTrue(configuration.isForceMetadataNameVersion());
        Assertions.assertTrue(configuration.isForceNonDuplicateChart());
    }

    @Test
    void testDebianType() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Debian);
        localRepoConfig.getPackageTypeConfig()
                .put(OPTIONAL_INDEX_COMPRESSION_FORMATS.getKey(), "[\"format-1\", \"format-2\"]");
        localRepoConfig.getPackageTypeConfig().put(DDEB_SUPPORTED.getKey(), "true");
        localRepoConfig.getPackageTypeConfig().put(DEBIAN_TRIVIAL_LAYOUT.getKey(), "true");
        LocalRepositoryConfigurationImpl configuration = new LocalRepositoryConfigurationImpl(localRepoConfig);
        Assertions.assertTrue(configuration.isDdebSupported());
        Assertions.assertTrue(configuration.isDebianTrivialLayout());
        assertEquals(List.of("format-1", "format-2"), configuration.getOptionalIndexCompressionFormats());
    }

    /**
     * To test package type specific config enableNormalizedVersion getting added for nuget package type
     */
    @Test
    void testNugetType() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.NuGet);
        localRepoConfig.getPackageTypeConfig()
                .put(ENABLE_NUGET_NORMALIZED_VERSION.getKey(), "true");

        LocalRepositoryConfigurationImpl configuration = new LocalRepositoryConfigurationImpl(localRepoConfig);

        Assert.assertTrue(configuration.isEnableNormalizedVersion());
    }

    /**
     * To test package type specific config enableNormalizedVersion should return false when it is not present.
     */
    @Test
    void testNugetTypeWhenEnableNormalizedVersionFlagIsNotPresent() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.NuGet);

        LocalRepositoryConfigurationImpl configuration = new LocalRepositoryConfigurationImpl(localRepoConfig);

        assertFalse(configuration.isEnableNormalizedVersion());
    }

    /**
     * To test package type specific config isMlRepoLayout getting added for huggingfaceml package type
     */
    @Test
    void isMLRepoLayoutAppliedForHuggingFace() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.HuggingFaceML);
        localRepoConfig.getPackageTypeConfig().put(IS_ML_REPO_LAYOUT, "true");

        LocalRepositoryConfigurationImpl configuration = new LocalRepositoryConfigurationImpl(localRepoConfig);
        Assert.assertTrue(configuration.isMlRepoLayout());
    }

    /**
     * To test package type specific config enableNormalizedVersion should return false when it is not present.
     */
    @Test
    void isMLRepoLayoutDisabledByDefault() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.HuggingFaceML);

        LocalRepositoryConfigurationImpl configuration = new LocalRepositoryConfigurationImpl(localRepoConfig);

        assertFalse(configuration.isMlRepoLayout());
    }

    /**
     * A brand-new {@link LocalRepositoryConfigurationImpl} (e.g. a create/update request that never touches
     * Cargo-specific fields) must report the sparse HTTP index as enabled - not the legacy Git index - matching
     * {@link org.artifactory.repo.packagetype.configuration.CargoConfiguration}'s own default.
     */
    @Test
    void isCargoInternalIndexDefaultsToTrueWhenUnset() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();

        Assertions.assertTrue(config.isCargoInternalIndex());
    }

    @Test
    void isCargoInternalIndexReflectsExplicitlySetValue() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();

        config.setCargoInternalIndex(false);

        assertFalse(config.isCargoInternalIndex());
    }

    @Test
    void isCargoInternalIndexReflectsExplicitlySetTrueValue() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();

        config.setCargoInternalIndex(true);

        Assertions.assertTrue(config.isCargoInternalIndex());
    }

    @Test
    void testSetXrayDataTtl() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();
        assertEquals(DEFAULT_DATA_TTL, config.getXrayDataTtl());
        config.setXrayDataTtl(10);
        assertEquals(10, config.getXrayDataTtl());
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_FailClose() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("xrayNotAvailableMode", "fail_close");
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertEquals(XrayNotAvailableMode.FAIL_CLOSE.getValue(), config.getXrayNotAvailableMode());
        assertFalse(repoConfig.containsKey("xrayNotAvailableMode"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fail_open", "FAIL_OPEN"})
    void testSetXrayNotAvailableModeFromConfig_FailOpen(String xrayNotAvailableMode) {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("xrayNotAvailableMode", xrayNotAvailableMode);
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertEquals(XrayNotAvailableMode.FAIL_OPEN.getValue(), config.getXrayNotAvailableMode());
        assertFalse(repoConfig.containsKey("xrayNotAvailableMode"));
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_EmptyString() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("xrayNotAvailableMode", "");
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertEquals(XrayNotAvailableMode.USE_SYSTEM_PROPERTY.getValue(), config.getXrayNotAvailableMode());
        assertFalse(repoConfig.containsKey("xrayNotAvailableMode"));
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_UnknownValue() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("xrayNotAvailableMode", "unknown");
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertEquals(XrayNotAvailableMode.USE_SYSTEM_PROPERTY.getValue(), config.getXrayNotAvailableMode());
        assertFalse(repoConfig.containsKey("xrayNotAvailableMode"));
    }

    @Test
    void testSetXrayNotAvailableModeFromConfig_NoKey() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();
        Map<String, Object> repoConfig = new HashMap<>();
        config.setXrayNotAvailableModeFromConfig(repoConfig);
        assertNull(config.getXrayNotAvailableMode());
    }

    // ========================================================================================
    // Federation Fields Tests
    // ========================================================================================

    @Test
    void setFederatedMembers_shouldSetFederatedMemberConfigurations() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();
        List<FederatedMemberConfiguration> members = List.of(
                new FederatedMemberConfiguration("http://site1/repo", null, true),
                new FederatedMemberConfiguration("http://site2/repo", null, false)
        );

        config.setFederatedMembers(members);

        assertEquals(members, config.getFederatedMemberConfigurations());
    }

    @Test
    void setFederatedMembers_shouldHandleNull() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();

        config.setFederatedMembers(null);

        assertNull(config.getFederatedMemberConfigurations());
    }

    @Test
    void setProxy_shouldSetProxyValue() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();

        config.setProxy("my-proxy");

        assertEquals("my-proxy", config.getProxy());
    }

    @Test
    void setDisableProxy_shouldSetDisableProxyValue() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();

        config.setDisableProxy(true);

        Assertions.assertTrue(config.isDisableProxy());
    }

    @Test
    void federationFields_shouldDefaultToNull() {
        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl();

        assertNull(config.getFederatedMemberConfigurations());
        assertNull(config.getProxy());
        assertFalse(config.isDisableProxy());
    }

    @Test
    void constructor_shouldSetGridTopologyId_whenPresentInRepoConfig() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("test-repo", RepoType.Generic);
        localRepoConfig.getBaseConfig().setGridTopologyId("topology-123");

        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl(localRepoConfig);

        assertEquals("topology-123", config.getGridTopologyId());
    }

    @Test
    void constructor_shouldNotSetGridTopologyId_whenBlankInRepoConfig() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("test-repo", RepoType.Generic);
        localRepoConfig.getBaseConfig().setGridTopologyId("");

        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl(localRepoConfig);

        assertNull(config.getGridTopologyId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void constructor_federationOnGrid_reflectsValueSetInFederationConfig(boolean federationOnGrid) {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("test-repo", RepoType.Generic);
        localRepoConfig.getBaseConfig().getFederationConfig().setFederationOnGrid(federationOnGrid);

        LocalRepositoryConfigurationImpl config = new LocalRepositoryConfigurationImpl(localRepoConfig);

        assertEquals(federationOnGrid, config.isFederationOnGrid());
    }
}
