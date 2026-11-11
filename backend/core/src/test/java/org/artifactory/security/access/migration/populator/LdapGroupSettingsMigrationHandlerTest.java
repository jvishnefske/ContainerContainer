package org.artifactory.security.access.migration.populator;

import jakarta.annotation.Nonnull;
import org.artifactory.config.AccessConfigKeys;
import org.artifactory.descriptor.security.ldap.group.LdapGroupPopulatorStrategies;
import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
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

import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SECURITY_LDAP_GROUP_SETTINGS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LdapGroupSettingsMigrationHandlerTest {

    @Mock
    private AccessService accessService;
    private AutoCloseable mocks;

    private LdapGroupSettingsMigrationHandler ldapGroupHandler;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ldapGroupHandler = new LdapGroupSettingsMigrationHandler(accessService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testPopulateNewLdapGroupSettings() throws JSONException {
        Map<String, Object> ldapGroupSettingsMutable = toMutableMap(generateLdapGroupSettings());

        when(accessService.getLdapGroupSettings()).thenReturn(List.of());

        int movedResources = ldapGroupHandler.migrate(ldapGroupSettingsMutable);

        Assertions.assertEquals(10, movedResources);

        ArgumentCaptor<List<LdapGroupSetting>> ldapGroupSettingsArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(accessService).upsertLdapGroupSettings(ldapGroupSettingsArgumentCaptor.capture());
        List<LdapGroupSetting> ldapGroupSettings = ldapGroupSettingsArgumentCaptor.getValue();
        Assertions.assertFalse(ldapGroupSettings.isEmpty());
        Assertions.assertEquals(2, ldapGroupSettings.size());
        Optional<LdapGroupSetting> optionalLdapGroupSetting =
                ldapGroupSettings.stream().filter(ldapGroupSetting -> ldapGroupSetting.getName().equals("uniqueID"))
                        .findFirst();
        Assertions.assertTrue(optionalLdapGroupSetting.isPresent());
        LdapGroupSetting ldapGroupSetting = optionalLdapGroupSetting.get();
        String actualJson = JsonUtils.getInstance().valueToString(ldapGroupSetting);
        String expectedJson = """
                {
                  "name": "uniqueID",
                  "groupBaseDn": "groupBase1",
                  "groupNameAttribute": "groupName",
                  "groupMemberAttribute": "uniqueMember",
                  "subTree": true,
                  "filter": "filter1",
                  "descriptionAttribute": "desc1",
                  "strategy": "DYNAMIC",
                  "enabledLdap": "enabled1",
                  "forceAttributeSearch": true
                }""";
        JSONAssert.assertEquals(expectedJson, actualJson, true);

        optionalLdapGroupSetting =
                ldapGroupSettings.stream().filter(setting -> setting.getName().equals("uniqueID2")).findFirst();
        Assertions.assertTrue(optionalLdapGroupSetting.isPresent());
        ldapGroupSetting = optionalLdapGroupSetting.get();
        actualJson = JsonUtils.getInstance().valueToString(ldapGroupSetting);
        expectedJson = """
                {
                  "name": "uniqueID2",
                  "groupBaseDn": "",
                  "groupNameAttribute": "cn",
                  "subTree": false,
                  "descriptionAttribute": "desc2",
                  "strategy": "STATIC",
                  "enabledLdap": "",
                  "forceAttributeSearch": false
                }""";
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    void testPartialPopulateExistingLdapGroupSettings() throws JSONException {
        Map<String, Object> ldapGroupSettingsMap = Map.of(
                SECURITY, Map.of(SECURITY_LDAP_GROUP_SETTINGS,
                        Map.of("uniqueID", Map.of(
                                AccessConfigKeys.LdapGroupConfigKeys.FILTER, "filter1",
                                AccessConfigKeys.LdapGroupConfigKeys.GROUP_BASE_DN, "groupBase1",
                                AccessConfigKeys.LdapGroupConfigKeys.GROUP_MEMBER_ATTR, "uniqueMember",
                                AccessConfigKeys.LdapGroupConfigKeys.GROUP_NAME_ATTR, "groupName",
                                AccessConfigKeys.LdapGroupConfigKeys.SUB_TREE, Boolean.TRUE.toString(),
                                AccessConfigKeys.LdapGroupConfigKeys.FORCE_ATTR_SEARCH, Boolean.TRUE.toString()
                        ))));

        Map<String, Object> ldapGroupSettingsMutable = toMutableMap(ldapGroupSettingsMap);

        LdapGroupSetting existingLdapGroupSetting = generateLdapGroupSetting("uniqueID");

        when(accessService.getLdapGroupSettings()).thenReturn(List.of(existingLdapGroupSetting));

        int movedResources = ldapGroupHandler.migrate(ldapGroupSettingsMutable);

        Assertions.assertEquals(6, movedResources);

        ArgumentCaptor<List<LdapGroupSetting>> ldapGroupSettingsArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(accessService).upsertLdapGroupSettings(ldapGroupSettingsArgumentCaptor.capture());
        List<LdapGroupSetting> ldapGroupSettings = ldapGroupSettingsArgumentCaptor.getValue();
        Assertions.assertFalse(ldapGroupSettings.isEmpty());
        Assertions.assertEquals(1, ldapGroupSettings.size());
        Optional<LdapGroupSetting> optionalLdapGroupSetting =
                ldapGroupSettings.stream().filter(ldapGroupSetting -> ldapGroupSetting.getName().equals("uniqueID"))
                        .findFirst();
        Assertions.assertTrue(optionalLdapGroupSetting.isPresent());
        LdapGroupSetting ldapGroupSetting = optionalLdapGroupSetting.get();
        String actualJson = JsonUtils.getInstance().valueToString(ldapGroupSetting);
        String expectedJson = """
                {
                  "name": "uniqueID",
                  "groupBaseDn": "groupBase1",
                  "groupNameAttribute": "groupName",
                  "groupMemberAttribute": "uniqueMember",
                  "subTree": true,
                  "filter": "filter1",
                  "descriptionAttribute": "existingDesc",
                  "strategy": "HIERARCHICAL",
                  "enabledLdap": "existingEnabled",
                  "forceAttributeSearch": true
                }""";
        JSONAssert.assertEquals(expectedJson, actualJson, true);
        verify(accessService, never()).removeLdapGroupOnly(anyString());
    }

    @Test
    void testRemoveAllLdapGroupSettings() {
        Map<String, Object> ldapSettingsMapMutable = toMutableMap(Map.of(
                SECURITY, Map.of(SECURITY_LDAP_GROUP_SETTINGS, Map.of())
        ));

        LdapGroupSetting existingLdapGroupSetting1 = generateLdapGroupSetting("uniqueID1");
        LdapGroupSetting existingLdapGroupSetting2 = generateLdapGroupSetting("uniqueID2");
        LdapGroupSetting existingLdapGroupSetting3 = generateLdapGroupSetting("uniqueID3");
        when(accessService.getLdapGroupSettings()).thenReturn(
                List.of(existingLdapGroupSetting1, existingLdapGroupSetting2, existingLdapGroupSetting3));

        int movedResources = ldapGroupHandler.migrate(ldapSettingsMapMutable);

        Assertions.assertEquals(3, movedResources);
        verify(accessService, times(3)).removeLdapGroupOnly(anyString());
        verify(accessService, never()).upsertLdapGroupSettings(any());
    }

    @Test
    void testRemoveLdapGroupSettingsByKey() {
        Map<String, Object> ldapGroupSettingsMapMutable = toMutableMap(Map.of(
                SECURITY, Map.of(SECURITY_LDAP_GROUP_SETTINGS,
                        Map.of("uniqueID", Map.of())
                )
        ));

        LdapGroupSetting existingLdapGroupSetting = generateLdapGroupSetting("uniqueID");
        when(accessService.getLdapGroupSettings()).thenReturn(List.of(existingLdapGroupSetting));

        int movedResources = ldapGroupHandler.migrate(ldapGroupSettingsMapMutable);

        Assertions.assertEquals(1, movedResources);
        verify(accessService).removeLdapGroupOnly("uniqueID");
        verify(accessService, never()).upsertLdapGroupSettings(any());
    }

    @Test
    void testRemoveNonExistentLdapGroupSettingsByKeyThrowsException() {
        Map<String, Object> ldapSettingsMapMutable = toMutableMap(Map.of(
                SECURITY, Map.of(SECURITY_LDAP_GROUP_SETTINGS,
                        Map.of("uniqueID", Map.of())
                )
        ));

        when(accessService.getLdapGroupSettings()).thenReturn(List.of());

        Assertions.assertThrows(IllegalArgumentException.class, () -> ldapGroupHandler.migrate(ldapSettingsMapMutable),
                "Attempted to delete: uniqueID but it does not exist");
        verify(accessService, never()).upsertLdapGroupSettings(any());
        verify(accessService, never()).removeLdapGroupOnly(anyString());
    }

    @Test
    void testTryToRemoveAllNonExistentSettingsThrowsException() {
        Map<String, Object> ldapGroupSettingsMapMutable = toMutableMap(Map.of(
                SECURITY, Map.of(SECURITY_LDAP_GROUP_SETTINGS, Map.of())
        ));
        when(accessService.getLdapSettings()).thenReturn(List.of());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> ldapGroupHandler.migrate(ldapGroupSettingsMapMutable),
                "Attempted to delete:" + SECURITY_LDAP_GROUP_SETTINGS + " but it does not exist");
        verify(accessService, never()).upsertLdapGroupSettings(any());
        verify(accessService, never()).removeLdapGroupOnly(anyString());
    }

    @Test
    void testEmptyConfigMapNoPopulate() {
        int movedResources = ldapGroupHandler.migrate(Map.of());
        Assertions.assertEquals(0, movedResources);
        verify(accessService, never()).upsertLdapGroupSettings(any());
        verify(accessService, never()).removeLdapGroupOnly(anyString());
    }

    @Nonnull
    private static Map<String, Object> generateLdapGroupSettings() {
        Map<String, Object> ldapGroupSettingsMap = Map.of(
                SECURITY, Map.of(SECURITY_LDAP_GROUP_SETTINGS,
                        Map.of("uniqueID", Map.of(
                                AccessConfigKeys.LdapGroupConfigKeys.DESC_ATTR, "desc1",
                                AccessConfigKeys.LdapGroupConfigKeys.ENABLED_LDAP, "enabled1",
                                AccessConfigKeys.LdapGroupConfigKeys.FILTER, "filter1",
                                AccessConfigKeys.LdapGroupConfigKeys.GROUP_BASE_DN, "groupBase1",
                                AccessConfigKeys.LdapGroupConfigKeys.GROUP_MEMBER_ATTR, "uniqueMember",
                                AccessConfigKeys.LdapGroupConfigKeys.GROUP_NAME_ATTR, "groupName",
                                AccessConfigKeys.LdapGroupConfigKeys.STRATEGY,
                                LdapGroupPopulatorStrategies.DYNAMIC.getType(),
                                AccessConfigKeys.LdapGroupConfigKeys.SUB_TREE, Boolean.TRUE.toString(),
                                AccessConfigKeys.LdapGroupConfigKeys.FORCE_ATTR_SEARCH, Boolean.TRUE.toString()
                        ), "uniqueID2", Map.of(
                                AccessConfigKeys.LdapGroupConfigKeys.DESC_ATTR, "desc2"

                        ))));
        return ldapGroupSettingsMap;
    }

    private LdapGroupSetting generateLdapGroupSetting(String settingKey) {
        LdapGroupSetting ldapGroupSetting = new LdapGroupSetting();
        ldapGroupSetting.setName(settingKey);
        ldapGroupSetting.setDescriptionAttribute("existingDesc");
        ldapGroupSetting.setEnabledLdap("existingEnabled");
        ldapGroupSetting.setFilter("existingFilter");
        ldapGroupSetting.setGroupBaseDn("existingGroupBaseDn");
        ldapGroupSetting.setGroupMemberAttribute("existingGroupMemberAtt");
        ldapGroupSetting.setGroupNameAttribute("existingGroupNameAtt");
        ldapGroupSetting.setStrategy(LdapGroupPopulatorStrategies.HIERARCHICAL);
        ldapGroupSetting.setSubTree(false);
        ldapGroupSetting.setForceAttributeSearch(false);
        return ldapGroupSetting;
    }

    private static Map<String, Object> toMutableMap(Map<String, Object> ldapSettingsMap) {
        return JsonUtils.getInstance().readValue(
                JsonUtils.getInstance().valueToString(ldapSettingsMap), HashMap.class);
    }
}