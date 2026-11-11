//package org.artifactory.security.access.migration.populator;
//
//import org.artifactory.descriptor.security.ldap.LdapSetting;
//import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
//import org.artifactory.security.access.AccessService;
//import org.jfrog.access.client.model.CrowdSetting;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.when;
//
//class PatchYamlAccessConfigMigrationServiceTest {
//
//    @Mock
//    private PatchYamlAccessConfigMigrationHandler populator1;
//    @Mock
//    private PatchYamlAccessConfigMigrationHandler populator2;
//    @Mock
//    private AccessService accessService;
//    private PatchYamlAccessConfigMigrationService yamlConfigMigrationPopulator;
//    private AutoCloseable mocks;
//
//    @BeforeEach
//    void setUp() {
//        mocks = MockitoAnnotations.openMocks(this);
//        yamlConfigMigrationPopulator = new PatchYamlAccessConfigMigrationService();
//        ReflectionTestUtils.setField(yamlConfigMigrationPopulator, "populators",
//                Set.of(populator1, populator2));
//    }
//
//    @AfterEach
//    void tearDown() throws Exception {
//        mocks.close();
//    }
//
//    @Test
//    void testPopulateFrom() {
//        Map<String, Object> configMap = Map.of();
//        when(populator1.migrate(configMap)).thenReturn(1);
//        when(populator2.migrate(configMap)).thenReturn(1);
//
//        int movedResources = yamlConfigMigrationPopulator.populateFrom(configMap);
//
//        Assertions.assertEquals(2, movedResources);
//    }
//
//    @Test
//    void testGetLdapSettings() {
//        PatchYamlLdapSettingsAccessConfigMigrationHandler ldapPopulator =
//                mock(PatchYamlLdapSettingsAccessConfigMigrationHandler.class);
//        LdapSetting ldapSetting = new LdapSetting();
//        when(ldapPopulator.getLdapSettings()).thenReturn(List.of(ldapSetting));
//
//        ReflectionTestUtils.setField(yamlConfigMigrationPopulator, "populators",
//                Set.of(populator1, populator2, ldapPopulator));
//
//        List<LdapSetting> ldapSettings = yamlConfigMigrationPopulator.getLdapSettings();
//        Assertions.assertNotNull(ldapSettings);
//        Assertions.assertFalse(ldapSettings.isEmpty());
//        Assertions.assertEquals(1, ldapSettings.size());
//        Assertions.assertEquals(ldapSetting, ldapSettings.get(0));
//    }
//
//    @Test
//    void testGetLdapGroupSettings() {
//        PatchYamlLdapGroupSettingsAccessConfigMigrationHandler ldapGroupPopulator =
//                mock(PatchYamlLdapGroupSettingsAccessConfigMigrationHandler.class);
//        LdapGroupSetting ldapGroupSetting = new LdapGroupSetting();
//        when(ldapGroupPopulator.getLdapGroupSettings()).thenReturn(List.of(ldapGroupSetting));
//
//        ReflectionTestUtils.setField(yamlConfigMigrationPopulator, "populators",
//                Set.of(populator1, populator2, ldapGroupPopulator));
//
//        List<LdapGroupSetting> ldapGroupSettings = yamlConfigMigrationPopulator.getLdapGroupSettings();
//        Assertions.assertNotNull(ldapGroupSettings);
//        Assertions.assertFalse(ldapGroupSettings.isEmpty());
//        Assertions.assertEquals(1, ldapGroupSettings.size());
//        Assertions.assertEquals(ldapGroupSetting, ldapGroupSettings.get(0));
//    }
//
//    @Test
//    void testGetCrowdSettings() {
//        PatchYamlCrowdSettingsAccessConfigMigrationHandler crowdPopulator =
//                mock(PatchYamlCrowdSettingsAccessConfigMigrationHandler.class);
//        CrowdSetting crowdSetting = CrowdSetting.builder().build();
//        when(crowdPopulator.getCrowdSettings()).thenReturn(crowdSetting);
//
//        ReflectionTestUtils.setField(yamlConfigMigrationPopulator, "populators",
//                Set.of(populator1, populator2, crowdPopulator));
//
//        CrowdSetting actualCrowdSettings = yamlConfigMigrationPopulator.getCrowdSettings();
//        Assertions.assertNotNull(actualCrowdSettings);
//        Assertions.assertEquals(crowdSetting, actualCrowdSettings);
//    }
//
//    @Test
//    void testGetPlatformConfigMap() {
//        PatchYamlPlatformConfigMigrationHandler platformPopulator =
//                mock(PatchYamlPlatformConfigMigrationHandler.class);
//        Map<String, Object> configMap = Map.of("propKey", "popValue");
//        when(platformPopulator.getPlatformConfigMap()).thenReturn(configMap);
//
//        ReflectionTestUtils.setField(yamlConfigMigrationPopulator, "populators",
//                Set.of(populator1, populator2, platformPopulator));
//
//        Map<String, Object> platformConfigMap = yamlConfigMigrationPopulator.getPlatformConfigMap();
//        Assertions.assertNotNull(platformConfigMap);
//        Assertions.assertFalse(platformConfigMap.isEmpty());
//        Assertions.assertEquals(1, platformConfigMap.size());
//        Assertions.assertEquals(configMap, platformConfigMap);
//    }
//}