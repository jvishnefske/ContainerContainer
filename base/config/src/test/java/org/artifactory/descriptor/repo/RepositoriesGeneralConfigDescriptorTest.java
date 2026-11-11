package org.artifactory.descriptor.repo;

import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Shashank Taliwal
 */
class RepositoriesGeneralConfigDescriptorTest extends ArtifactoryHomeBoundJUnitTest {

    private RepositoriesGeneralConfigDescriptor descriptor;

    /**
     * Test utility method to get field values via reflection.
     */
    private Object getFieldValue(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            fail("Failed to get field " + fieldName + ": " + e.getMessage());
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        descriptor = new RepositoriesGeneralConfigDescriptor();
    }

    @Test
    void testDefaultInitialization() {
        assertNotNull(descriptor.getHelm(), "Helm config should be initialized by default");
        assertNotNull(descriptor.getNuget(), "Nuget config should be initialized by default");
        assertNotNull(descriptor.getRpm(), "RPM config should be initialized by default");
        assertNotNull(descriptor.getDocker(), "Docker config should be initialized by default");
    }

    @Test
    void testHelmConfigDefaults() {
        HelmGeneralConfig helm = descriptor.getHelm();
        
        assertTrue(helm.isForceOciFirstUiFlow(), "Default forceOciFirstUiFlow should be true");
        assertFalse(helm.isForceMetadataNameVersion(), "Default forceMetadataNameVersion should be false");
        assertFalse(helm.isForceNonDuplicateChart(), "Default forceNonDuplicateChart should be false");
    }

    @Test
    void testNugetConfigDefaults() {
        NugetGeneralConfig nuget = descriptor.getNuget();
        
        assertFalse(nuget.isEnableGlobalNormalization(), "Default enableGlobalNormalization should be false");
    }

    @Test
    void testRpmConfigDefaults() {
        RpmGeneralConfig rpm = descriptor.getRpm();
        
        assertNotNull(rpm.getEnableRecommendTags(), "EnableRecommendTags should be initialized");
        assertNotNull(rpm.getEnableSha256(), "EnableSha256 should be initialized");
        assertTrue(rpm.getEnableRecommendTags().isVisibleOnUi(), 
                "Default recommend tags visibleOnUi should be true");
        assertFalse(rpm.getEnableRecommendTags().isEnabled(), 
                "Default recommend tags enabled should be false");
        assertTrue(rpm.getEnableSha256().isVisibleOnUi(), "Default SHA256 visibleOnUi should be true");
        assertFalse(rpm.getEnableSha256().isEnabled(), "Default SHA256 enabled should be false");
    }

    @Test
    void testDockerConfigDefaults() {
        DockerGeneralConfig docker = descriptor.getDocker();

        assertFalse(docker.isCompleteListManifestOverwrite(),
                "Default completeListManifestOverwrite should be false");
        assertFalse(docker.isSharedDigestStatsSyncEnabled(),
                "Default sharedDigestStatsSyncEnabled should be false");
        assertFalse(docker.isExplicitConfig(), "Default explicitConfig should be false");
    }

    @Test
    void testDockerConfigSetttersAndGetters() {
        RepositoriesGeneralConfigDescriptor newDescriptor = new RepositoriesGeneralConfigDescriptor();

        // Test setting non-UI configured Docker config
        DockerGeneralConfig nonUIConfig = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .explicitConfig(false)
                .build();
        newDescriptor.setDocker(nonUIConfig);
        assertSame(nonUIConfig, newDescriptor.getDocker(), "Should store the exact non-UI configured instance");

        // Test with UI-configured instance
        DockerGeneralConfig uiConfig = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .explicitConfig(true)
                .build();
        newDescriptor.setDocker(uiConfig);
        assertSame(uiConfig, newDescriptor.getDocker(), "Should store the exact UI-configured instance");
    }

    @Test
    void testHelmConfigSettersAndGetters() {
        HelmGeneralConfig newHelm = new HelmGeneralConfig();
        newHelm.setForceOciFirstUiFlow(false);
        newHelm.setForceMetadataNameVersion(true);
        newHelm.setForceNonDuplicateChart(true);
        
        descriptor.setHelm(newHelm);
        
        assertEquals(newHelm, descriptor.getHelm(), "Helm config should be set correctly");
        assertFalse(descriptor.getHelm().isForceOciFirstUiFlow(), 
                "Modified forceOciFirstUiFlow should be false");
        assertTrue(descriptor.getHelm().isForceMetadataNameVersion(), 
                "Modified forceMetadataNameVersion should be true");
        assertTrue(descriptor.getHelm().isForceNonDuplicateChart(), 
                "Modified forceNonDuplicateChart should be true");
    }

    @Test
    void testNugetConfigSettersAndGetters() {
        NugetGeneralConfig newNuget = new NugetGeneralConfig();
        newNuget.setEnableGlobalNormalization(true);
        
        descriptor.setNuget(newNuget);
        
        assertEquals(newNuget, descriptor.getNuget(), "Nuget config should be set correctly");
        assertTrue(descriptor.getNuget().isEnableGlobalNormalization(), 
                "Modified enableGlobalNormalization should be true");
    }

    @Test
    void testRpmConfigSettersAndGetters() {
        RpmGeneralConfig newRpm = new RpmGeneralConfig();
        RpmEnableRecommendTagsConfig customRecommendTags = new RpmEnableRecommendTagsConfig();
        customRecommendTags.setVisibleOnUi(false);
        customRecommendTags.setEnabled(true);
        
        RpmEnableSha256Config customSha256 = new RpmEnableSha256Config();
        customSha256.setVisibleOnUi(false);
        customSha256.setEnabled(true);
        
        newRpm.setEnableRecommendTags(customRecommendTags);
        newRpm.setEnableSha256(customSha256);
        
        descriptor.setRpm(newRpm);
        
        assertEquals(newRpm, descriptor.getRpm(), "RPM config should be set correctly");
        assertFalse(descriptor.getRpm().getEnableRecommendTags().isVisibleOnUi(), 
                "Modified recommend tags visibleOnUi should be false");
        assertTrue(descriptor.getRpm().getEnableRecommendTags().isEnabled(), 
                "Modified recommend tags enabled should be true");
        assertFalse(descriptor.getRpm().getEnableSha256().isVisibleOnUi(), 
                "Modified SHA256 visibleOnUi should be false");
        assertTrue(descriptor.getRpm().getEnableSha256().isEnabled(), 
                "Modified SHA256 enabled should be true");
    }

    @Test
    void testDockerConfigSettersAndGetters() {
        DockerGeneralConfig newDocker = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .explicitConfig(true)
                .build();
        newDocker.setCompleteListManifestOverwrite(true);
        
        descriptor.setDocker(newDocker);
        
        assertEquals(newDocker, descriptor.getDocker(), "Docker config should be set correctly");
        assertTrue(descriptor.getDocker().isCompleteListManifestOverwrite(), 
                "Modified completeListManifestOverwrite should be true");
    }

    @Test
    void testIndependentNestedConfigs() {
        descriptor.getHelm().setForceOciFirstUiFlow(false);
        descriptor.getNuget().setEnableGlobalNormalization(true);
        descriptor.getRpm().getEnableRecommendTags().setEnabled(true);
        DockerGeneralConfig dockerCfg = new DockerGeneralConfig();
        descriptor.setDocker(dockerCfg);
        descriptor.getDocker().setCompleteListManifestOverwrite(true);
        
        assertFalse(descriptor.getHelm().isForceOciFirstUiFlow(), 
                "Helm modification should be isolated");
        assertTrue(descriptor.getNuget().isEnableGlobalNormalization(), 
                "Nuget modification should be isolated");
        assertTrue(descriptor.getRpm().getEnableRecommendTags().isEnabled(), 
                "RPM modification should be isolated");
        assertTrue(descriptor.getDocker().isCompleteListManifestOverwrite(), 
                "Docker modification should be isolated");
        assertFalse(descriptor.getHelm().isForceMetadataNameVersion(), 
                "Other Helm values should remain default");
        assertTrue(descriptor.getRpm().getEnableRecommendTags().isVisibleOnUi(), 
                "Other RPM values should remain default");
        assertFalse(descriptor.getRpm().getEnableSha256().isEnabled(), 
                "SHA256 config should remain default");
    }

    @Test
    void testNullSafety() {
        assertDoesNotThrow(() -> descriptor.setHelm(null), "Setting null Helm config should not throw");
        assertDoesNotThrow(() -> descriptor.setNuget(null), "Setting null Nuget config should not throw");
        assertDoesNotThrow(() -> descriptor.setRpm(null), "Setting null RPM config should not throw");
        assertDoesNotThrow(() -> descriptor.setDocker(null), "Setting null Docker config should not throw");
        descriptor.setHelm(null);
        descriptor.setNuget(null);
        descriptor.setRpm(null);
        descriptor.setDocker(null);
    }

    @Test
    void testObjectReferences() {
        HelmGeneralConfig originalHelm = descriptor.getHelm();
        NugetGeneralConfig originalNuget = descriptor.getNuget();
        RpmGeneralConfig originalRpm = descriptor.getRpm();
        DockerGeneralConfig originalDocker = descriptor.getDocker();
        
        assertSame(originalHelm, descriptor.getHelm(), "Multiple Helm getter calls should return same reference");
        assertSame(originalNuget, descriptor.getNuget(), "Multiple Nuget getter calls should return same reference");
        assertSame(originalRpm, descriptor.getRpm(), "Multiple RPM getter calls should return same reference");
        assertSame(originalDocker, descriptor.getDocker(), "Multiple Docker getter calls should return same reference");
        
        HelmGeneralConfig newHelm = new HelmGeneralConfig();
        NugetGeneralConfig newNuget = new NugetGeneralConfig();
        RpmGeneralConfig newRpm = new RpmGeneralConfig();
        DockerGeneralConfig newDocker = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(false)
                .explicitConfig(true)
                .build();
        
        descriptor.setHelm(newHelm);
        descriptor.setNuget(newNuget);
        descriptor.setRpm(newRpm);
        descriptor.setDocker(newDocker);
        
        assertSame(newHelm, descriptor.getHelm(), "Should get new Helm reference after setting");
        assertSame(newNuget, descriptor.getNuget(), "Should get new Nuget reference after setting");
        assertSame(newRpm, descriptor.getRpm(), "Should get new RPM reference after setting");
        assertSame(newDocker, descriptor.getDocker(), "Should get new Docker reference after setting");
        
        assertNotSame(originalHelm, descriptor.getHelm(), "Should not get original Helm reference");
        assertNotSame(originalNuget, descriptor.getNuget(), "Should not get original Nuget reference");
        assertNotSame(originalRpm, descriptor.getRpm(), "Should not get original RPM reference");
        assertNotSame(originalDocker, descriptor.getDocker(), "Should not get original Docker reference");
    }

    @Test
    void testComplexConfigurationScenario() {
        descriptor.getHelm().setForceOciFirstUiFlow(false);
        descriptor.getHelm().setForceMetadataNameVersion(true);
        descriptor.getNuget().setEnableGlobalNormalization(true);
        descriptor.getRpm().getEnableRecommendTags().setVisibleOnUi(false);
        descriptor.getRpm().getEnableRecommendTags().setEnabled(true);
        descriptor.getRpm().getEnableSha256().setVisibleOnUi(true);
        descriptor.getRpm().getEnableSha256().setEnabled(true);
        DockerGeneralConfig dockerCfg = DockerGeneralConfig.builder()
                .completeListManifestOverwrite(true)
                .explicitConfig(true)
                .build();
        descriptor.setDocker(dockerCfg);
        assertFalse(descriptor.getHelm().isForceOciFirstUiFlow());
        assertTrue(descriptor.getHelm().isForceMetadataNameVersion());
        assertFalse(descriptor.getHelm().isForceNonDuplicateChart());
        
        assertTrue(descriptor.getNuget().isEnableGlobalNormalization());
        
        assertFalse(descriptor.getRpm().getEnableRecommendTags().isVisibleOnUi());
        assertTrue(descriptor.getRpm().getEnableRecommendTags().isEnabled());
        assertTrue(descriptor.getRpm().getEnableSha256().isVisibleOnUi());
        assertTrue(descriptor.getRpm().getEnableSha256().isEnabled());
        
        assertTrue(descriptor.getDocker().isCompleteListManifestOverwrite());
    }
} 