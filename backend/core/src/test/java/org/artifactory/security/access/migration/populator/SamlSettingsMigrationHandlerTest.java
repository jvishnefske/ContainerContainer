package org.artifactory.security.access.migration.populator;

import org.artifactory.config.AccessConfigKeys.SamlConfigKeys;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.model.SamlSetting;
import org.jfrog.common.JsonUtils;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.HashMap;
import java.util.Map;

import static org.artifactory.config.AccessConfigKeys.SAML_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.security.access.AccessServiceImpl.SAML_DEFAULT_SETTINGS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SamlSettingsMigrationHandlerTest {
    @Mock
    private AccessService accessService;
    private AutoCloseable mocks;
    private SamlSettingsMigrationHandler samlPopulator;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        samlPopulator = new SamlSettingsMigrationHandler(accessService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testPopulateNewSamlSettings() throws JSONException {
        Map<String, Object> samlSettingsMap = Map.of(SECURITY,
                Map.of(SAML_SETTINGS,
                        Map.ofEntries(
                                Map.entry(SamlConfigKeys.ENABLE_INTEGRATION, Boolean.TRUE),
                                Map.entry(SamlConfigKeys.LOGIN_URL, "login_url"),
                                Map.entry(SamlConfigKeys.LOGOUT_URL, "logout_url"),
                                Map.entry(SamlConfigKeys.SERVICE_PROVIDER_NAME, "service_name"),
                                Map.entry(SamlConfigKeys.CERTIFICATE, "certificate"),
                                Map.entry(SamlConfigKeys.USE_ENCRYPTED_ASSERTION, Boolean.TRUE),
                                Map.entry(SamlConfigKeys.SYNC_GROUPS, Boolean.TRUE),
                                Map.entry(SamlConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, Boolean.TRUE),
                                Map.entry(SamlConfigKeys.GROUP_ATTRIBUTE, "groupAttribute"),
                                Map.entry(SamlConfigKeys.EMAIL_ATTRIBUTE, "emailAttribute"),
                                Map.entry(SamlConfigKeys.NO_AUTO_USER_CREATION, Boolean.TRUE),
                                Map.entry(SamlConfigKeys.AUTO_REDIRECT, Boolean.TRUE),
                                Map.entry(SamlConfigKeys.VERIFY_AUDIENCE_RESTRICTION, Boolean.TRUE)
                        )
                )
        );

        Map<String, Object> samlSettingsMapMutable = JsonUtils.getInstance().readValue(
                JsonUtils.getInstance().valueToString(samlSettingsMap), HashMap.class);

        when(accessService.getSamlSettings()).thenReturn(SamlSetting.builder().build());

        int movedResources = samlPopulator.migrate(samlSettingsMapMutable);

        Assertions.assertEquals(13, movedResources);

        ArgumentCaptor<SamlSetting> samlSettingArgumentCaptor = ArgumentCaptor.forClass(SamlSetting.class);
        verify(accessService).upsertSamlSetting(samlSettingArgumentCaptor.capture());
        SamlSetting actualSamlSetting = samlSettingArgumentCaptor.getValue();
        String actualJson = JsonUtils.getInstance().valueToString(actualSamlSetting);
        String expectedJson = """
                {
                  "enableIntegration": true,
                  "loginUrl": "login_url",
                  "logoutUrl": "logout_url",
                  "serviceProviderName": "service_name",
                  "certificate": "certificate",
                  "useEncryptedAssertion": true,
                  "syncGroups": true,
                  "allowUserToAccessProfile": true,
                  "groupAttribute": "groupAttribute",
                  "emailAttribute": "emailAttribute",
                  "autoUserCreation": true,
                  "autoRedirect": true,
                  "verifyAudienceRestriction": true
                }""";
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    void testPartialPopulateExistingSamlSettings() throws JSONException {
        Map<String, Object> samlSettingsMap = Map.of(SECURITY,
                Map.of(SAML_SETTINGS,
                        Map.of(
                                SamlConfigKeys.CERTIFICATE, "cert",
                                SamlConfigKeys.ENABLE_INTEGRATION, Boolean.FALSE,
                                SamlConfigKeys.NO_AUTO_USER_CREATION, Boolean.FALSE
                        )
                )
        );

        Map<String, Object> samlSettingsMapMutable = JsonUtils.getInstance().readValue(
                JsonUtils.getInstance().valueToString(samlSettingsMap), HashMap.class);
        SamlSetting existingSamlSetting = SamlSetting.builder()
                .name("default")
                .enableIntegration(true)
                .certificate("password")
                .loginUrl("login_url")
                .logoutUrl("logout_url")
                .serviceProviderName("service_name")
                .emailAttribute("emailAttribute")
                .groupAttribute("groupAttribute")
                .verifyAudienceRestriction(true)
                .allowUserToAccessProfile(true)
                .autoRedirect(true)
                .syncGroups(true)
                .useEncryptedAssertion(true)
                .build();
        when(accessService.getSamlSettings()).thenReturn(existingSamlSetting);

        int movedResources = samlPopulator.migrate(samlSettingsMapMutable);

        Assertions.assertEquals(3, movedResources);

        ArgumentCaptor<SamlSetting> samlSettingArgumentCaptor = ArgumentCaptor.forClass(SamlSetting.class);
        verify(accessService).upsertSamlSetting(samlSettingArgumentCaptor.capture());
        SamlSetting actualSamlSetting = samlSettingArgumentCaptor.getValue();
        String actualJson = JsonUtils.getInstance().valueToString(actualSamlSetting);
        String expectedJson = """
                {
                  "name": "default",
                  "enableIntegration": false,
                  "loginUrl": "login_url",
                  "logoutUrl": "logout_url",
                  "serviceProviderName": "service_name",
                  "certificate": "cert",
                  "useEncryptedAssertion": true,
                  "syncGroups": true,
                  "allowUserToAccessProfile": true,
                  "groupAttribute": "groupAttribute",
                  "emailAttribute": "emailAttribute",
                  "autoUserCreation": false,
                  "autoRedirect": true,
                  "verifyAudienceRestriction": true
                }""";
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    void testRemoveSettingByKeyWithEmptyBody() throws JSONException {
        Map<String, Object> samlSettingsMap = Map.of(SECURITY,
                Map.of(SAML_SETTINGS, Map.of())
        );

        Map<String, Object> samlSettingsMapMutable = JsonUtils.getInstance().readValue(
                JsonUtils.getInstance().valueToString(samlSettingsMap), HashMap.class);

        SamlSetting existingSamlSetting = SamlSetting.builder()
                .name("default")
                .enableIntegration(true)
                .certificate("password")
                .loginUrl("login_url")
                .logoutUrl("logout_url")
                .serviceProviderName("service_name")
                .emailAttribute("emailAttribute")
                .groupAttribute("groupAttribute")
                .verifyAudienceRestriction(true)
                .allowUserToAccessProfile(true)
                .autoRedirect(true)
                .syncGroups(true)
                .useEncryptedAssertion(true)
                .build();
        when(accessService.getSamlSettings()).thenReturn(existingSamlSetting);

        int movedResources = samlPopulator.migrate(samlSettingsMapMutable);

        Assertions.assertEquals(1, movedResources);

        ArgumentCaptor<SamlSetting> samlSettingArgumentCaptor = ArgumentCaptor.forClass(SamlSetting.class);
        verify(accessService).upsertSamlSetting(samlSettingArgumentCaptor.capture());
        SamlSetting samlSetting = samlSettingArgumentCaptor.getValue();
        Assertions.assertNotNull(samlSetting);
        String actualJson = JsonUtils.getInstance().valueToString(samlSetting);
        String expectedJson = JsonUtils.getInstance().valueToString(SamlSetting.builder().build());
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    void testTryToRemoveNonExistentSamlSetting() {
        Map<String, Object> samlSettingsMap = Map.of(SECURITY,
                Map.of(SAML_SETTINGS, Map.of())
        );

        when(accessService.getSamlSettings()).thenReturn(SAML_DEFAULT_SETTINGS);


        Assertions.assertThrows(IllegalArgumentException.class, () -> samlPopulator.migrate(samlSettingsMap),
                "Attempted to delete: " + SAML_SETTINGS + " but it does not exist");

        when(accessService.getSamlSettings()).thenReturn(SAML_DEFAULT_SETTINGS);

        Assertions.assertThrows(IllegalArgumentException.class, () -> samlPopulator.migrate(samlSettingsMap),
                "Attempted to delete: " + SAML_SETTINGS + " but it does not exist");
        verify(accessService, never()).upsertSamlSetting(any());
    }

    @Test
    void testEmptyConfigMapNoPopulate() {
        int movedResources = samlPopulator.migrate(Map.of());
        Assertions.assertEquals(0, movedResources);
        verify(accessService, never()).upsertSamlSetting(any());
    }
}
