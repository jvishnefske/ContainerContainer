package org.artifactory.descriptor.repo;

import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.annotations.Test;

import java.lang.reflect.Field;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class DockerGeneralConfigTest extends ArtifactoryHomeBoundTest {

    /**
     * Test utility method to set fields directly via reflection without triggering UI-configured flag.
     */
    private void setFieldDirectly(DockerGeneralConfig config, String fieldName, boolean value) {
        try {
            Field field = DockerGeneralConfig.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(config, value);
        } catch (Exception e) {
            fail("Failed to set field " + fieldName + ": " + e.getMessage());
        }
    }

    @Test
    public void testDefaultValues() {
        DockerGeneralConfig config = new DockerGeneralConfig();

        // Should return raw field value (false by default)
        assertFalse(config.isCompleteListManifestOverwrite(),
                "Default should return false (raw field value)");
        assertFalse(config.isEnforceStrictTagOverwrite(),
                "Default enforceStrictTagOverwrite should be false");
        assertFalse(config.isSharedDigestStatsSyncEnabled(),
                "Default sharedDigestStatsSyncEnabled should be false");
        assertFalse(config.isExplicitConfig(),
                "Default config should not be marked as explicitly configured");
    }

    @Test
    public void testLombokGeneratedSetters() {
        DockerGeneralConfig config = new DockerGeneralConfig();

        // Initially not configured via UI
        assertFalse(config.isExplicitConfig(), "Should not be explicitly configured initially");

        // Test setting via Lombok-generated setter
        config.setCompleteListManifestOverwrite(true);
        assertTrue(config.isCompleteListManifestOverwrite(),
                "completeListManifestOverwrite should be true after setting");
        // Note: Lombok setter doesn't mark as UI-configured - that's handled by the service layer
        assertFalse(config.isExplicitConfig(),
                "Lombok setter should NOT mark as explicitly configured");

        // Test setting explicitConfig separately
        // simulate marking as explicitly configured
        setFieldDirectly(config, "explicitConfig", true);
        assertTrue(config.isExplicitConfig(),
                "Should be marked as explicitly configured after explicit setting");
    }

    @Test
    public void testUIConfigurationTracking() {
        DockerGeneralConfig config = new DockerGeneralConfig();

        // Initially not configured
        assertFalse(config.isExplicitConfig(), "Should not be explicitly configured initially");

        // Manually set UI-configured flag (service layer responsibility)
        config.setExplicitConfig(true);
        assertTrue(config.isExplicitConfig(), "Should be UI-configured after explicit setting");

        // Test that it persists
        config.setCompleteListManifestOverwrite(true);
        assertTrue(config.isExplicitConfig(), "Should remain UI-configured after value change");
    }

    @Test
    public void testEqualsAndHashCode() {
        DockerGeneralConfig config1 = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .explicitConfig(true)
                .build();
        DockerGeneralConfig config2 = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .explicitConfig(true)
                .build();
        DockerGeneralConfig config3 = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .explicitConfig(false)
                .build();

        assertEquals(config1, config2, "Configs with same values should be equal");
        assertEquals(config1.hashCode(), config2.hashCode(), "Hash codes should be equal for equal objects");

        assertNotEquals(config1, config3, "Configs with different values should not be equal");
        assertNotEquals(config1.hashCode(), config3.hashCode(), "Hash codes should be different for different objects");
    }

    @Test
    public void testDirectFieldAccessVsSetterBehavior() {
        DockerGeneralConfig config = new DockerGeneralConfig();

        // Initially not configured via UI
        assertFalse(config.isExplicitConfig(), "Should not be UI-configured initially");

        // Test direct field setting - should not affect UI-configured flag
        setFieldDirectly(config, "completeListManifestOverwrite", true);
        assertTrue(config.isCompleteListManifestOverwrite(),
                "completeListManifestOverwrite should be true after direct field setting");
        assertFalse(config.isExplicitConfig(),
                "Should NOT be marked as explicitly configured after direct field setting");

        // Test that Lombok setter doesn't change UI-configured flag
        config.setCompleteListManifestOverwrite(false);
        assertFalse(config.isCompleteListManifestOverwrite(),
                "completeListManifestOverwrite should be false after Lombok setter");
        assertFalse(config.isExplicitConfig(),
                "Should still NOT be marked as explicitly configured after Lombok setter");
    }

    @Test
    public void testBuilder() {
        // Test builder with completeListManifestOverwrite and explicitConfig
        DockerGeneralConfig config = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .explicitConfig(true)
                .build();
        assertTrue(config.isCompleteListManifestOverwrite(),
                "Should return true when created with true");
        assertTrue(config.isExplicitConfig(),
                "Should be marked as explicitly configured when created with true");
        assertFalse(config.isEnforceStrictTagOverwrite(),
                "Builder without enforceStrictTagOverwrite should default to false");
        assertFalse(config.isSharedDigestStatsSyncEnabled(),
                "Builder without sharedDigestStatsSyncEnabled should default to false");

        // Test with false values
        DockerGeneralConfig configFalse = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .explicitConfig(false)
                .build();
        assertFalse(configFalse.isCompleteListManifestOverwrite(),
                "Should return false when created with false");
        assertFalse(configFalse.isExplicitConfig(),
                "Should not be marked as explicitly configured when created with false");

        // Test builder with enforceStrictTagOverwrite
        DockerGeneralConfig configWithOverride = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .enforceStrictTagOverwrite(true)
                .explicitConfig(true)
                .build();
        assertTrue(configWithOverride.isEnforceStrictTagOverwrite(),
                "Should return true when created with enforceStrictTagOverwrite true");
        assertTrue(configWithOverride.isExplicitConfig(),
                "Should be marked as explicitly configured");

        // Test builder with sharedDigestStatsSyncEnabled
        DockerGeneralConfig configWithSharedDigestStats = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .sharedDigestStatsSyncEnabled(true)
                .explicitConfig(true)
                .build();
        assertTrue(configWithSharedDigestStats.isSharedDigestStatsSyncEnabled(),
                "Should return true when created with sharedDigestStatsSyncEnabled true");
        assertTrue(configWithSharedDigestStats.isExplicitConfig(),
                "Should be marked as explicitly configured");
    }
}
