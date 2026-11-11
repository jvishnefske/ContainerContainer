package org.artifactory.security.access.migration.populator;

import jakarta.annotation.Nonnull;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.model.HttpSsoSetting;
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

import static org.artifactory.config.AccessConfigKeys.HTTP_SSO_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.HttpSsoConfigKeys;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class HttpSsoSettingsMigrationHandlerTest {

    @Mock
    private AccessService accessService;
    private AutoCloseable mocks;

    private HttpSsoSettingsMigrationHandler httpSsoPopulator;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        httpSsoPopulator = new HttpSsoSettingsMigrationHandler(accessService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testPopulateNewHttpSSoSettings() throws JSONException {
        Map<String, Object> httpSsoSettingsMapMutable = toMutableMap(Map.of(SECURITY,
                Map.of(HTTP_SSO_SETTINGS,
                        Map.of(HttpSsoConfigKeys.HTTP_SSO_PROXIED, Boolean.TRUE,
                                HttpSsoConfigKeys.REMOTE_USER_REQUEST_VARIABLE, "remoter",
                                HttpSsoConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, Boolean.TRUE.toString(),
                                HttpSsoConfigKeys.NO_AUTO_CREATION, Boolean.FALSE,
                                HttpSsoConfigKeys.SYNC_LDAP_GROUPS, Boolean.TRUE
                        )
                )
        ));
        when(accessService.getHttpSsoSettings()).thenReturn(Optional.of(HttpSsoSetting.builder().build()));

        int movedResources = httpSsoPopulator.migrate(httpSsoSettingsMapMutable);

        Assertions.assertEquals(5, movedResources);

        ArgumentCaptor<HttpSsoSetting> httpSsoSettingArgumentCaptor = ArgumentCaptor.forClass(HttpSsoSetting.class);
        verify(accessService).upsertHttpSsoSettings(httpSsoSettingArgumentCaptor.capture());
        HttpSsoSetting httpSsoSetting = httpSsoSettingArgumentCaptor.getValue();

        String actualJson = JsonUtils.getInstance().valueToString(httpSsoSetting);
        String expectedJson = """
                {
                  "httpSsoProxied": true,
                  "remoteUserRequestVariable": "remoter",
                  "allowUserToAccessProfile": true,
                  "autoCreateUser": false,
                  "syncLdapGroups": true
                }""";
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    void testPartialPopulateExistingHttpSsoSettings() throws JSONException {
        Map<String, Object> httpSsoSettingsMapMutable = toMutableMap(Map.of(SECURITY,
                Map.of(HTTP_SSO_SETTINGS,
                        Map.of(HttpSsoConfigKeys.HTTP_SSO_PROXIED, Boolean.TRUE,
                                HttpSsoConfigKeys.REMOTE_USER_REQUEST_VARIABLE, "remoter",
                                HttpSsoConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, Boolean.TRUE.toString()
                        )
                )
        ));
        HttpSsoSetting existingHttpSsoSetting = HttpSsoSetting.builder()
                .httpSsoProxied(false)
                .autoCreateUser(false)
                .remoteUserRequestVariable("old")
                .allowUserToAccessProfile(false)
                .syncLdapGroups(true)
                .build();

        when(accessService.getHttpSsoSettings()).thenReturn(Optional.of(existingHttpSsoSetting));

        int movedResources = httpSsoPopulator.migrate(httpSsoSettingsMapMutable);

        Assertions.assertEquals(3, movedResources);

        ArgumentCaptor<HttpSsoSetting> httpSsoSettingArgumentCaptor = ArgumentCaptor.forClass(HttpSsoSetting.class);
        verify(accessService).upsertHttpSsoSettings(httpSsoSettingArgumentCaptor.capture());
        HttpSsoSetting httpSsoSetting = httpSsoSettingArgumentCaptor.getValue();

        String actualJson = JsonUtils.getInstance().valueToString(httpSsoSetting);
        String expectedJson = """
                {
                  "httpSsoProxied": true,
                  "remoteUserRequestVariable": "remoter",
                  "allowUserToAccessProfile": true,
                  "autoCreateUser": false,
                  "syncLdapGroups": true
                }""";
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    void testRemoveSettingByKeyWithEmptyBody() throws JSONException {
        Map<String, Object> httpSsoSettingsMapMutable = toMutableMap(Map.of(SECURITY,
                Map.of(HTTP_SSO_SETTINGS, Map.of())
        ));

        HttpSsoSetting existingHttpSsoSettings = HttpSsoSetting.builder()
                .httpSsoProxied(true)
                .autoCreateUser(false)
                .allowUserToAccessProfile(true)
                .syncLdapGroups(true)
                .build();
        when(accessService.getHttpSsoSettings()).thenReturn(Optional.of(existingHttpSsoSettings));

        int movedResources = httpSsoPopulator.migrate(httpSsoSettingsMapMutable);

        Assertions.assertEquals(1, movedResources);

        ArgumentCaptor<HttpSsoSetting> httpSsoSettingArgumentCaptor = ArgumentCaptor.forClass(HttpSsoSetting.class);
        verify(accessService).upsertHttpSsoSettings(httpSsoSettingArgumentCaptor.capture());
        HttpSsoSetting httpSsoSetting = httpSsoSettingArgumentCaptor.getValue();

        Assertions.assertNotNull(httpSsoSetting);
        String actualJson = JsonUtils.getInstance().valueToString(httpSsoSetting);
        String expectedJson = JsonUtils.getInstance().valueToString(HttpSsoSetting.builder().build());
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    void testTryToRemoveNonExistingSettingThrowsException() {
        Map<String, Object> httpSsoSettingsMapMutable = toMutableMap(Map.of(SECURITY,
                Map.of(HTTP_SSO_SETTINGS, Map.of())
        ));

        when(accessService.getHttpSsoSettings()).thenReturn(Optional.empty());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> httpSsoPopulator.migrate(httpSsoSettingsMapMutable),
                "Attempted to delete: " + HTTP_SSO_SETTINGS + " but it does not exist");

        when(accessService.getHttpSsoSettings()).thenReturn(Optional.of(HttpSsoSetting.builder().build()));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> httpSsoPopulator.migrate(httpSsoSettingsMapMutable),
                "Attempted to delete: " + HTTP_SSO_SETTINGS + " but it does not exist");
        verify(accessService, never()).upsertHttpSsoSettings(any());
    }

    @Test
    void testEmptyConfigMapNoPopulate() {
        int movedResources = httpSsoPopulator.migrate(Map.of());
        Assertions.assertEquals(0, movedResources);
        verify(accessService, never()).upsertHttpSsoSettings(any());
    }


    @Nonnull
    private static Map<String, Object> toMutableMap(Map<String, Object> httpSsoSettingsMap) {
        return JsonUtils.getInstance().readValue(
                JsonUtils.getInstance().valueToString(httpSsoSettingsMap), HashMap.class);
    }
}