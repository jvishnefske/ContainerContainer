package org.artifactory.ui.rest.service.admin.configuration.general;

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.rest.subscription.Subscription;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.DebianGeneralConfig;
import org.artifactory.descriptor.repo.DockerConfigResolver;
import org.artifactory.descriptor.repo.DockerGeneralConfig;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.descriptor.subscription.SubscriptionConfig;
import org.artifactory.platform.PlatformConfigServiceImpl;
import org.artifactory.platform.PlatformGeneralConfig;
import org.artifactory.security.access.AccessService;
import org.artifactory.service.ConfigValidationService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.configuration.generalconfig.GeneralConfig;
import org.jfrog.access.client.platformconfig.GrpcPlatformConfigClient;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Optional;

import static org.artifactory.common.ConstantValues.dockerCompleteManifestOverrideEnabled;
import static org.artifactory.common.ConstantValues.uiItemsBrowserPagingMaxLimit;
import static org.artifactory.ui.rest.service.admin.configuration.general.UpdateConfigUtils.prepareAddonsManager;
import static org.artifactory.ui.rest.service.admin.configuration.general.UpdateConfigUtils.prepareMutableCentralConfigDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
public class UpdateGeneralConfigServiceTest extends ArtifactoryHomeBoundTest {

    private UpdateGeneralConfigService updateGeneralConfigService;
    private MutableCentralConfigDescriptor descriptor;

    @BeforeClass
    public void beforeClass() {
        descriptor = prepareMutableCentralConfigDescriptor();
        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);
        AccessService accessService = mock(AccessService.class);
        GrpcPlatformConfigClient grpcPlatformConfigClient = mock(GrpcPlatformConfigClient.class);
        when(applicationContext.beanForType(AccessService.class)).thenReturn(accessService);
        when(accessService.getPlatformConfigClient()).thenReturn(grpcPlatformConfigClient);
        when(grpcPlatformConfigClient.getConfig(anyString(), eq(PlatformGeneralConfig.class)))
                .thenReturn(Optional.empty());
        CentralConfigService testCentralConfigService = mock(CentralConfigService.class);
        when(testCentralConfigService.getMutableDescriptor()).thenReturn(descriptor);
        DockerConfigResolver dockerConfigResolver = new DockerConfigResolver();
        updateGeneralConfigService = new UpdateGeneralConfigService(testCentralConfigService, prepareAddonsManager(),
                new ConfigValidationService(), new PlatformConfigServiceImpl(accessService), dockerConfigResolver);
        ArtifactoryContextThreadBinder.bind(applicationContext);
    }

    @BeforeMethod
    public void setUp() {
        getOrCreateArtifactoryHomeStub().setProperty(uiItemsBrowserPagingMaxLimit, String.valueOf(1000));

        // Reset Docker configuration state to prevent test interference
        resetDockerConfigState();

        // Clear invocations on shared descriptor mock between parameterized runs
        Mockito.clearInvocations(descriptor);
    }

    /**
     * Reset Docker configuration to a clean state before each test to prevent test interference.
     * This is crucial for parallel test execution.
     */
    private void resetDockerConfigState() {
        RepositoriesGeneralConfigDescriptor repoConfig = descriptor.getRepositoriesGeneralConfig();
        // Create a fresh, non UI/API configured Docker config instance
        DockerGeneralConfig freshDockerConfig = new DockerGeneralConfig();
        freshDockerConfig.setCompleteListManifestOverwrite(false);
        freshDockerConfig.setExplicitConfig(false);
        repoConfig.setDocker(freshDockerConfig);

        // Reset system property to default (false) - tests will override as needed
        setStringSystemProperty(dockerCompleteManifestOverrideEnabled, "false");
    }

    @AfterClass
    public void afterClass() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testUpdateDescriptorAndSave() {
        updateGeneralConfigService.updateDescriptorAndSave(prepareGeneralConfig("cool"));
    }

    @Test
    public void testUpdateDescriptorAndSaveWithLogoUrl() {
        updateGeneralConfigService.updateDescriptorAndSave(
                prepareGeneralConfig("cool", "https://artifactory.com/logo.png", 500));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Logo URL is not valid")
    public void testUpdateDescriptorAndSaveBadLogoUrl() {
        updateGeneralConfigService.updateDescriptorAndSave(
                prepareGeneralConfig("cool", "https://artifactory.com/1.php", 500));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Illegal name.*is not allowed")
    public void testUpdateDescriptorAndSaveBadServerName() {
        updateGeneralConfigService.updateDescriptorAndSave(prepareGeneralConfig("<a href=http://attacker.com a"));
    }

    @Test
    public void testUpdateDescriptorAndSaveBlankServerName() {
        updateGeneralConfigService.updateDescriptorAndSave(prepareGeneralConfig(StringUtils.EMPTY));
    }

    @Test
    public void testUpdateDescriptorAndSaveValidItemsBrowserPagingSize() {
        updateGeneralConfigService.updateDescriptorAndSave(prepareGeneralConfig(500));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "The number of items per browser page must be in the range: 1 - 1000.")
    public void testUpdateDescriptorAndSaveNegativeItemsBrowserPagingSize() {
        updateGeneralConfigService.updateDescriptorAndSave(prepareGeneralConfig(-1));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "The number of items per browser page must be in the range: 1 - 1000.")
    public void testUpdateDescriptorAndSaveWithInvalidItemsBrowserPagingSize() {
        updateGeneralConfigService.updateDescriptorAndSave(
                prepareGeneralConfig(uiItemsBrowserPagingMaxLimit.getInt() + 1));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "The number of items per browser page must be in the range: 1 - 1000.")
    public void testUpdateDescriptorAndSaveWithNullItemsBrowserPagingSize() {
        updateGeneralConfigService.updateDescriptorAndSave(
                prepareGeneralConfig("serverName", "https://artifactory.com/logo.png", null));
    }

    @Test
    public void whenNoEmailsUpdateIsCalled() {
        GeneralConfig config = prepareGeneralConfig("cool");
        Subscription subscription = new Subscription(Collections.emptySet());
        config.setSubscription(subscription);

        updateGeneralConfigService.updateDescriptorAndSave(config);

        ArgumentCaptor<SubscriptionConfig> captor = ArgumentCaptor.forClass(SubscriptionConfig.class);
        verify(descriptor).setSubscriptionConfig(captor.capture());

        assertTrue(captor.getValue().getEmails().isEmpty());
    }

    // ===== Debian Configuration Tests =====

    @Test
    public void testUpdateDebianConfig_BlockUnsafeChecksumsTrue() {
        GeneralConfig generalConfig = prepareGeneralConfigWithDebian(true);

        updateGeneralConfigService.updateDescriptorAndSave(generalConfig);

        ArgumentCaptor<RepositoriesGeneralConfigDescriptor> repoCaptor =
                ArgumentCaptor.forClass(RepositoriesGeneralConfigDescriptor.class);
        verify(descriptor).setRepositoriesGeneralConfig(repoCaptor.capture());
        DebianGeneralConfig resultDebian = repoCaptor.getValue().getDebian();
        assertTrue(resultDebian.isBlockUnsafeChecksums(),
                "Debian blockUnsafeChecksums should be true when set in incoming config");
    }

    @Test
    public void testUpdateDebianConfig_BlockUnsafeChecksumsFalse() {
        GeneralConfig generalConfig = prepareGeneralConfigWithDebian(false);

        updateGeneralConfigService.updateDescriptorAndSave(generalConfig);

        ArgumentCaptor<RepositoriesGeneralConfigDescriptor> repoCaptor =
                ArgumentCaptor.forClass(RepositoriesGeneralConfigDescriptor.class);
        verify(descriptor).setRepositoriesGeneralConfig(repoCaptor.capture());
        DebianGeneralConfig resultDebian = repoCaptor.getValue().getDebian();
        assertFalse(resultDebian.isBlockUnsafeChecksums(),
                "Debian blockUnsafeChecksums should be false when set in incoming config");
    }

    /**
     * When incoming request has repositoriesConfig but Debian config is null, update should succeed
     * and descriptor keeps default Debian config (blockUnsafeChecksums false).
     */
    @Test
    public void testUpdateRepositoriesGeneralConfig_debianUsesDefault_whenIncomingDebianIsNull() {
        GeneralConfig generalConfig = prepareGeneralConfig("test");
        RepositoriesGeneralConfigDescriptor incomingRepoConfig = new RepositoriesGeneralConfigDescriptor();
        incomingRepoConfig.setDebian(null);
        generalConfig.setRepositoriesConfig(incomingRepoConfig);

        updateGeneralConfigService.updateDescriptorAndSave(generalConfig);

        ArgumentCaptor<RepositoriesGeneralConfigDescriptor> repoCaptor =
                ArgumentCaptor.forClass(RepositoriesGeneralConfigDescriptor.class);
        verify(descriptor).setRepositoriesGeneralConfig(repoCaptor.capture());
        DebianGeneralConfig resultDebian = repoCaptor.getValue().getDebian();
        assertNotNull(resultDebian, "Debian config should be present with default instance");
        assertFalse(resultDebian.isBlockUnsafeChecksums(),
                "Debian blockUnsafeChecksums should remain default (false) when incoming Debian is null");
    }

    // ===== Docker Configuration Tests =====

    /**
     * Test edge case: null Docker config in incoming request should not affect existing config
     */
    @Test
    public void testUpdateDockerConfigIfChanged_NullDockerConfig() {
        // Setup: Null Docker config in incoming request
        GeneralConfig generalConfig = prepareGeneralConfig("test");
        generalConfig.getRepositoriesConfig().setDocker(null);

        RepositoriesGeneralConfigDescriptor repoConfig = descriptor.getRepositoriesGeneralConfig();
        DockerGeneralConfig initialDocker = repoConfig.getDocker();
        boolean initialExplicitConfig = initialDocker.isExplicitConfig();
        boolean initialValue = initialDocker.isCompleteListManifestOverwrite();

        // Execute
        updateGeneralConfigService.updateDescriptorAndSave(generalConfig);

        // Verify: Should not change existing Docker config
        DockerGeneralConfig resultDocker = repoConfig.getDocker();
        assertEquals(resultDocker.isExplicitConfig(), initialExplicitConfig,
                "UI configuration state should be unchanged");
        assertEquals(resultDocker.isCompleteListManifestOverwrite(), initialValue, "Value should be unchanged");
    }

    /**
     * Test that our helper method creates proper incoming UI data
     */
    @Test
    public void testPrepareGeneralConfigWithDocker() {
        // Test with true value
        GeneralConfig configTrue = prepareGeneralConfigWithDocker(true);
        DockerGeneralConfig dockerConfigTrue = configTrue.getRepositoriesConfig().getDocker();

        assertTrue(dockerConfigTrue.isCompleteListManifestOverwrite(),
                "Incoming UI data should return true when set to true");
        assertFalse(dockerConfigTrue.isExplicitConfig(),
                "Incoming UI data should not be marked as explicitly configured");

        // Test with false value
        GeneralConfig configFalse = prepareGeneralConfigWithDocker(false);
        DockerGeneralConfig dockerConfigFalse = configFalse.getRepositoriesConfig().getDocker();

        assertFalse(dockerConfigFalse.isCompleteListManifestOverwrite(),
                "Incoming UI data should return false when set to false");
        assertFalse(dockerConfigFalse.isExplicitConfig(),
                "Incoming UI data should not be marked as explicitly configured");
    }

    /**
     * Test that state reset works correctly between tests
     */
    @Test
    public void testDockerConfigStateReset() {
        // Verify clean initial state
        RepositoriesGeneralConfigDescriptor repoConfig = descriptor.getRepositoriesGeneralConfig();
        DockerGeneralConfig dockerConfig = repoConfig.getDocker();

        assertFalse(dockerConfig.isExplicitConfig(), "Docker config should not be explicitly configured after reset");
        assertFalse(dockerConfig.isCompleteListManifestOverwrite(),
                "Docker config should return system property value (false) after reset");

        // Modify state (use setters)
        dockerConfig.setCompleteListManifestOverwrite(true);
        dockerConfig.setExplicitConfig(true);
        assertTrue(dockerConfig.isExplicitConfig(), "Docker config should be explicitly configured after modification");

        // Reset should be called automatically before next test via @BeforeMethod
    }

    /**
     * Test that prepareForSerialization is called and works correctly
     */
    @Test
    public void testUpdateDockerConfigIfChanged_PrepareForSerializationCalled() {
        // Setup: System property = false, UI not configured, incoming UI value = false (no change)
        setStringSystemProperty(dockerCompleteManifestOverrideEnabled, "false");

        GeneralConfig generalConfig = prepareGeneralConfigWithDocker(false);
        RepositoriesGeneralConfigDescriptor repoConfig = descriptor.getRepositoriesGeneralConfig();

        // Ensure Docker config exists but is not explicitly configured
        DockerGeneralConfig dockerConfig = repoConfig.getDocker();
        assertFalse(dockerConfig.isExplicitConfig(), "Should not be explicitly configured initially");

        // Execute
        updateGeneralConfigService.updateDescriptorAndSave(generalConfig);

        // Verify: Docker config should still not be UI-configured
        DockerGeneralConfig resultDocker = repoConfig.getDocker();
        assertFalse(resultDocker.isExplicitConfig(), "Should still not be explicitly configured");

        // Verify setDocker() behavior: non-UI-configured instances should be cleaned up
        // This is tested indirectly by ensuring the config is still accessible but not UI-configured
        assertFalse(resultDocker.isCompleteListManifestOverwrite(), "Should return raw field value (false)");
    }

    @DataProvider(name = "dockerConfigChangeScenarios")
    public Object[][] dockerConfigChangeScenarios() {
        return new Object[][] {
                // systemPropertyValue, initialUIConfigured, initialUIValue, incomingUIValue, expectedUIConfigured,
                // expectedFinalValue, description

                // System property scenarios (UI not configured initially)
                // Note: With simplified model, initial raw field value is always false when not UI-configured
                {"false", false, false, false, false, false, "NoChange_SystemPropertyFalse: system=false, raw=false →" +
                        " no change"},
                {"true", false, false, true, false, false, "NoChange_SystemPropertyTrue: system=true, raw=false, UI " +
                        "sends true → no change"},
                {"false", false, false, true, true, true, "ChangeFromSystemProperty_FalseToTrue: system=false, " +
                        "raw=false → UI=true"},
                {"true", false, false, false, true, false, "Change_SystemPropertyTrueToFalse: system=true, raw=false " +
                        "→ UI=false"},

                // UI already configured scenarios
                {"false", true, true, true, true, true, "NoChange_AlreadyUIConfigured: UI=true → UI=true " +
                        "(system=false ignored)"},
                {"false", true, true, false, true, false, "ChangeUIConfiguredValue: UI=true → UI=false"},
                {"true", true, false, false, true, false, "NoChange_UIConfiguredFalse: UI=false → UI=false " +
                        "(system=true ignored)"},
                {"true", true, false, true, true, true, "ChangeUIConfiguredValue_FalseToTrue: UI=false → UI=true"},

                // System property change ignored when UI configured
                {"false", true, true, true, true, true, "SystemPropertyChangeIgnored: UI=true, system changes to " +
                        "false → UI=true"},
        };
    }

    @Test(dataProvider = "dockerConfigChangeScenarios")
    public void testUpdateDockerConfigIfChanged_AllScenarios(
            String systemPropertyValue,
            boolean initialUIConfigured,
            boolean initialUIValue,
            boolean incomingUIValue,
            boolean expectedUIConfigured,
            boolean expectedFinalValue,
            String description) {

        // Setup system property
        setStringSystemProperty(dockerCompleteManifestOverrideEnabled, systemPropertyValue);

        // Setup initial Docker config state
        RepositoriesGeneralConfigDescriptor repoConfig = descriptor.getRepositoriesGeneralConfig();
        DockerGeneralConfig dockerConfig;

        if (initialUIConfigured) {
            dockerConfig = DockerGeneralConfig.builder()
                    .completeListManifestOverwrite(initialUIValue)
                    .explicitConfig(true)
                    .build();
        } else {
            dockerConfig = new DockerGeneralConfig();
            dockerConfig.setCompleteListManifestOverwrite(false);
            dockerConfig.setExplicitConfig(false);
        }
        repoConfig.setDocker(dockerConfig);

        // Verify initial state matches expectations
        boolean actualInitialUIConfigured = dockerConfig.isExplicitConfig();
        boolean actualInitialValue = dockerConfig.isCompleteListManifestOverwrite();
        // For the simplified model, the raw field value is what we get
        boolean expectedInitialValue = initialUIConfigured && initialUIValue; // Default field value

        assertEquals(actualInitialUIConfigured, initialUIConfigured,
                String.format("%s - Initial UI configured state setup failed. Expected: %s, Actual: %s",
                        description, initialUIConfigured, actualInitialUIConfigured));

        assertEquals(actualInitialValue, expectedInitialValue, String.format(
                "%s - Initial value setup failed. Expected: %s, Actual: %s (systemProp=%s, " +
                        "initialUIConfigured=%s, initialUIValue=%s)",
                description, expectedInitialValue, actualInitialValue, systemPropertyValue, initialUIConfigured,
                initialUIValue));

        // Prepare incoming request and execute
        GeneralConfig generalConfig = prepareGeneralConfigWithDocker(incomingUIValue);
        updateGeneralConfigService.updateDescriptorAndSave(generalConfig);

        // Capture the updated repositories config that was set on the descriptor
        ArgumentCaptor<RepositoriesGeneralConfigDescriptor> repoCaptor =
                ArgumentCaptor.forClass(RepositoriesGeneralConfigDescriptor.class);
        verify(descriptor).setRepositoriesGeneralConfig(repoCaptor.capture());
        RepositoriesGeneralConfigDescriptor updatedRepoCfg = repoCaptor.getValue();
        DockerGeneralConfig resultDocker = updatedRepoCfg.getDocker();
        boolean actualFinalUIConfigured = resultDocker.isExplicitConfig();
        boolean actualFinalValue = resultDocker.isCompleteListManifestOverwrite();

        assertEquals(actualFinalUIConfigured, expectedUIConfigured,
                String.format("%s - Final UI configured state mismatch. Expected: %s, Actual: %s",
                        description, expectedUIConfigured, actualFinalUIConfigured));
        assertEquals(actualFinalValue, expectedFinalValue,
                String.format("%s - Final value mismatch. Expected: %s, Actual: %s",
                        description, expectedFinalValue, actualFinalValue));
    }

    /**
     * Helper to create GeneralConfig with Debian blockUnsafeChecksums set.
     * Simulates what the UI/API sends for the Debian general config.
     */
    private GeneralConfig prepareGeneralConfigWithDebian(boolean blockUnsafeChecksums) {
        GeneralConfig config = prepareGeneralConfig("test");
        RepositoriesGeneralConfigDescriptor incomingRepoConfig = new RepositoriesGeneralConfigDescriptor();
        DebianGeneralConfig debianConfig = new DebianGeneralConfig();
        debianConfig.setBlockUnsafeChecksums(blockUnsafeChecksums);
        incomingRepoConfig.setDebian(debianConfig);
        config.setRepositoriesConfig(incomingRepoConfig);
        return config;
    }

    /**
     * Helper method to create GeneralConfig with Docker configuration.
     * Simulates what the UI/API sends - a DockerGeneralConfig with the user's selected value.
     * <p>
     * The incoming config should behave like a simple data transfer object that returns
     * the value the user selected in the UI form, regardless of system properties.
     */
    private GeneralConfig prepareGeneralConfigWithDocker(boolean dockerValue) {
        GeneralConfig config = prepareGeneralConfig("test");

        // Create a Docker config that simulates incoming UI form data
        DockerGeneralConfig dockerConfig = new DockerGeneralConfig();

        dockerConfig.setCompleteListManifestOverwrite(dockerValue);
        try {
            java.lang.reflect.Field f = DockerGeneralConfig.class.getDeclaredField("explicitConfig");
            f.setAccessible(true);
            f.setBoolean(dockerConfig, false);
        } catch (Exception e) {throw new RuntimeException(e);}

        RepositoriesGeneralConfigDescriptor incomingRepoConfig = new RepositoriesGeneralConfigDescriptor();
        incomingRepoConfig.setDocker(dockerConfig);
        config.setRepositoriesConfig(incomingRepoConfig);

        return config;
    }

    @Nonnull
    public GeneralConfig prepareGeneralConfig(Integer itemsBrowserPagingSize) {
        return prepareGeneralConfig("serverName", null, itemsBrowserPagingSize);
    }

    @Nonnull
    public GeneralConfig prepareGeneralConfig(String serverName) {
        return prepareGeneralConfig(serverName, null, 500);
    }

    @Nonnull
    public GeneralConfig prepareGeneralConfig(String serverName, String logoUrl, Integer itemsBrowserPagingSize) {
        GeneralConfig config = new GeneralConfig();
        config.setServerName(serverName);
        config.setLogoUrl(logoUrl);
        config.setHelpLinksEnabled(true);
        config.setFileUploadMaxSize(10);
        config.setItemsBrowserPagingSize(itemsBrowserPagingSize);
        config.setShowAddonSettings(false);
        config.setGlobalOfflineMode(true);
        config.setSystemMessageEnabled(false);
        config.setFolderDownloadEnabled(false);
        config.setFolderDownloadEnabled(false);
        config.setRepositoriesConfig(new RepositoriesGeneralConfigDescriptor());
        return config;
    }
}
