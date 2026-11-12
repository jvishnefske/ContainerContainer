package org.artifactory.repo.migration.mappers;

import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.NugetGeneralConfig;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.model.BaseConfig;
import org.artifactory.model.FederationConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.federated.FederatedMember;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.util.PackageTypeConfigFields.ENABLE_NUGET_NORMALIZED_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepoConfigMigrationPatchUtilsTest {

    @Mock
    private RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor;

    @Test
    void testBuildLocalPackageTypeConfig() {
        Map<String, String> config = new HashMap<>();
        RepoConfigMigrationPatchUtils.buildLocalPackageTypeConfig(config, RepoType.TerraformBackend,
                Map.of(ENCRYPT_STATES.getKey(), "false"), repositoriesGeneralConfigDescriptor);
        Assertions.assertEquals("false", config.get(ENCRYPT_STATES.getKey()));
    }

    /**
     * To test package type specific config enableNormalizedVersion getting added for nuget package type
     */
    @Test
    void testBuildLocalPackageTypeConfigEnableNugetNormalizedVersion() {
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor =
                new RepositoriesGeneralConfigDescriptor();
        NugetGeneralConfig nugetGeneralConfig = new NugetGeneralConfig();
        nugetGeneralConfig.setEnableGlobalNormalization(false);
        repositoriesGeneralConfigDescriptor.setNuget(nugetGeneralConfig);
        configDescriptor.setRepositoriesGeneralConfig(repositoriesGeneralConfigDescriptor);
        Map<String, String> config = new HashMap<>();
        RepoConfigMigrationPatchUtils.buildLocalPackageTypeConfig(config, RepoType.NuGet,
                Map.of(ENABLE_NUGET_NORMALIZED_VERSION.getKey(), "true"), repositoriesGeneralConfigDescriptor);
        Assertions.assertEquals("true", config.get(ENABLE_NUGET_NORMALIZED_VERSION.getKey()));
    }

    @Test
    void testBuildLocalPackageTypeConfigWithCargoInternalIndex() {
        Map<String, String> config = new HashMap<>();
        RepoConfigMigrationPatchUtils.buildLocalPackageTypeConfig(config, RepoType.Cargo,
                Map.of("cargoInternalIndex", "true"), repositoriesGeneralConfigDescriptor);
        Assertions.assertEquals("true", config.get("cargoInternalIndex"));
    }

    @Test
    void testBuildLocalPackageTypeConfigGenericCarriesPluginPackageType() {
        Map<String, String> config = new HashMap<>();
        RepoConfigMigrationPatchUtils.buildLocalPackageTypeConfig(config, RepoType.Generic,
                Map.of("pluginPackageType", "simpleidx", "dockerApiVersion", "V2"),
                repositoriesGeneralConfigDescriptor);
        assertEquals(Map.of("pluginPackageType", "simpleidx"), config);
    }

    // ===== parseFederationMembers tests =====

    @Test
    void parseFederationMembers_shouldReturnMembers_whenMembersKeyExists() {
        // Arrange - "members" key exists as a Map
        Map<String, Object> newValues = Map.of(
                "members", Map.of(
                        "0", Map.of("url", "http://server1/repo", "enabled", true, "mode", "READ_WRITE"),
                        "1", Map.of("url", "http://server2/repo", "enabled", false, "mode", "READ_ONLY")
                )
        );

        // Act
        List<FederatedMember> result = RepoConfigMigrationPatchUtils.parseFederationMembers(newValues);

        // Assert
        assertEquals(2, result.size());
        assertEquals("http://server1/repo", result.get(0).getUrlOrTemplate());
        assertTrue(result.get(0).isEnabled());
    }

    @Test
    void parseFederationMembers_shouldFallbackToFederatedMembers_whenMembersKeyIsNull() {
        // Arrange - "members" is null, but "federatedMembers" exists
        Map<String, Object> newValues = Map.of(
                "federatedMembers", Map.of(
                        "0", Map.of("url", "http://fallback/repo", "enabled", true, "mode", "READ_WRITE")
                )
        );

        // Act
        List<FederatedMember> result = RepoConfigMigrationPatchUtils.parseFederationMembers(newValues);

        // Assert
        assertEquals(1, result.size());
        assertEquals("http://fallback/repo", result.get(0).getUrlOrTemplate());
    }

    @Test
    void parseFederationMembers_shouldExtractFromFederationConfig_whenMembersAndFederatedMembersAreNull() {
        // Arrange - both "members" and "federatedMembers" are null, but "federationConfig.members" exists
        Map<String, Object> newValues = Map.of(
                "federationConfig", Map.of(
                        "members", Map.of(
                                "0", Map.of("url", "http://nested/repo", "enabled", true, "mode", "READ_WRITE")
                        )
                )
        );

        // Act
        List<FederatedMember> result = RepoConfigMigrationPatchUtils.parseFederationMembers(newValues);

        // Assert
        assertEquals(1, result.size());
        assertEquals("http://nested/repo", result.get(0).getUrlOrTemplate());
    }

    @Test
    void parseFederationMembers_shouldReturnEmptyList_whenNoMembersFound() {
        // Arrange - no members in any location
        Map<String, Object> newValues = Map.of("someOtherKey", "someValue");

        // Act
        List<FederatedMember> result = RepoConfigMigrationPatchUtils.parseFederationMembers(newValues);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void parseFederationMembers_shouldReturnEmptyList_whenInputIsEmpty() {
        // Arrange
        Map<String, Object> newValues = new HashMap<>();

        // Act
        List<FederatedMember> result = RepoConfigMigrationPatchUtils.parseFederationMembers(newValues);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void parseFederationMembers_shouldParseList_whenMembersIsListFormat() {
        // Arrange - "members" is a List (not a Map)
        Map<String, Object> newValues = Map.of(
                "members", List.of(
                        Map.of("url", "http://list-server1/repo", "enabled", true, "mode", "READ_WRITE"),
                        Map.of("url", "http://list-server2/repo", "enabled", false, "mode", "READ_ONLY")
                )
        );

        // Act
        List<FederatedMember> result = RepoConfigMigrationPatchUtils.parseFederationMembers(newValues);

        // Assert
        assertEquals(2, result.size());
        assertEquals("http://list-server1/repo", result.get(0).getUrlOrTemplate());
        assertEquals("http://list-server2/repo", result.get(1).getUrlOrTemplate());
    }

    @Test
    void parseFederationMembers_shouldSkipNonMapItems_whenListContainsMixedTypes() {
        // Arrange - List contains both Map and non-Map items
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("members", List.of(
                Map.of("url", "http://valid/repo", "enabled", true, "mode", "READ_WRITE"),
                "invalid-string-item"
        ));

        // Act
        List<FederatedMember> result = RepoConfigMigrationPatchUtils.parseFederationMembers(newValues);

        // Assert - only the valid Map item should be parsed
        assertEquals(1, result.size());
        assertEquals("http://valid/repo", result.get(0).getUrlOrTemplate());
    }

    @Test
    void parseFederationMembers_shouldReturnEmptyList_whenFederationConfigIsNotMap() {
        // Arrange - "federationConfig" exists but is not a Map
        Map<String, Object> newValues = Map.of("federationConfig", "not-a-map");

        // Act
        List<FederatedMember> result = RepoConfigMigrationPatchUtils.parseFederationMembers(newValues);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void parseFederationMembers_shouldSetAllMemberFields_whenAllFieldsProvided() {
        // Arrange - explicitly test all switch cases: enabled, mode, url
        Map<String, Object> newValues = Map.of(
                "members", List.of(
                        Map.of("url", "http://test/repo", "enabled", false, "mode", "READ_ONLY")
                )
        );

        // Act
        List<FederatedMember> result = RepoConfigMigrationPatchUtils.parseFederationMembers(newValues);

        // Assert - verify all three fields are set correctly
        assertEquals(1, result.size());
        FederatedMember member = result.get(0);
        assertEquals("http://test/repo", member.getUrlOrTemplate());
        assertFalse(member.isEnabled());
        assertEquals("READ_ONLY", member.getMode());
    }

    // ===== setFederationMembers tests =====

    @Test
    void setFederationMembers_shouldSetMembers_whenMembersExist() {
        // Arrange
        RepoConfig config = mock(RepoConfig.class);
        BaseConfig baseConfig = mock(BaseConfig.class);
        FederationConfig federationConfig = mock(FederationConfig.class);
        when(config.getBaseConfig()).thenReturn(baseConfig);
        when(baseConfig.getFederationConfig()).thenReturn(federationConfig);

        Map<String, Object> newValues = Map.of(
                "members", List.of(
                        Map.of("url", "http://server/repo", "enabled", true, "mode", "READ_WRITE")
                )
        );

        // Act
        RepoConfigMigrationPatchUtils.setFederationMembers(config, newValues);

        // Assert
        verify(federationConfig).setMembers(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void setFederationMembers_shouldNotSetMembers_whenMembersEmpty() {
        // Arrange
        RepoConfig config = mock(RepoConfig.class);
        Map<String, Object> newValues = new HashMap<>();

        // Act
        RepoConfigMigrationPatchUtils.setFederationMembers(config, newValues);

        // Assert - getBaseConfig should never be called if members are empty
        verify(config, org.mockito.Mockito.never()).getBaseConfig();
    }
}