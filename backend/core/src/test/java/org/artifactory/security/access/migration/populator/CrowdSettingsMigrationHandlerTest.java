package org.artifactory.security.access.migration.populator;

import org.artifactory.config.AccessConfigKeys.CrowdConfigKeys;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.model.CrowdSetting;
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
import java.util.Optional;

import static org.artifactory.config.AccessConfigKeys.CROWD_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CrowdSettingsMigrationHandlerTest {
    @Mock
    private AccessService accessService;
    private AutoCloseable mocks;
    private CrowdSettingsMigrationHandler crowdPopulator;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        crowdPopulator = new CrowdSettingsMigrationHandler(accessService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testPopulateNewCrowdSettings() throws JSONException {
        Map<String, Object> crowdSettingsMap = Map.of(SECURITY,
                Map.of(CROWD_SETTINGS,
                        Map.ofEntries(
                                Map.entry(CrowdConfigKeys.APPLICATION_NAME, "appName1"),
                                Map.entry(CrowdConfigKeys.PASSWORD, "password1"),
                                Map.entry(CrowdConfigKeys.SERVER_URL, "crowd://myserver:myport"),
                                Map.entry(CrowdConfigKeys.SESSION_VALIDATION_INTERVAL, 5),
                                Map.entry(CrowdConfigKeys.ENABLE_INTEGRATION, Boolean.TRUE),
                                Map.entry(CrowdConfigKeys.NO_AUTO_CREATION, Boolean.TRUE.toString()),
                                Map.entry(CrowdConfigKeys.USE_DEFAULT_PROXY, Boolean.TRUE),
                                Map.entry(CrowdConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, Boolean.TRUE),
                                Map.entry(CrowdConfigKeys.CUSTOM_COOKIE_TOKEN_KEY, "customCookieTokenKey1"),
                                Map.entry(CrowdConfigKeys.DIRECT_AUTHENTICATION, Boolean.TRUE),
                                Map.entry(CrowdConfigKeys.OVERRIDE_ALL_GROUPS_UPON_LOGIN, Boolean.TRUE)
                        )
                )
        );

        Map<String, Object> crowdSettingsMapMutable = JsonUtils.getInstance().readValue(
                JsonUtils.getInstance().valueToString(crowdSettingsMap), HashMap.class);

        when(accessService.getCrowdSettings()).thenReturn(Optional.of(CrowdSetting.builder().build()));

        int movedResources = crowdPopulator.migrate(crowdSettingsMapMutable);

        Assertions.assertEquals(11, movedResources);

        ArgumentCaptor<CrowdSetting> crowdSettingArgumentCaptor = ArgumentCaptor.forClass(CrowdSetting.class);
        verify(accessService).upsertCrowdSettings(crowdSettingArgumentCaptor.capture());
        CrowdSetting actualCrowdSetting = crowdSettingArgumentCaptor.getValue();
        String actualJson = JsonUtils.getInstance().valueToString(actualCrowdSetting);
        String expectedJson = """
                {
                  "enable": true,
                  "serverUrl": "crowd://myserver:myport",
                  "applicationName": "appName1",
                  "password": "password1",
                  "sessionValidationInterval": 5,
                  "useDefaultProxy": true,
                  "autoUserCreation": true,
                  "allowUserToAccessProfile": true,
                  "customCookieTokenKey": "customCookieTokenKey1",
                  "directAuthentication": true,
                  "overrideAllGroupsUponLogin": true
                }""";
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    void testPartialPopulateExistingCrowdSettings() throws JSONException {
        Map<String, Object> crowdSettingsMap = Map.of(SECURITY,
                Map.of(CROWD_SETTINGS,
                        Map.of(
                                CrowdConfigKeys.SESSION_VALIDATION_INTERVAL, "10",
                                CrowdConfigKeys.ENABLE_INTEGRATION, Boolean.FALSE,
                                CrowdConfigKeys.NO_AUTO_CREATION, Boolean.FALSE.toString()
                        )
                )
        );

        Map<String, Object> crowdSettingsMapMutable = JsonUtils.getInstance().readValue(
                JsonUtils.getInstance().valueToString(crowdSettingsMap), HashMap.class);
        CrowdSetting existingCrowdSetting = CrowdSetting.builder()
                .applicationName("appName1")
                .serverUrl("serverUrl1")
                .password("password1")
                .useDefaultProxy(true)
                .autoUserCreation(true)
                .allowUserToAccessProfile(true)
                .customCookieTokenKey("customCookieToken1")
                .directAuthentication(true)
                .overrideAllGroupsUponLogin(true)
                .sessionValidationInterval(32L)
                .enable(true)
                .autoUserCreation(true)
                .build();
        when(accessService.getCrowdSettings()).thenReturn(Optional.of(existingCrowdSetting));

        int movedResources = crowdPopulator.migrate(crowdSettingsMapMutable);

        Assertions.assertEquals(3, movedResources);

        ArgumentCaptor<CrowdSetting> crowdSettingArgumentCaptor = ArgumentCaptor.forClass(CrowdSetting.class);
        verify(accessService).upsertCrowdSettings(crowdSettingArgumentCaptor.capture());
        CrowdSetting actualCrowdSetting = crowdSettingArgumentCaptor.getValue();
        String actualJson = JsonUtils.getInstance().valueToString(actualCrowdSetting);
        String expectedJson = """
                {
                  "enable": false,
                  "serverUrl": "serverUrl1",
                  "applicationName": "appName1",
                  "password": "password1",
                  "sessionValidationInterval": 10,
                  "useDefaultProxy": true,
                  "autoUserCreation": false,
                  "allowUserToAccessProfile": true,
                  "customCookieTokenKey": "customCookieToken1",
                  "directAuthentication": true,
                  "overrideAllGroupsUponLogin": true
                }""";
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    void testRemoveSettingByKeyWithEmptyBody() throws JSONException {
        Map<String, Object> crowdSettingsMap = Map.of(SECURITY,
                Map.of(CROWD_SETTINGS, Map.of())
        );

        Map<String, Object> crowdSettingsMapMutable = JsonUtils.getInstance().readValue(
                JsonUtils.getInstance().valueToString(crowdSettingsMap), HashMap.class);

        CrowdSetting existingCrowdSetting = CrowdSetting.builder()
                .applicationName("appName1")
                .serverUrl("serverUrl1")
                .password("password1")
                .useDefaultProxy(true)
                .autoUserCreation(true)
                .allowUserToAccessProfile(true)
                .customCookieTokenKey("customCookieToken1")
                .directAuthentication(true)
                .overrideAllGroupsUponLogin(true)
                .sessionValidationInterval(32L)
                .enable(true)
                .autoUserCreation(true)
                .build();
        when(accessService.getCrowdSettings()).thenReturn(Optional.of(existingCrowdSetting));

        int movedResources = crowdPopulator.migrate(crowdSettingsMapMutable);

        Assertions.assertEquals(1, movedResources);

        ArgumentCaptor<CrowdSetting> crowdSettingArgumentCaptor = ArgumentCaptor.forClass(CrowdSetting.class);
        verify(accessService).upsertCrowdSettings(crowdSettingArgumentCaptor.capture());
        CrowdSetting crowdSetting = crowdSettingArgumentCaptor.getValue();
        Assertions.assertNotNull(crowdSetting);
        String actualJson = JsonUtils.getInstance().valueToString(crowdSetting);
        String expectedJson = JsonUtils.getInstance().valueToString(CrowdSetting.builder().build());
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    void testTryToRemoveNonExistentCrowdSetting() {
        Map<String, Object> crowdSettingsMap = Map.of(SECURITY,
                Map.of(CROWD_SETTINGS, Map.of())
        );

        when(accessService.getCrowdSettings()).thenReturn(Optional.empty());


        Assertions.assertThrows(IllegalArgumentException.class, () -> crowdPopulator.migrate(crowdSettingsMap),
                "Attempted to delete: " + CROWD_SETTINGS + " but it does not exist");

        when(accessService.getCrowdSettings()).thenReturn(Optional.of(CrowdSetting.builder().build()));

        Assertions.assertThrows(IllegalArgumentException.class, () -> crowdPopulator.migrate(crowdSettingsMap),
                "Attempted to delete: " + CROWD_SETTINGS + " but it does not exist");
        verify(accessService, never()).upsertCrowdSettings(any());
    }

    @Test
    void testEmptyConfigMapNoPopulate() {
        int movedResources = crowdPopulator.migrate(Map.of());
        Assertions.assertEquals(0, movedResources);
        verify(accessService, never()).upsertCrowdSettings(any());
    }
}