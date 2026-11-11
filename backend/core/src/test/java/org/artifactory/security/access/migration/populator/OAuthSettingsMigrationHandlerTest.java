package org.artifactory.security.access.migration.populator;

import org.artifactory.aol.AolService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.AccessConfigKeys;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.model.OAuthSetting;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.config.AccessConfigKeys.OAUTH_PROVIDER_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.OAUTH_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OAuthSettingsMigrationHandlerTest {

    @Mock
    private AccessService accessService;
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private EncryptionWrapper encryptionWrapper;
    @Mock
    private AolService aolService;
    private AutoCloseable mocks;
    private OAuthSettingsMigrationHandler oAuthPopulator;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(aolService.isAol()).thenReturn(false);
        oAuthPopulator = new OAuthSettingsMigrationHandler(accessService, aolService);
        ArtifactoryHome.bind(artifactoryHome);
        when(encryptionWrapper.encryptIfNeeded(any())).then(AdditionalAnswers.returnsFirstArg());
        when(artifactoryHome.getMasterEncryptionWrapper()).thenReturn(encryptionWrapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testPopulateNewOAuthSettings() {
        Map<String, Object> githubSettings = new HashMap<>();
        githubSettings.put("enabled", true);
        githubSettings.put("providerType", "github");
        githubSettings.put("id", "clientId");
        githubSettings.put("secret", "clientSecret");
        githubSettings.put("apiUrl", "https://api.github.com/user");
        githubSettings.put("authUrl", "https://github.com/login/oauth/authorize");
        githubSettings.put("tokenUrl", "https://github.com/login/oauth/access_token");
        githubSettings.put("basicUrl", "https://github.com/");
        githubSettings.put("domain", "example.com");
        githubSettings.put("pkce", false);
        githubSettings.put("useDefaultProxy", false);

        Map<String, Object> oAuthProviderSettings = new HashMap<>();
        oAuthProviderSettings.put("github", githubSettings);

        Map<String, Object> oAuthSettings = new HashMap<>();
        oAuthSettings.put("enableIntegration", true);
        oAuthSettings.put("allowUserToAccessProfile", true);
        oAuthSettings.put("persistUsers", true);
        oAuthSettings.put(OAUTH_PROVIDER_SETTINGS, oAuthProviderSettings);

        Map<String, Object> security = new HashMap<>();
        security.put(OAUTH_SETTINGS, oAuthSettings);

        Map<String, Object> oAuthSettingsMap = new HashMap<>();
        oAuthSettingsMap.put(SECURITY, security);

        when(accessService.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());
        when(accessService.getAllOAuthSettings()).thenReturn(List.of());

        int movedResources = oAuthPopulator.migrate(oAuthSettingsMap);

        Assertions.assertEquals(14, movedResources);

        ArgumentCaptor<List<OAuthSetting>> oAuthSettingsCaptor = ArgumentCaptor.forClass(List.class);
        verify(accessService).upsertOAuthSettings(oAuthSettingsCaptor.capture());
        List<OAuthSetting> actualOAuthSettings = oAuthSettingsCaptor.getValue();
        Assertions.assertEquals(1, actualOAuthSettings.size());
        Assertions.assertEquals(actualOAuthSettings,
                List.of(OAuthSetting.builder().name("github").enabled(true).providerType("github").clientId("clientId")
                        .clientSecret("clientSecret").apiUrl("https://api.github.com/user")
                        .authUrl("https://github.com/login/oauth/authorize")
                        .tokenUrl("https://github.com/login/oauth/access_token").basicUrl("https://github.com/")
                        .domain("example.com").pkce(false).useDefaultProxy(false).autoUserCreation(true)
                        .allowUserToAccessProfile(true).build()));
    }

    @Test
    void testPartialUpdateExistingOAuthSettings() {
        Map<String, Object> githubSettings = new HashMap<>();
        githubSettings.put("enabled", false);
        githubSettings.put("providerType", "github");
        githubSettings.put("id", "newClientId");
        githubSettings.put("secret", "newClientSecret");

        Map<String, Object> oAuthProviderSettings = new HashMap<>();
        oAuthProviderSettings.put("github", githubSettings);

        Map<String, Object> oAuthSettings = new HashMap<>();
        oAuthSettings.put("persistUsers", false);
        oAuthSettings.put(OAUTH_PROVIDER_SETTINGS, oAuthProviderSettings);

        Map<String, Object> security = new HashMap<>();
        security.put(OAUTH_SETTINGS, oAuthSettings);

        Map<String, Object> oAuthSettingsMap = new HashMap<>();
        oAuthSettingsMap.put(SECURITY, security);

        OAuthSetting existingOAuthSetting =
                OAuthSetting.builder().name("github").enabled(true).providerType("github").clientId("oldClientId")
                        .domain("test").clientSecret("oldClientSecret").autoUserCreation(true)
                        .allowUserToAccessProfile(true).build();

        when(accessService.getAllOAuthSettings()).thenReturn(List.of(existingOAuthSetting));
        when(accessService.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());

        int movedResources = oAuthPopulator.migrate(oAuthSettingsMap);

        Assertions.assertEquals(5, movedResources);

        ArgumentCaptor<List<OAuthSetting>> oAuthSettingsCaptor = ArgumentCaptor.forClass(List.class);
        verify(accessService).upsertOAuthSettings(oAuthSettingsCaptor.capture());
        List<OAuthSetting> actualOAuthSettings = oAuthSettingsCaptor.getValue();

        Assertions.assertEquals(actualOAuthSettings,
                List.of(OAuthSetting.builder().name("github").enabled(false).providerType("github").domain("test")
                        .clientId("newClientId").clientSecret("newClientSecret").autoUserCreation(false)
                        .allowUserToAccessProfile(true).build()));
    }

    @Test
    void testRemoveAllOAuthSettings() {
        Map<String, Object> oAuthProviderSettings = new HashMap<>();

        Map<String, Object> oAuthSettings = new HashMap<>();
        oAuthSettings.put(OAUTH_PROVIDER_SETTINGS, oAuthProviderSettings);

        Map<String, Object> security = new HashMap<>();
        security.put(OAUTH_SETTINGS, oAuthSettings);

        Map<String, Object> oAuthSettingsMap = new HashMap<>();
        oAuthSettingsMap.put(SECURITY, security);

        OAuthSetting existingOAuthSetting =
                OAuthSetting.builder().name("github").enabled(true).providerType("github").clientId("clientId")
                        .clientSecret("clientSecret").build();

        when(accessService.getAllOAuthSettings()).thenReturn(List.of(existingOAuthSetting));
        when(accessService.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());

        int movedResources = oAuthPopulator.migrate(oAuthSettingsMap);

        Assertions.assertEquals(1, movedResources);

        verify(accessService).removeOAuthSetting("github");
    }

    @Test
    void testEmptyConfigMapNoMigration() {
        int movedResources = oAuthPopulator.migrate(new HashMap<>());
        Assertions.assertEquals(0, movedResources);
        verify(accessService, never()).upsertOAuthSettings(any());
        verify(accessService, never()).removeOAuthSetting(any());
    }

    @Test
    void testRemoveSingleOAuthProvider() {
        Map<String, Object> githubSettings = new HashMap<>();

        Map<String, Object> oAuthProviderSettings = new HashMap<>();
        oAuthProviderSettings.put("github", githubSettings);

        Map<String, Object> oAuthSettings = new HashMap<>();
        oAuthSettings.put(OAUTH_PROVIDER_SETTINGS, oAuthProviderSettings);

        Map<String, Object> security = new HashMap<>();
        security.put(OAUTH_SETTINGS, oAuthSettings);

        Map<String, Object> oAuthSettingsMap = new HashMap<>();
        oAuthSettingsMap.put(SECURITY, security);

        OAuthSetting existingOAuthSetting =
                OAuthSetting.builder().name("github").enabled(true).providerType("github").clientId("clientId")
                        .clientSecret("clientSecret").build();

        when(accessService.getAllOAuthSettings()).thenReturn(List.of(existingOAuthSetting));
        when(accessService.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());

        int movedResources = oAuthPopulator.migrate(oAuthSettingsMap);

        Assertions.assertEquals(1, movedResources);

        verify(accessService).removeOAuthSetting("github");
    }

    @Test
    void testRemoveSingleOAuthProvider_Empty() {

        Map<String, Object> oAuthProviderSettings = new HashMap<>();
        oAuthProviderSettings.put("github", null);

        Map<String, Object> oAuthSettings = new HashMap<>();
        oAuthSettings.put(OAUTH_PROVIDER_SETTINGS, oAuthProviderSettings);

        Map<String, Object> security = new HashMap<>();
        security.put(OAUTH_SETTINGS, oAuthSettings);

        Map<String, Object> oAuthSettingsMap = new HashMap<>();
        oAuthSettingsMap.put(SECURITY, security);

        OAuthSetting existingOAuthSetting =
                OAuthSetting.builder().name("github").enabled(true).providerType("github").clientId("clientId")
                        .clientSecret("clientSecret").build();

        when(accessService.getAllOAuthSettings()).thenReturn(List.of(existingOAuthSetting));
        when(accessService.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());

        int movedResources = oAuthPopulator.migrate(oAuthSettingsMap);

        Assertions.assertEquals(1, movedResources);

        verify(accessService).removeOAuthSetting("github");
    }

    @Test
    void testInvalidProviderValidation() {
        Map<String, Object> githubSettings = new HashMap<>();
        githubSettings.put("enabled", true);

        Map<String, Object> oAuthProviderSettings = new HashMap<>();
        oAuthProviderSettings.put("github", githubSettings);

        Map<String, Object> oAuthSettings = new HashMap<>();
        oAuthSettings.put(OAUTH_PROVIDER_SETTINGS, oAuthProviderSettings);

        Map<String, Object> security = new HashMap<>();
        security.put(OAUTH_SETTINGS, oAuthSettings);

        Map<String, Object> oAuthSettingsMap = new HashMap<>();
        oAuthSettingsMap.put(SECURITY, security);

        when(aolService.isAol()).thenReturn(true);

        OAuthSetting existingOAuthSetting =
                OAuthSetting.builder().name("github").enabled(true).providerType("github").clientId("clientId")
                        .clientSecret("clientSecret").useDefaultProxy(true).build();

        when(accessService.getAllOAuthSettings()).thenReturn(List.of(existingOAuthSetting));
        when(accessService.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            oAuthPopulator.migrate(oAuthSettingsMap);
        });

        Assertions.assertTrue(exception.getMessage().contains("Proxy is not available in Saas for provider: github"));
    }

    @Test
    void testEmptyProviderTypeThrowsException() {

        when(accessService.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());
        when(accessService.getAllOAuthSettings()).thenReturn(List.of());

        Map<String, Object> githubSettings = new HashMap<>();
        githubSettings.put("enabled", true);
        githubSettings.put("providerType", ""); // Blank provider type

        Map<String, Object> oAuthProviderSettings = new HashMap<>();
        oAuthProviderSettings.put("github", githubSettings);

        Map<String, Object> oAuthSettings = new HashMap<>();
        oAuthSettings.put(OAUTH_PROVIDER_SETTINGS, oAuthProviderSettings);

        Map<String, Object> security = new HashMap<>();
        security.put(OAUTH_SETTINGS, oAuthSettings);

        Map<String, Object> oAuthSettingsMap = new HashMap<>();
        oAuthSettingsMap.put(SECURITY, security);

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            oAuthPopulator.migrate(oAuthSettingsMap);
        });

        Assertions.assertTrue(exception.getMessage().contains("Provider type cannot be empty for provider: github"));
    }

    @Test
    void testMissingDomainForGitHubThrowsException() {

        when(accessService.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());
        when(accessService.getAllOAuthSettings()).thenReturn(List.of());

        Map<String, Object> githubSettings = new HashMap<>();
        githubSettings.put("enabled", true);
        githubSettings.put("providerType", "github");
        githubSettings.put("domain", ""); // Blank domain

        Map<String, Object> oAuthProviderSettings = new HashMap<>();
        oAuthProviderSettings.put("github", githubSettings);

        Map<String, Object> oAuthSettings = new HashMap<>();
        oAuthSettings.put(OAUTH_PROVIDER_SETTINGS, oAuthProviderSettings);

        Map<String, Object> security = new HashMap<>();
        security.put(OAUTH_SETTINGS, oAuthSettings);

        Map<String, Object> oAuthSettingsMap = new HashMap<>();
        oAuthSettingsMap.put(SECURITY, security);

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            oAuthPopulator.migrate(oAuthSettingsMap);
        });

        Assertions.assertTrue(
                exception.getMessage().contains("Organization cannot be empty for github provider: github"));
    }

    @Test
    void testUpdateAllowUserToAccessProfile() {
        Map<String, Object> githubSettings = new HashMap<>();
        githubSettings.put("enabled", true);
        githubSettings.put("providerType", "github");
        githubSettings.put("domain", "github.com");

        Map<String, Object> oAuthProviderSettings = new HashMap<>();
        oAuthProviderSettings.put("github", githubSettings);

        Map<String, Object> oAuthSettings = new HashMap<>();
        oAuthSettings.put(OAUTH_PROVIDER_SETTINGS, oAuthProviderSettings);
        oAuthSettings.put(AccessConfigKeys.OAuthConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, false);

        Map<String, Object> security = new HashMap<>();
        security.put(OAUTH_SETTINGS, oAuthSettings);

        Map<String, Object> oAuthSettingsMap = new HashMap<>();
        oAuthSettingsMap.put(SECURITY, security);

        OAuthSetting existingOAuthSetting =
                OAuthSetting.builder().name("github").enabled(true).providerType("github").clientId("clientId")
                        .clientSecret("clientSecret").allowUserToAccessProfile(true).build();

        when(accessService.getAllOAuthSettings()).thenReturn(List.of(existingOAuthSetting));
        when(accessService.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());

        int movedResources = oAuthPopulator.migrate(oAuthSettingsMap);

        Assertions.assertEquals(4, movedResources);

        ArgumentCaptor<List<OAuthSetting>> oAuthSettingsCaptor = ArgumentCaptor.forClass(List.class);
        verify(accessService).upsertOAuthSettings(oAuthSettingsCaptor.capture());
        List<OAuthSetting> actualOAuthSettings = oAuthSettingsCaptor.getValue();

        Assertions.assertEquals(1, actualOAuthSettings.size());
        OAuthSetting updatedSetting = actualOAuthSettings.get(0);
        Assertions.assertFalse(updatedSetting.isAllowUserToAccessProfile());
    }

    @Test
    void testEnableIntegrationUpdate() {
        Map<String, Object> oAuthSettings = new HashMap<>();
        oAuthSettings.put("enableIntegration", true);

        Map<String, Object> security = new HashMap<>();
        security.put(OAUTH_SETTINGS, oAuthSettings);

        Map<String, Object> oAuthSettingsMap = new HashMap<>();
        oAuthSettingsMap.put(SECURITY, security);

        OAuthGeneralSettings oldGeneralSettings = OAuthGeneralSettings.builder().enable(false).build();

        when(accessService.getOAuthGeneralSettings()).thenReturn(oldGeneralSettings);
        when(accessService.getAllOAuthSettings()).thenReturn(List.of());

        int movedResources = oAuthPopulator.migrate(oAuthSettingsMap);

        Assertions.assertEquals(2, movedResources);
        ArgumentCaptor<OAuthGeneralSettings> generalSettingsCaptor =
                ArgumentCaptor.forClass(OAuthGeneralSettings.class);
        verify(accessService).upsertOAuthGeneralSetting(generalSettingsCaptor.capture());
        OAuthGeneralSettings updatedSettings = generalSettingsCaptor.getValue();

        Assertions.assertTrue(updatedSettings.isEnable());
    }

    @Test
    void testDefaultNpmUpdate() {
        Map<String, Object> oAuthSettings = new HashMap<>();
        oAuthSettings.put("defaultNpm", "newDefaultNpm");

        Map<String, Object> security = new HashMap<>();
        security.put(OAUTH_SETTINGS, oAuthSettings);

        Map<String, Object> oAuthSettingsMap = new HashMap<>();
        oAuthSettingsMap.put(SECURITY, security);

        OAuthGeneralSettings oldGeneralSettings =
                OAuthGeneralSettings.builder().defaultSetting("oldDefaultNpm").build();

        when(accessService.getOAuthGeneralSettings()).thenReturn(oldGeneralSettings);
        when(accessService.getAllOAuthSettings()).thenReturn(List.of());

        int movedResources = oAuthPopulator.migrate(oAuthSettingsMap);

        Assertions.assertEquals(2, movedResources);
        ArgumentCaptor<OAuthGeneralSettings> generalSettingsCaptor =
                ArgumentCaptor.forClass(OAuthGeneralSettings.class);
        verify(accessService).upsertOAuthGeneralSetting(generalSettingsCaptor.capture());
        OAuthGeneralSettings updatedSettings = generalSettingsCaptor.getValue();

        Assertions.assertEquals("newDefaultNpm", updatedSettings.getDefaultSetting());
    }

    @Test
    void testRemoveAllOAuthSettingsWhenProviderSettingsMissing() {
        // Setup: oAuthSettings without OAUTH_PROVIDER_SETTINGS
        Map<String, Object> oAuthSettings = new HashMap<>();
        // No OAUTH_PROVIDER_SETTINGS key

        Map<String, Object> security = new HashMap<>();
        security.put(OAUTH_SETTINGS, oAuthSettings);

        Map<String, Object> oAuthSettingsMap = new HashMap<>();
        oAuthSettingsMap.put(SECURITY, security);

        OAuthSetting existingOAuthSetting =
                OAuthSetting.builder().name("github").enabled(true).providerType("github").clientId("clientId")
                        .clientSecret("clientSecret").build();

        when(accessService.getAllOAuthSettings()).thenReturn(List.of(existingOAuthSetting));
        when(accessService.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());

        int movedResources = oAuthPopulator.migrate(oAuthSettingsMap);

        Assertions.assertEquals(1, movedResources);
        verify(accessService).removeOAuthSetting("github");
    }

}