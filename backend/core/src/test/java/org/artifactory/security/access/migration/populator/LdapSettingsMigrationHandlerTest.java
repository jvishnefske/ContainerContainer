package org.artifactory.security.access.migration.populator;

import jakarta.annotation.Nonnull;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.security.access.AccessService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.artifactory.config.AccessConfigKeys.LdapConfigKeys;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SECURITY_LDAP_SETTINGS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LdapSettingsMigrationHandlerTest {

    @Mock
    private AccessService accessService;
    private AutoCloseable mocks;

    private LdapSettingsMigrationHandler ldapHandler;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ldapHandler = new LdapSettingsMigrationHandler(accessService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testPopulateNewLdapSettings() throws JSONException {
        Map<String, Object> ldapSettingsMap = Map.of(
                SECURITY, Map.of(SECURITY_LDAP_SETTINGS,
                        Map.of("uniqueID", Map.of(
                                        LdapConfigKeys.EMAIL_ATTR, "email@jfrog.com",
                                        LdapConfigKeys.POISONING_PROTECTION, Boolean.TRUE.toString(),
                                        LdapConfigKeys.URL, "ldap://myserver:myport/dc=sampledomain,dc=com",
                                        LdapConfigKeys.SEARCH, Map.of(
                                                LdapConfigKeys.Search.MANAGER_DN, "manager1",
                                                LdapConfigKeys.Search.MANAGER_PASSWORD, "managerpass1",
                                                LdapConfigKeys.Search.SEARCH_BASE, "searchBase1",
                                                LdapConfigKeys.Search.SEARCH_FILTER, "searchFilter1",
                                                LdapConfigKeys.Search.SEARCH_SUB_TREE, Boolean.FALSE.toString()
                                        ),
                                        LdapConfigKeys.USER_DN_PATTERN, "userpattern1",
                                        LdapConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, Boolean.TRUE.toString(),
                                        LdapConfigKeys.AUTO_CREATE_USER, Boolean.TRUE.toString(),
                                        LdapConfigKeys.PAGING_SUPPORT_ENABLED, Boolean.FALSE.toString(),
                                        LdapConfigKeys.ENABLED, Boolean.TRUE.toString()
                                ),
                                "uniqueID2", Map.of(
                                        LdapConfigKeys.EMAIL_ATTR, "email2@jfrog.com"
                                )
                        )
                ));

        Map<String, Object> ldapSettingsMapMutable = toMutableMap(ldapSettingsMap);

        when(accessService.getLdapSettings()).thenReturn(List.of());

        LdapSettingsMigrationHandler
                populator = new LdapSettingsMigrationHandler(accessService);
        int movedResources = populator.migrate(ldapSettingsMapMutable);
        verify(accessService).upsertLdapSettings(any());
        Assertions.assertEquals(14, movedResources);

        ArgumentCaptor<List<LdapSetting>> ldapSettingsArgumentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(accessService).upsertLdapSettings(ldapSettingsArgumentsCaptor.capture());
        List<LdapSetting> ldapSettings = ldapSettingsArgumentsCaptor.getValue();
        Assertions.assertFalse(ldapSettings.isEmpty());
        Assertions.assertEquals(2, ldapSettings.size());
        Optional<LdapSetting> optionalLdapSetting =
                ldapSettings.stream().filter(settings -> settings.getKey().equals("uniqueID")).findFirst();
        Assertions.assertTrue(optionalLdapSetting.isPresent());
        LdapSetting ldapSetting = optionalLdapSetting.get();
        String actualJson = JsonUtils.getInstance().valueToString(ldapSetting);
        String expectedJson = """
                {
                  "key": "uniqueID",
                  "enabled": true,
                  "ldapUrl": "ldap://myserver:myport/dc=sampledomain,dc=com",
                  "userDnPattern": "userpattern1",
                  "search": {
                    "managerDn": "manager1",
                    "managerPassword": "managerpass1",
                    "searchBase": "searchBase1",
                    "searchFilter": "searchFilter1",
                    "searchSubTree": false
                  },
                  "autoCreateUser": true,
                  "emailAttribute": "email@jfrog.com",
                  "ldapPoisoningProtection": true,
                  "allowUserToAccessProfile": true,
                  "pagingSupportEnabled": false
                }""";
        JSONAssert.assertEquals(expectedJson, actualJson, true);
        optionalLdapSetting =
                ldapSettings.stream().filter(settings -> settings.getKey().equals("uniqueID2")).findFirst();
        Assertions.assertTrue(optionalLdapSetting.isPresent());
        ldapSetting = optionalLdapSetting.get();
        actualJson = JsonUtils.getInstance().valueToString(ldapSetting);
        expectedJson = """
                {
                  "key": "uniqueID2",
                  "enabled": true,
                  "autoCreateUser": true,
                  "emailAttribute": "email2@jfrog.com",
                  "ldapPoisoningProtection": true,
                  "allowUserToAccessProfile": false,
                  "pagingSupportEnabled": true
                }""";
        JSONAssert.assertEquals(expectedJson, actualJson, true);
        verify(accessService, never()).removeLdapSetting(any());
    }

    @Test
    void testPartialPopulateExistingLdapSettings() throws JSONException {
        Map<String, Object> ldapSettingsMap = Map.of(
                SECURITY, Map.of(SECURITY_LDAP_SETTINGS,
                        Map.of("uniqueID", Map.of(
                                        LdapConfigKeys.POISONING_PROTECTION, Boolean.TRUE.toString(),
                                        LdapConfigKeys.URL, "ldap://myserver:myport/dc=sampledomain,dc=com",
                                        LdapConfigKeys.SEARCH, Map.of(
                                                LdapConfigKeys.Search.MANAGER_DN, "manager1",
                                                LdapConfigKeys.Search.MANAGER_PASSWORD, "managerpass1",
                                                LdapConfigKeys.Search.SEARCH_BASE, "searchBase1",
                                                LdapConfigKeys.Search.SEARCH_FILTER, "searchFilter1",
                                                LdapConfigKeys.Search.SEARCH_SUB_TREE, Boolean.FALSE.toString()
                                        ),
                                        LdapConfigKeys.USER_DN_PATTERN, "userpattern1",
                                        LdapConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, Boolean.TRUE.toString(),
                                        LdapConfigKeys.AUTO_CREATE_USER, Boolean.TRUE.toString(),
                                        LdapConfigKeys.PAGING_SUPPORT_ENABLED, Boolean.FALSE.toString()
                                )
                        )
                ));

        Map<String, Object> ldapSettingsMapMutable = toMutableMap(ldapSettingsMap);

        LdapSetting existingLdapSetting = generateLdapSetting("uniqueID");

        when(accessService.getLdapSettings()).thenReturn(List.of(existingLdapSetting));

        int movedResources = ldapHandler.migrate(ldapSettingsMapMutable);

        Assertions.assertEquals(11, movedResources);

        ArgumentCaptor<List<LdapSetting>> ldapSettingsArgumentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(accessService).upsertLdapSettings(ldapSettingsArgumentsCaptor.capture());
        List<LdapSetting> ldapSettings = ldapSettingsArgumentsCaptor.getValue();
        Assertions.assertFalse(ldapSettings.isEmpty());
        Assertions.assertEquals(1, ldapSettings.size());
        Optional<LdapSetting> optionalLdapSetting =
                ldapSettings.stream().filter(settings -> settings.getKey().equals("uniqueID")).findFirst();
        Assertions.assertTrue(optionalLdapSetting.isPresent());
        LdapSetting ldapSetting = optionalLdapSetting.get();
        String actualJson = JsonUtils.getInstance().valueToString(ldapSetting);
        String expectedJson = """
                {
                  "key": "uniqueID",
                  "enabled": false,
                  "ldapUrl": "ldap://myserver:myport/dc=sampledomain,dc=com",
                  "userDnPattern": "userpattern1",
                  "search": {
                    "managerDn": "manager1",
                    "managerPassword": "managerpass1",
                    "searchBase": "searchBase1",
                    "searchFilter": "searchFilter1",
                    "searchSubTree": false
                  },
                  "autoCreateUser": true,
                  "emailAttribute": "existingEmail@jfrog.com",
                  "ldapPoisoningProtection": true,
                  "allowUserToAccessProfile": true,
                  "pagingSupportEnabled": false
                }""";
        JSONAssert.assertEquals(expectedJson, actualJson, true);
        verify(accessService, never()).removeLdapSetting(any());
    }

    @Test
    void testRemoveAllLdapSettings() {
        Map<String, Object> ldapSettingsMap = Map.of(
                SECURITY, Map.of(SECURITY_LDAP_SETTINGS, Map.of())
        );

        Map<String, Object> ldapSettingsMapMutable = toMutableMap(ldapSettingsMap);

        LdapSetting existingLdapSetting1 = generateLdapSetting("uniqueID1");
        LdapSetting existingLdapSetting2 = generateLdapSetting("uniqueID2");
        LdapSetting existingLdapSetting3 = generateLdapSetting("uniqueID3");
        when(accessService.getLdapSettings()).thenReturn(
                List.of(existingLdapSetting1, existingLdapSetting2, existingLdapSetting3));

        int movedResources = ldapHandler.migrate(ldapSettingsMapMutable);

        Assertions.assertEquals(3, movedResources);
        verify(accessService, never()).upsertLdapSettings(any());
        verify(accessService, times(3)).removeLdapSetting(any());
    }

    @Test
    void testRemoveLdapSettingsByKey() {
        Map<String, Object> ldapSettingsMap = Map.of(
                SECURITY, Map.of(SECURITY_LDAP_SETTINGS,
                        Map.of("uniqueID", Map.of())
                )
        );

        Map<String, Object> ldapSettingsMapMutable = toMutableMap(ldapSettingsMap);

        LdapSetting existingLdapSetting = generateLdapSetting("uniqueID");
        when(accessService.getLdapSettings()).thenReturn(List.of(existingLdapSetting));

        int movedResources = ldapHandler.migrate(ldapSettingsMapMutable);

        Assertions.assertEquals(1, movedResources);
        verify(accessService).removeLdapSetting("uniqueID");
        verify(accessService, never()).upsertLdapSettings(any());
    }

    @Test
    void testRemoveNonExistentLdapSettingsByKeyThrowsException() {
        Map<String, Object> ldapSettingsMap = Map.of(
                SECURITY, Map.of(SECURITY_LDAP_SETTINGS,
                        Map.of("uniqueID", Map.of())
                )
        );

        Map<String, Object> ldapSettingsMapMutable = toMutableMap(ldapSettingsMap);

        when(accessService.getLdapSettings()).thenReturn(List.of());

        Assertions.assertThrows(IllegalArgumentException.class, () -> ldapHandler.migrate(ldapSettingsMapMutable),
                "Attempted to delete: uniqueID but it does not exist");
        verify(accessService, never()).upsertLdapSettings(any());
        verify(accessService, never()).removeLdapSetting(any());
    }

    @Test
    void testRemoveInnerLdapSearch() throws JSONException {
        Map<String, Object> ldapSettingsMap = Map.of(
                SECURITY, Map.of(SECURITY_LDAP_SETTINGS,
                        Map.of("uniqueID", Map.of(LdapConfigKeys.SEARCH, Map.of()))
                )
        );

        Map<String, Object> ldapSettingsMapMutable = toMutableMap(ldapSettingsMap);

        LdapSetting existingLdapSetting = generateLdapSetting("uniqueID");
        when(accessService.getLdapSettings()).thenReturn(List.of(existingLdapSetting));

        int movedResources = ldapHandler.migrate(ldapSettingsMapMutable);

        Assertions.assertEquals(1, movedResources);

        ArgumentCaptor<List<LdapSetting>> ldapSettingsArgumentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(accessService).upsertLdapSettings(ldapSettingsArgumentsCaptor.capture());
        List<LdapSetting> ldapSettings = ldapSettingsArgumentsCaptor.getValue();
        Assertions.assertFalse(ldapSettings.isEmpty());
        Assertions.assertEquals(1, ldapSettings.size());
        Optional<LdapSetting> optionalLdapSetting =
                ldapSettings.stream().filter(settings -> settings.getKey().equals("uniqueID")).findFirst();
        Assertions.assertTrue(optionalLdapSetting.isPresent());
        LdapSetting ldapSetting = optionalLdapSetting.get();
        String actualJson = JsonUtils.getInstance().valueToString(ldapSetting);
        existingLdapSetting.setSearch(new SearchPattern());
        String expectedJson = JsonUtils.getInstance().valueToString(existingLdapSetting);
        JSONAssert.assertEquals(expectedJson, actualJson, true);
        verify(accessService, never()).removeLdapSetting(any());
    }

    @Test
    void testTryToRemoveAllNonExistentSettingsThrowsException() {
        Map<String, Object> ldapSettingsMap = Map.of(
                SECURITY, Map.of(SECURITY_LDAP_SETTINGS, Map.of())
        );
        Map<String, Object> ldapSettingsMapMutable = toMutableMap(ldapSettingsMap);
        when(accessService.getLdapSettings()).thenReturn(List.of());

        Assertions.assertThrows(IllegalArgumentException.class, () -> ldapHandler.migrate(ldapSettingsMapMutable),
                "Attempted to delete:" + SECURITY_LDAP_SETTINGS + " but it does not exist");
        verify(accessService, never()).upsertLdapSettings(any());
        verify(accessService, never()).removeLdapSetting(any());
    }

    @Test
    void testEmptyConfigMapNoop() {
        int movedResources = ldapHandler.migrate(Map.of());
        Assertions.assertEquals(0, movedResources);
        verify(accessService, never()).upsertLdapSettings(any());
        verify(accessService, never()).removeLdapSetting(any());
    }

    private static Map<String, Object> toMutableMap(Map<String, Object> ldapSettingsMap) {
        return JsonUtils.getInstance().readValue(
                JsonUtils.getInstance().valueToString(ldapSettingsMap), HashMap.class);
    }

    @Nonnull
    private static LdapSetting generateLdapSetting(String settingsKey) {
        LdapSetting existingLdapSetting = new LdapSetting();
        existingLdapSetting.setKey(settingsKey);
        existingLdapSetting.setEmailAttribute("existingEmail@jfrog.com");
        existingLdapSetting.setLdapPoisoningProtection(false);
        existingLdapSetting.setLdapUrl("ldap://existingServer:port/dc=sampleDomain,dc=com");
        SearchPattern searchPattern = new SearchPattern();
        searchPattern.setSearchBase("existingSearchBase");
        searchPattern.setSearchFilter("existingSearchFilter");
        searchPattern.setManagerDn("existingManagerDn");
        searchPattern.setManagerPassword("existingManagerPassword");
        searchPattern.setSearchSubTree(true);
        existingLdapSetting.setSearch(searchPattern);
        existingLdapSetting.setUserDnPattern("existingUserDnPattern");
        existingLdapSetting.setAllowUserToAccessProfile(false);
        existingLdapSetting.setAutoCreateUser(false);
        existingLdapSetting.setPagingSupportEnabled(true);
        existingLdapSetting.setEnabled(false);
        return existingLdapSetting;
    }
}