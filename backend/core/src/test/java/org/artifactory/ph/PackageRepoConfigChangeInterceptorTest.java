package org.artifactory.ph;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.compress.java.util.jar.Pack200.Packer.FALSE;
import static org.apache.commons.compress.java.util.jar.Pack200.Packer.TRUE;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_REGISTRY_URL;
import static org.artifactory.ph.PackageRepoConfigChangeInterceptor.SKIP_NORMALIZATION_OVERRIDE_MARKER;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageRepoConfigChangeInterceptorTest extends ArtifactoryHomeBoundJUnitTest {

    private PackageRepoConfigChangeInterceptor interceptor;

    @Mock
    private RepoConfig oldRepoConfig;

    @Mock
    private RepoConfig newRepoConfig;

    @Mock
    private RepoConfigChange repoConfigChange;

    @BeforeEach
    void setUp() {
        interceptor = new PackageRepoConfigChangeInterceptor();
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    /**
     * Tests the behavior of the onBeforeSave method when the old repo config contains the ENABLE_NORMALIZED_VERSION
     * key.
     * Verifies that the new repo config inherits the ENABLE_NORMALIZED_VERSION value from the old repo config.
     */
    @Test
    void testOnBeforeSave() {
        // Mock package type config maps
        Map<String, String> oldPackageTypeConfig = new HashMap<>();
        oldPackageTypeConfig.put("enableNormalizedVersion", "true");
        Map<String, String> newPackageTypeConfig = new HashMap<>();

        // Setup mocks
        when(repoConfigChange.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_UPDATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldRepoConfig);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.NuGet);
        when(oldRepoConfig.getPackageTypeConfig()).thenReturn(oldPackageTypeConfig);
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);

        // Create input map
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        // Call method
        interceptor.onBeforeSave(mutableRepoConfigChanges);

        // Verify the new config has the old value
        assertEquals("true", newPackageTypeConfig.get("enableNormalizedVersion"));

        // Verify interactions
        // getNewRepoConfig now called 4 times (was 3): the extra call comes from
        // consumeSkipNormalizationOverrideMarker(change), which checks the marker before the
        // override path. newRepoConfig.getPackageTypeConfig() is similarly called twice (was 1)
        // because the marker check reads it once, and the override path reads it once more.
        verify(repoConfigChange).getConfigAction();
        verify(repoConfigChange, times(2)).getOldRepoConfig();
        verify(repoConfigChange, times(4)).getNewRepoConfig();
        verify(oldRepoConfig).getPackageTypeConfig();
        verify(newRepoConfig, times(2)).getPackageTypeConfig();
    }



    /**
     * Tests the behavior of the onBeforeSave method when the RepoConfigChange object is null.
     * Verifies that no exceptions are thrown and no changes are made.
     */
    @Test
    void testOnBeforeSaveNullRepoConfigChange() {
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(null));

        // Call method
        assertDoesNotThrow(() -> interceptor.onBeforeSave(mutableRepoConfigChanges));
    }

    /**
     * Tests the behavior of the onBeforeSave method when the old repo config is of a non-NuGet type.
     * Verifies that the ENABLE_NORMALIZED_VERSION key is not added to the new repo config.
     */
    @Test
    void testOnBeforeSaveNonNuGetRepo() {
        // Setup mocks
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.Maven);
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        // Call method
        interceptor.onBeforeSave(mutableRepoConfigChanges);

        // Verify interactions

        verify(repoConfigChange).getNewRepoConfig();
        verify(newRepoConfig).getPackageType();
        verify(oldRepoConfig, never()).getPackageTypeConfig();
        verify(newRepoConfig, never()).getPackageTypeConfig();
    }

    /**
     * Tests the behavior of the onBeforeSave method when the old repo config is null.
     * Verifies that the ENABLE_NORMALIZED_VERSION key is not added to the new repo config.
     */
    @Test
    void testOnBeforeSaveNullOldRepoConfig() {
        // Setup mocks
        when(repoConfigChange.getNewRepoConfig()).thenReturn(null); // Null oldRepoConfig

        // Create input map
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        // Call method
        interceptor.onBeforeSave(mutableRepoConfigChanges);

        // Verify the new config does not have the old value
        verify(repoConfigChange).getNewRepoConfig();
        verify(newRepoConfig, never()).getPackageTypeConfig();
    }

    /**
     * Tests the behavior of the onBeforeSave method when the new repo config is null.
     * Verifies that no changes are made to the old repo config.
     */
    @Test
    void testOnBeforeSaveNullNewRepoConfig() {
        // Create input map
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        // Call method
        interceptor.onBeforeSave(mutableRepoConfigChanges);

        // Verify no changes are made

        verify(repoConfigChange).getNewRepoConfig();
        verify(oldRepoConfig, never()).getPackageType();
        verify(oldRepoConfig, never()).getPackageTypeConfig();
    }

    /**
     * Tests the behavior of the onBeforeSave method when the config action is not REPO_UPDATE.
     * Verifies that the override method is not called.
     */
    @Test
    void testGetConfigActionCondition() {
        // Setup mocks
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.NuGet);
        when(repoConfigChange.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_CREATE);

        // Create input map
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        // Call method
        interceptor.onBeforeSave(mutableRepoConfigChanges);

        // Verify the condition
        verify(repoConfigChange, never()).getOldRepoConfig();
    }

    /**
     * Tests the behavior of the onBeforeSave method when the old repo config does not contain the
     * ENABLE_NORMALIZED_VERSION key.
     * Verifies that the ENABLE_NORMALIZED_VERSION key is removed from the new repo config.
     */
    @Test
    void testOnBeforeSaveEnableNormalizedVersionNotPresent() {
        // Mock package type config maps
        Map<String, String> oldPackageTypeConfig = new HashMap<>(); // No ENABLE_NORMALIZED_VERSION
        Map<String, String> newPackageTypeConfig = new HashMap<>();
        newPackageTypeConfig.put("enableNormalizedVersion", "true"); // Initially present

        // Setup mocks
        when(repoConfigChange.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_UPDATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldRepoConfig);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.NuGet);
        when(oldRepoConfig.getPackageTypeConfig()).thenReturn(oldPackageTypeConfig);
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);

        // Create input map
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        // Call method
        interceptor.onBeforeSave(mutableRepoConfigChanges);

        // Verify ENABLE_NORMALIZED_VERSION is removed from the new config
        assertNull(newPackageTypeConfig.get("enableNormalizedVersion"));

        // Verify interactions. See note in testOnBeforeSave on the +1 counts caused by
        // consumeSkipNormalizationOverrideMarker.
        verify(repoConfigChange).getConfigAction();
        verify(repoConfigChange, times(2)).getOldRepoConfig();
        verify(repoConfigChange, times(4)).getNewRepoConfig();
        verify(newRepoConfig).getPackageType();
        verify(oldRepoConfig).getPackageTypeConfig();
        verify(newRepoConfig, times(2)).getPackageTypeConfig();
    }
    /**
     * Tests the behavior of the onBeforeSave method when HF repository created.
     * Verifies that the new repo config has the FORCE_FROG_ML_LAYOUT value set in constant values.
     */
    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void huggingFaceRepositoryShouldHaveFrogMLLayoutEnabledAfterCreate(String frogMLEnabledValue) {
        // Mock package type config maps
        Map<String, String> newPackageTypeConfig = new HashMap<>();
        newPackageTypeConfig.put(IS_ML_REPO_LAYOUT, "false");

        // Setup mocks
        when(repoConfigChange.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_CREATE);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.HuggingFaceML);
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);

        // Create input map
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        // init properties
        homeStub.getArtifactoryProperties().setProperty(
                ConstantValues.huggingFaceMLEnableFrogMLLayout.getPropertyName(), frogMLEnabledValue);

        // Call method
        interceptor.onBeforeSave(mutableRepoConfigChanges);

        // Verify the new config has the old value
        assertEquals(frogMLEnabledValue, newPackageTypeConfig.get(IS_ML_REPO_LAYOUT));

        // Verify interactions
        verify(repoConfigChange).getConfigAction();
        verify(repoConfigChange, never()).getOldRepoConfig();
        verify(repoConfigChange, times(3)).getNewRepoConfig();
        verify(oldRepoConfig, never()).getPackageTypeConfig();
        verify(newRepoConfig).getPackageTypeConfig();
    }

    /**
     * Tests the behavior of the onBeforeSave method when HF repository update.
     * Verifies that FORCE_FROG_ML_LAYOUT not updated with new value but exception is thrown.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void huggingFaceRepositoryShouldThrowExceptionDuringLayoutUpdate(boolean configValue) {
        // Mock package type config maps
        Map<String, String> oldPackageTypeConfig = new HashMap<>();
        Map<String, String> newPackageTypeConfig = new HashMap<>();
        oldPackageTypeConfig.put(IS_ML_REPO_LAYOUT, String.valueOf(!configValue));
        newPackageTypeConfig.put(IS_ML_REPO_LAYOUT, String.valueOf(configValue));
        homeStub.getArtifactoryProperties().setProperty(
                ConstantValues.dev.getPropertyName(), FALSE);
        // Setup mocks
        when(repoConfigChange.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_UPDATE);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldRepoConfig);
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);
        when(oldRepoConfig.getPackageTypeConfig()).thenReturn(oldPackageTypeConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.HuggingFaceML);

        // Create input map
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        // Call method
        Assertions.assertThrowsExactly(IllegalArgumentException.class,
                () -> interceptor.onBeforeSave(mutableRepoConfigChanges));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void hfShouldSucceedOnLayoutUpdateWhenDevToggleEnabled(boolean configValue) {
        // Mock package type config maps
        Map<String, String> oldPackageTypeConfig = new HashMap<>();
        Map<String, String> newPackageTypeConfig = new HashMap<>();
        oldPackageTypeConfig.put(IS_ML_REPO_LAYOUT, String.valueOf(!configValue));
        newPackageTypeConfig.put(IS_ML_REPO_LAYOUT, String.valueOf(configValue));
        homeStub.getArtifactoryProperties().setProperty(
                ConstantValues.dev.getPropertyName(), TRUE);
        // Setup mocks
        when(repoConfigChange.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_UPDATE);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldRepoConfig);
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);
        when(oldRepoConfig.getPackageTypeConfig()).thenReturn(oldPackageTypeConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.HuggingFaceML);

        // Create input map
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        // Call method
        assertDoesNotThrow(() -> interceptor.onBeforeSave(mutableRepoConfigChanges));

    }

    /**
     * Tests the behavior of the onBeforeSave method when HF repository update.
     * Verifies that no exception thrown when layout stays the same.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void huggingFaceRepositoryShouldThrowNothingWhenLayoutStaysSame(boolean configValue) {
        // Mock package type config maps
        Map<String, String> oldPackageTypeConfig = new HashMap<>();
        Map<String, String> newPackageTypeConfig = new HashMap<>();
        final String stringConfigValue = String.valueOf(configValue);
        oldPackageTypeConfig.put(IS_ML_REPO_LAYOUT, stringConfigValue);
        newPackageTypeConfig.put(IS_ML_REPO_LAYOUT, stringConfigValue);

        // Setup mocks
        when(repoConfigChange.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_UPDATE);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldRepoConfig);
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);
        when(oldRepoConfig.getPackageTypeConfig()).thenReturn(oldPackageTypeConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.HuggingFaceML);

        // Create input map
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        // Call method
        interceptor.onBeforeSave(mutableRepoConfigChanges);

        // Verify the new config has the old value
        assertEquals(stringConfigValue, newPackageTypeConfig.get(IS_ML_REPO_LAYOUT));

        // Verify interactions
        verify(repoConfigChange, times(2)).getConfigAction();
        verify(repoConfigChange, times(2)).getOldRepoConfig();
        verify(repoConfigChange, times(3)).getNewRepoConfig();
        verify(oldRepoConfig, times(1)).getPackageTypeConfig();
        verify(newRepoConfig, times(2)).getPackageTypeConfig();
    }

    /**
     * Tests the behavior of the handleTerraformConfigurationChange method.
     * Verifies that bypassHeadRequests is set to true for specific registry URLs.
     */
    @ParameterizedTest
    @ValueSource(strings = {"https://registry.terraform.io", "https://registry.opentofu.org", "https://tf.app.wiz.io"})
    void testHandleTerraformConfigurationChange(String registryUrl) {
        Map<String, String> newPackageTypeConfig = new HashMap<>();
        newPackageTypeConfig.put(TERRAFORM_REGISTRY_URL.getKey(), registryUrl);
        RemoteRepositoryRepoTypeConfig remoteRepoConfig = new RemoteRepositoryRepoTypeConfig();

        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);
        when(newRepoConfig.getRepoTypeConfig()).thenReturn(remoteRepoConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.Terraform);

        interceptor.onBeforeSave(Map.of(RepoDetailsType.REMOTE, List.of(repoConfigChange)));

        assertEquals(true, remoteRepoConfig.isBypassHeadRequests());
    }

    @Test
    void testHandleTerraformConfigurationChangeOnUnOfficialRemote() {
        Map<String, String> newPackageTypeConfig = new HashMap<>();
        newPackageTypeConfig.put(TERRAFORM_REGISTRY_URL.getKey(), "https://someremote.com");

        RemoteRepositoryRepoTypeConfig remoteRepoConfig = new RemoteRepositoryRepoTypeConfig();

        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.Terraform);

        interceptor.onBeforeSave(Map.of(RepoDetailsType.REMOTE, List.of(repoConfigChange)));

        assertEquals(false, remoteRepoConfig.isBypassHeadRequests());
    }

    /**
     * Creates and returns the standard changes map.
     * @param changeMock mock of repoConfigChange to be inserted into the map.
     */
    private Map<RepoDetailsType, List<RepoConfigChange>> createRemoteChangesMap(RepoConfigChange changeMock) {
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new EnumMap<>(RepoDetailsType.class);
        mutableRepoConfigChanges.put(RepoDetailsType.REMOTE, Collections.singletonList(changeMock));
        return mutableRepoConfigChanges;
    }

    /**
     * Sets up the repoConfigChangeMock with the action and configurations.
     *
     * @param repoConfigChangeMock  The mock of repoConfigChange.
     * @param newConfigMockToReturn The mock of newRepoConfig to be returned.
     * @param oldConfigMockToReturn The mock of oldRepoConfig to be returned (can be null).
     */
    private void setupRepoConfigChange(RepoConfigChange repoConfigChangeMock, RepoConfig newConfigMockToReturn,
            RepoConfig oldConfigMockToReturn) {
        when(repoConfigChangeMock.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_UPDATE);
        when(repoConfigChangeMock.getNewRepoConfig()).thenReturn(newConfigMockToReturn);
        when(repoConfigChangeMock.getOldRepoConfig()).thenReturn(oldConfigMockToReturn);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.AIEditorExtensions);
    }

    @Test
    void testVsCodeRepoBypassHeadRequestsIsForcedTrueOnCreate() {
        RemoteRepositoryRepoTypeConfig remoteRepoConfig = new RemoteRepositoryRepoTypeConfig();
        remoteRepoConfig.setBypassHeadRequests(false);
        when(repoConfigChange.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_CREATE);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(newRepoConfig.getRepoTypeConfig()).thenReturn(remoteRepoConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.AIEditorExtensions);

        interceptor.onBeforeSave(createRemoteChangesMap(repoConfigChange));
        assertTrue(remoteRepoConfig.isBypassHeadRequests());
    }

    @ParameterizedTest
    @CsvSource({"true,true,false", "false,true,false", "true,false,true", "false,false,false"})
    void testVsCodeRepoUpdateBypassHeadRequests(boolean oldValue, boolean newValue, boolean shouldThrow) {
        RemoteRepositoryRepoTypeConfig oldRemoteRepoConfig = new RemoteRepositoryRepoTypeConfig();
        oldRemoteRepoConfig.setBypassHeadRequests(oldValue);
        RemoteRepositoryRepoTypeConfig remoteRepoConfig = new RemoteRepositoryRepoTypeConfig();
        remoteRepoConfig.setBypassHeadRequests(newValue);
        setupRepoConfigChange(repoConfigChange , newRepoConfig, oldRepoConfig);
        when(newRepoConfig.getRepoTypeConfig()).thenReturn(remoteRepoConfig);
        when(oldRepoConfig.getRepoTypeConfig()).thenReturn(oldRemoteRepoConfig);

        Map<RepoDetailsType, List<RepoConfigChange>> remoteChangesMap = createRemoteChangesMap(repoConfigChange);
        if (shouldThrow) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> {
                interceptor.onBeforeSave(remoteChangesMap);
            });
        } else {
            assertDoesNotThrow(() -> interceptor.onBeforeSave(remoteChangesMap));
        }
        assertTrue(remoteRepoConfig.isBypassHeadRequests());
    }

    @Test
    void testVsCodeRepoUpdateBypassHeadRequestsNullOldRepoConfig() {
        setupRepoConfigChange(repoConfigChange , newRepoConfig, null);
        when(newRepoConfig.getRepoTypeConfig()).thenReturn(new RemoteRepositoryRepoTypeConfig());
        assertDoesNotThrow(() -> interceptor.onBeforeSave(createRemoteChangesMap(repoConfigChange)));
    }

    @Test
    void testVsCodeRepoBypassHeadRequestsOnNonRelevantAction() {
        RemoteRepositoryRepoTypeConfig remoteRepoConfig = new RemoteRepositoryRepoTypeConfig();
        remoteRepoConfig.setBypassHeadRequests(false);
        when(repoConfigChange.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_DELETE);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(newRepoConfig.getRepoTypeConfig()).thenReturn(remoteRepoConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.AIEditorExtensions);
        assertDoesNotThrow(() -> interceptor.onBeforeSave(createRemoteChangesMap(repoConfigChange)));
    }

    @Test
    void testVsCodeRepoUpdateBypassHeadRequestsNullOldValue() {
        setupRepoConfigChange(repoConfigChange , newRepoConfig, oldRepoConfig);
        when(newRepoConfig.getRepoTypeConfig()).thenReturn(new RemoteRepositoryRepoTypeConfig());
        when(oldRepoConfig.getRepoTypeConfig()).thenReturn(null);
        assertDoesNotThrow(() -> interceptor.onBeforeSave(createRemoteChangesMap(repoConfigChange)));
    }

    /**
     * When the caller writes the {@link PackageRepoConfigChangeInterceptor#SKIP_NORMALIZATION_OVERRIDE_MARKER}
     * into {@code packageTypeConfig} alongside the real {@code enableNormalizedVersion} value, the override
     * must be skipped AND the marker must be stripped from the map so it never reaches the database.
     */
    @Test
    void nugetUpdateBypassesOverrideAndStripsMarkerWhenSkipMarkerIsSet() {
        Map<String, String> oldPackageTypeConfig = new HashMap<>(); // old has nothing
        Map<String, String> newPackageTypeConfig = new HashMap<>();
        newPackageTypeConfig.put("enableNormalizedVersion", "true");
        newPackageTypeConfig.put(SKIP_NORMALIZATION_OVERRIDE_MARKER, "true");

        when(repoConfigChange.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_UPDATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldRepoConfig);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.NuGet);
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);

        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        interceptor.onBeforeSave(mutableRepoConfigChanges);

        // Real value preserved - override was skipped.
        assertEquals("true", newPackageTypeConfig.get("enableNormalizedVersion"));
        // Marker stripped - it must not leak to the DB.
        assertFalse(newPackageTypeConfig.containsKey(SKIP_NORMALIZATION_OVERRIDE_MARKER));
        // The override path was not taken: oldRepoConfig's package-type config is never read.
        verify(oldRepoConfig, never()).getPackageTypeConfig();
    }

    /**
     * The marker is only honored when its value is exactly {@code "true"}. Any other value must
     * be treated as an absent marker (and is still stripped to avoid leaking junk to the DB).
     */
    @Test
    void nugetUpdateAppliesOverrideWhenMarkerValueIsNotTrue() {
        Map<String, String> oldPackageTypeConfig = new HashMap<>();
        oldPackageTypeConfig.put("enableNormalizedVersion", "true");
        Map<String, String> newPackageTypeConfig = new HashMap<>();
        newPackageTypeConfig.put(SKIP_NORMALIZATION_OVERRIDE_MARKER, "false"); // not "true"

        when(repoConfigChange.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_UPDATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldRepoConfig);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.NuGet);
        when(oldRepoConfig.getPackageTypeConfig()).thenReturn(oldPackageTypeConfig);
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);

        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        interceptor.onBeforeSave(mutableRepoConfigChanges);

        // Override fired: old value carried over.
        assertEquals("true", newPackageTypeConfig.get("enableNormalizedVersion"));
        // Marker still consumed (removed) so it doesn't leak.
        assertFalse(newPackageTypeConfig.containsKey(SKIP_NORMALIZATION_OVERRIDE_MARKER));
    }

    /**
     * If the marker is absent altogether, the original override behavior is preserved bit-for-bit.
     * This is the default user-driven update path.
     */
    @Test
    void nugetUpdateAppliesOverrideWhenMarkerAbsent() {
        Map<String, String> oldPackageTypeConfig = new HashMap<>();
        oldPackageTypeConfig.put("enableNormalizedVersion", "true");
        Map<String, String> newPackageTypeConfig = new HashMap<>();

        when(repoConfigChange.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_UPDATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldRepoConfig);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newRepoConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.NuGet);
        when(oldRepoConfig.getPackageTypeConfig()).thenReturn(oldPackageTypeConfig);
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);

        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = new HashMap<>();
        mutableRepoConfigChanges.put(RepoDetailsType.LOCAL, Collections.singletonList(repoConfigChange));

        interceptor.onBeforeSave(mutableRepoConfigChanges);

        // Override fired: old value carried over to new.
        assertEquals("true", newPackageTypeConfig.get("enableNormalizedVersion"));
    }
}