package org.artifactory.descriptor.repo;

import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


class DockerConfigResolverTest extends ArtifactoryHomeBoundJUnitTest {

    private static final int EXPECTED_CLASS_FIELD_COUNT = 4;
    private DockerConfigResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DockerConfigResolver();
        // Reset to default value for clean test state
        setStringSystemProperty(ConstantValues.dockerCompleteManifestOverrideEnabled, "false");
        setStringSystemProperty(ConstantValues.dockerSharedDigestStatsSyncEnabled, "false");
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        setStringSystemProperty(ConstantValues.dockerCompleteManifestOverrideEnabled, "false");
        setStringSystemProperty(ConstantValues.dockerSharedDigestStatsSyncEnabled, "false");
    }

    @Test
    void testResolveCompleteManifestOverride_Enabled_NotUIConfigured_UsesSystemProperty() {
        // Given
        setStringSystemProperty(ConstantValues.dockerCompleteManifestOverrideEnabled, "false");
        DockerGeneralConfig config = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .explicitConfig(false)
                .build(); // Not explicitly configured

        // When
        boolean result = resolver.resolveCompleteManifestOverrideEnabled(config);

        // Then
        assertFalse(result, "Should use system property value when not UI-configured");
    }

    @Test
    void testResolveCompleteManifestOverrideEnabled() {
        // Given
        setStringSystemProperty(ConstantValues.dockerCompleteManifestOverrideEnabled,
                "false"); // System property is false
        DockerGeneralConfig config = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .explicitConfig(true)
                .build(); // explicitly configured with true

        // When
        boolean result = resolver.resolveCompleteManifestOverrideEnabled(config);

        // Then
        assertTrue(result, "Should use stored UI value when UI-configured, ignoring system property");
    }

    @Test
    void testResolveCompleteManifestOverride_Enabled_NullConfig_UsesSystemProperty() {
        // Given
        setStringSystemProperty(ConstantValues.dockerCompleteManifestOverrideEnabled, "false");

        // When
        boolean result = resolver.resolveCompleteManifestOverrideEnabled(null);

        // Then
        assertFalse(result, "Should use system property value when config is null");
    }

    @Test
    void testCreateDisplayOnlyInstance() {
        // When
        DockerGeneralConfig result = resolver.createDockerConfigBySystemProperty();

        // Then
        assertFalse(result.isCompleteListManifestOverwrite(), "Should store the provided value");
        assertFalse(result.isSharedDigestStatsSyncEnabled(),
                "sharedDigestStatsSyncEnabled should mirror system property (false)");
        assertFalse(result.isExplicitConfig(), "Should NOT be marked as UI-configured");
    }

    @Test
    void testCreateDisplayOnlyInstance_SharedDigestFollowsSystemProperty() {
        setStringSystemProperty(ConstantValues.dockerSharedDigestStatsSyncEnabled, "true");

        DockerGeneralConfig result = resolver.createDockerConfigBySystemProperty();

        assertTrue(result.isSharedDigestStatsSyncEnabled(),
                "Display config should expose sharedDigestStatsSyncEnabled from ConstantValues");
    }

    @Test
    void testShouldApplyChange_ValueChanged_ReturnsTrue() {
        // Given
        setStringSystemProperty(ConstantValues.dockerCompleteManifestOverrideEnabled,
                "false"); // System property is false
        DockerGeneralConfig currentConfig = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .explicitConfig(false)
                .build();
        boolean incomingValue = true; // User wants to change to true

        // When
        boolean result = shouldApplyChange(currentConfig, incomingValue);

        // Then
        assertTrue(result, "Should apply change when value differs from effective value");
    }

    @Test
    void testShouldApplyChange_ValueUnchanged_ReturnsFalse() {
        // Given
        DockerGeneralConfig currentConfig = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .explicitConfig(false)
                .build();
        boolean incomingValue = false; // User submits same value as system property

        setStringSystemProperty(ConstantValues.dockerCompleteManifestOverrideEnabled,
                "false"); // System property is false

        // When
        boolean result = shouldApplyChange(currentConfig, incomingValue);

        // Then
        assertFalse(result, "Should NOT apply change when value matches effective value");
    }

    @Test
    void testShouldApplyChange_UIConfiguredValueChanged_ReturnsTrue() {
        // Given
        DockerGeneralConfig currentConfig = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .explicitConfig(true)
                .build();
        boolean incomingValue = false; // User wants to change to false

        // When
        boolean result = shouldApplyChange(currentConfig, incomingValue);

        // Then
        assertTrue(result, "Should apply change when UI value changes");
    }

    @Test
    void testShouldApplyChange_UIConfiguredValueUnchanged_ReturnsFalse() {
        // Given
        DockerGeneralConfig currentConfig = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .explicitConfig(true)
                .build();
        boolean incomingValue = true; // User submits same value

        // When
        boolean result = shouldApplyChange(currentConfig, incomingValue);

        // Then
        assertFalse(result, "Should NOT apply change when UI value unchanged");
    }

    private boolean shouldApplyChange(DockerGeneralConfig currentConfig, boolean incomingValue) {
        boolean currentEffectiveValue = resolver.resolveCompleteManifestOverrideEnabled(currentConfig);
        return currentEffectiveValue != incomingValue;
    }

    @Test
    void testResolveEnforceStrictTagOverwrite_nullConfig_returnsDefaultFalse() {
        assertFalse(resolver.resolveEnforceStrictTagOverwrite(null),
                "Should return default false when config is null");
    }

    @Test
    void testResolveEnforceStrictTagOverwrite_explicitConfig_usesConfigValue() {
        DockerGeneralConfig config = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .enforceStrictTagOverwrite(true)
                .explicitConfig(true)
                .build();
        assertTrue(resolver.resolveEnforceStrictTagOverwrite(config),
                "Should use config value when explicitly configured");
    }

    @Test
    void testResolveEnforceStrictTagOverwrite_notExplicit_returnsDefaultFalse() {
        DockerGeneralConfig config = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .enforceStrictTagOverwrite(true)
                .explicitConfig(false)
                .build();
        assertFalse(resolver.resolveEnforceStrictTagOverwrite(config),
                "Should return default false when not explicitly configured");
    }

    @Test
    void testResolveSharedDigestStatsSyncEnabled_NullConfig_UsesSystemProperty() {
        setStringSystemProperty(ConstantValues.dockerSharedDigestStatsSyncEnabled, "false");

        assertFalse(resolver.resolveSharedDigestStatsSyncEnabled(null),
                "Should use system property when config is null");
    }

    @Test
    void testResolveSharedDigestStatsSyncEnabled_NullConfig_SystemPropertyTrue() {
        setStringSystemProperty(ConstantValues.dockerSharedDigestStatsSyncEnabled, "true");

        assertTrue(resolver.resolveSharedDigestStatsSyncEnabled(null),
                "Should use system property when config is null");
    }

    @Test
    void testResolveSharedDigestStatsSyncEnabled_explicitConfig_usesConfigValue() {
        DockerGeneralConfig config = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .sharedDigestStatsSyncEnabled(true)
                .explicitConfig(true)
                .build();
        assertTrue(resolver.resolveSharedDigestStatsSyncEnabled(config),
                "Should use config value when explicitly configured");
    }

    @Test
    void testResolveSharedDigestStatsSyncEnabled_explicitConfig_ignoresSystemProperty() {
        setStringSystemProperty(ConstantValues.dockerSharedDigestStatsSyncEnabled, "true");
        DockerGeneralConfig config = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .sharedDigestStatsSyncEnabled(false)
                .explicitConfig(true)
                .build();
        assertFalse(resolver.resolveSharedDigestStatsSyncEnabled(config),
                "Should use stored UI value when UI-configured, ignoring system property");
    }

    @Test
    void testResolveSharedDigestStatsSyncEnabled_NotUIConfigured_UsesSystemProperty() {
        setStringSystemProperty(ConstantValues.dockerSharedDigestStatsSyncEnabled, "false");
        DockerGeneralConfig config = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .sharedDigestStatsSyncEnabled(true)
                .explicitConfig(false)
                .build();

        assertFalse(resolver.resolveSharedDigestStatsSyncEnabled(config),
                "Should use system property when not UI-configured");
    }

    @Test
    void testResolveSharedDigestStatsSyncEnabled_NotUIConfigured_SystemPropertyTrue_IgnoresStoredField() {
        setStringSystemProperty(ConstantValues.dockerSharedDigestStatsSyncEnabled, "true");
        DockerGeneralConfig config = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .sharedDigestStatsSyncEnabled(false)
                .explicitConfig(false)
                .build();

        assertTrue(resolver.resolveSharedDigestStatsSyncEnabled(config),
                "Should use system property when not UI-configured, ignoring raw field");
    }

    // --- mergeFromUiRequest ---

    @Test
    void testMergeFromUiRequest_nullIncoming_returnsExisting() {
        DockerGeneralConfig existing = DockerGeneralConfig.builder().explicitConfig(true).build();
        assertSame(existing, resolver.mergeFromUiRequest(null, existing));
    }

    @Test
    void testMergeFromUiRequest_noChanges_returnsExistingUnmodified() {
        DockerGeneralConfig existing = DockerGeneralConfig.builder().explicitConfig(false).build();
        DockerGeneralConfig incoming = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .enforceStrictTagOverwrite(false)
                .sharedDigestStatsSyncEnabled(false)
                .build();
        assertSame(existing, resolver.mergeFromUiRequest(incoming, existing),
                "Should return same instance when nothing changed");
    }

    @Test
    void testMergeFromUiRequest_enforceStrictTagChanged_allFieldsPreservedAndMarkedExplicit() {
        DockerGeneralConfig existing = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .enforceStrictTagOverwrite(false)
                .sharedDigestStatsSyncEnabled(false)
                .explicitConfig(true)
                .build();
        DockerGeneralConfig incoming = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .enforceStrictTagOverwrite(true)
                .sharedDigestStatsSyncEnabled(false)
                .build();

        DockerGeneralConfig result = resolver.mergeFromUiRequest(incoming, existing);

        assertTrue(result.isExplicitConfig(), "Result must be marked as UI-configured");
        assertTrue(result.isEnforceStrictTagOverwrite(), "Changed field must be persisted");
        assertTrue(result.isCompleteListManifestOverwrite(), "Unchanged field must be preserved");
        assertFalse(result.isSharedDigestStatsSyncEnabled(), "Unchanged field must be preserved");
    }

    @Test
    void testMergeFromUiRequest_allFieldsChanged_allValuesPreserved() {
        DockerGeneralConfig existing = DockerGeneralConfig.builder().explicitConfig(false).build();
        DockerGeneralConfig incoming = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .enforceStrictTagOverwrite(true)
                .sharedDigestStatsSyncEnabled(true)
                .build();

        DockerGeneralConfig result = resolver.mergeFromUiRequest(incoming, existing);

        assertTrue(result.isExplicitConfig());
        assertTrue(result.isCompleteListManifestOverwrite());
        assertTrue(result.isEnforceStrictTagOverwrite());
        assertTrue(result.isSharedDigestStatsSyncEnabled());
    }

    // --- Structural guard ---

    @Test
    void testDockerGeneralConfig_fieldCount_failsIfNewFieldAddedWithoutUpdatingMergeLogic() {
        long fieldCount = Arrays.stream(DockerGeneralConfig.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
                .count();
        assertEquals(EXPECTED_CLASS_FIELD_COUNT, fieldCount,
                "DockerGeneralConfig has a new field. Add a resolve*() method and register it in "
                + "resolveEffective() in DockerConfigResolver, then bump this expected count.");
    }
}
