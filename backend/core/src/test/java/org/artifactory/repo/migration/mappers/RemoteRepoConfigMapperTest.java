package org.artifactory.repo.migration.mappers;

import org.artifactory.common.ConstantValues;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.descriptor.repo.NugetGeneralConfig;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.remote.CustomHttpHeader;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.PackageTypeConfigFields;
import org.jfrog.common.ResourceUtils;
import org.jfrog.common.YamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_PODS_CDN_URL;
import static org.artifactory.repo.migration.mappers.RemoteRepoConfigMapper.mapRemoteRepoConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author shahafg
 */
class RemoteRepoConfigMapperTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void mapRemoteRepoConfigTest() {
        RemoteRepoConfig localRepoConfig = RemoteRepoConfig.create("cool-repo", RepoType.Maven);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString("/config/remote_repo_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, StringToObjectHashMap.class);
        mapRemoteRepoConfig(localRepoConfig, newValues, descriptor);
        assertTrue(localRepoConfig.isReal());
    }

    @Test
    void mapRemoteRepoConfigWithIllegalValuesTest() {
        RemoteRepoConfig localRepoConfig = RemoteRepoConfig.create("cool-repo", RepoType.Maven);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString("/config/remote_repo_invalid_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, StringToObjectHashMap.class);
        assertThrows(IllegalArgumentException.class, () -> mapRemoteRepoConfig(localRepoConfig, newValues, descriptor));
    }

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT_PODS_CDN_URL, "https://cdn-url.org"})
    void addCdnUrlToCocoapods(String cdnUrl) {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("Coco-remote", RepoType.CocoaPods);
        CentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        Map<String, Object> cocoapodsConfigFields = new HashMap<>();
        cocoapodsConfigFields.put(PackageTypeConfigFields.COCOA_PODS_CDN_URL.getKey(), cdnUrl);
        assertEquals(4, remoteRepoConfig.getPackageTypeConfig().size());
        assertNull(remoteRepoConfig.getPackageTypeConfig().get(PackageTypeConfigFields.COCOA_PODS_CDN_URL.getKey()));
        mapRemoteRepoConfig(remoteRepoConfig,
                Map.of("cocoaPodsConfiguration", cocoapodsConfigFields), descriptor);
        assertEquals(5, remoteRepoConfig.getPackageTypeConfig().size());
        assertEquals(remoteRepoConfig.getPackageTypeConfig().get(PackageTypeConfigFields.COCOA_PODS_CDN_URL.getKey()),
                cdnUrl);
    }

    @Test
    void mapDockerRemoteRepoConfigTest() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("docker-remote", RepoType.Docker);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString("/config/remote_repo_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, StringToObjectHashMap.class);
        mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor);
        assertTrue(remoteRepoConfig.getPackageTypeConfig().containsKey("dockerProjectId"));
    }

    @Test
    void mapGoRemoteRepoConfigTest() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("go-remote", RepoType.Go);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString("/config/remote_go_artifactory_repo_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, StringToObjectHashMap.class);
        mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor);
        assertEquals(RepoType.Go, remoteRepoConfig.getPackageType());
        assertEquals("https://proxy.golang.org", remoteRepoConfig.getRepoTypeConfig().getUrl());
        assertEquals("GIT", remoteRepoConfig.getPackageTypeConfig().get("vcsType"));
        assertEquals("ARTIFACTORY", remoteRepoConfig.getPackageTypeConfig().get("vcsGitProvider"));
    }

    @Test
    void mapCargoRemoteRepoConfigTest() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("cargo-remote", RepoType.Cargo);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString("/config/remote_cargo_repo_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, StringToObjectHashMap.class);
        mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor);
        assertEquals(RepoType.Cargo, remoteRepoConfig.getPackageType());
        assertEquals("true", remoteRepoConfig.getPackageTypeConfig().get("cargoAnonymousAccess"));
        assertEquals("true", remoteRepoConfig.getPackageTypeConfig().get("cargoInternalIndex"));
        assertEquals("https://notdefault.com", remoteRepoConfig.getPackageTypeConfig().get("gitRegistryUrl"));

    }

    @Test
    void mapNugetSymbolServerUrlTest() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("nuget-remote", RepoType.NuGet);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor =
                new RepositoriesGeneralConfigDescriptor();
        NugetGeneralConfig nugetGeneralConfig = new NugetGeneralConfig();
        nugetGeneralConfig.setEnableGlobalNormalization(false);
        repositoriesGeneralConfigDescriptor.setNuget(nugetGeneralConfig);
        configDescriptor.setRepositoriesGeneralConfig(repositoriesGeneralConfigDescriptor);
        String patchYaml = ResourceUtils.getResourceAsString("/config/nuget_remote_repo_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, StringToObjectHashMap.class);
        mapRemoteRepoConfig(remoteRepoConfig, newValues, configDescriptor);
        assertEquals(RepoType.NuGet, remoteRepoConfig.getPackageType());
        assertTrue(remoteRepoConfig.getPackageTypeConfig().containsKey("symbolServerUrl"));
        assertEquals("https://symbols.nuget.org/download/symbols",
                remoteRepoConfig.getPackageTypeConfig().get("symbolServerUrl"));
    }

    @Test
    void mapHexUrlTest() {
        String hexUrl = "http://sample";
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("hex-remote", RepoType.Hex);
        CentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        assertNull(remoteRepoConfig.getPackageTypeConfig().get(PackageTypeConfigFields.HEX_REGISTRY_URL.getKey()));
        Map<String, Object> hexConfigFields = new HashMap<>();
        hexConfigFields.put(PackageTypeConfigFields.HEX_REGISTRY_URL.getKey(), hexUrl);
        mapRemoteRepoConfig(remoteRepoConfig, Map.of("hexConfiguration", hexConfigFields), descriptor);
        assertEquals(RepoType.Hex, remoteRepoConfig.getPackageType());
        assertTrue(
                remoteRepoConfig.getPackageTypeConfig().containsKey(PackageTypeConfigFields.HEX_REGISTRY_URL.getKey()));
        assertFalse(remoteRepoConfig.getPackageTypeConfig()
                .containsKey(PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY.getKey()));
        assertEquals(remoteRepoConfig.getPackageTypeConfig().get(PackageTypeConfigFields.HEX_REGISTRY_URL.getKey()),
                hexUrl);
    }

    @Test
    void mapGoGitlabResolveSubgroupsTest() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("hex-remote", RepoType.Go);
        CentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        assertFalse(Boolean.parseBoolean(remoteRepoConfig.getPackageTypeConfig()
                .get(PackageTypeConfigFields.GITLAB_RESOLVE_SUBGROUPS.getKey())));
        mapRemoteRepoConfig(remoteRepoConfig, Map.of(PackageTypeConfigFields.GITLAB_RESOLVE_SUBGROUPS.getKey(), true),
                descriptor);
        assertEquals(RepoType.Go, remoteRepoConfig.getPackageType());
        assertTrue(
                remoteRepoConfig.getPackageTypeConfig()
                        .containsKey(PackageTypeConfigFields.GITLAB_RESOLVE_SUBGROUPS.getKey()));
        assertTrue(Boolean.parseBoolean(remoteRepoConfig.getPackageTypeConfig()
                .get(PackageTypeConfigFields.GITLAB_RESOLVE_SUBGROUPS.getKey())));
    }

    @Test
    void mapNugetEnableNormalizedVersionTestWhenNormalizationIsEnabled() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("nuget-remote", RepoType.NuGet);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor =
                new RepositoriesGeneralConfigDescriptor();
        NugetGeneralConfig nugetGeneralConfig = new NugetGeneralConfig();
        nugetGeneralConfig.setEnableGlobalNormalization(false);
        repositoriesGeneralConfigDescriptor.setNuget(nugetGeneralConfig);
        configDescriptor.setRepositoriesGeneralConfig(repositoriesGeneralConfigDescriptor);
        String patchYaml = ResourceUtils.getResourceAsString("/config/nuget_remote_repo_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, StringToObjectHashMap.class);

        mapRemoteRepoConfig(remoteRepoConfig, newValues, configDescriptor);

        assertEquals(RepoType.NuGet, remoteRepoConfig.getPackageType());
        assertTrue(remoteRepoConfig.getPackageTypeConfig().containsKey("symbolServerUrl"));
        assertEquals("true",
                remoteRepoConfig.getPackageTypeConfig().get("enableNormalizedVersion"));
    }

    @Test
    void mapNugetEnableNormalizedVersionTestWhenNormalizationIsNotPresent() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("nuget-remote", RepoType.NuGet);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString("/config/nuget_remote_repo_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, StringToObjectHashMap.class);
        ((Map<String, Object>) newValues.get("nuget")).remove("enableNormalizedVersion");
        mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor);

        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        assertEquals(RepoType.NuGet, remoteRepoConfig.getPackageType());
        assertTrue(packageTypeConfig.containsKey("symbolServerUrl"));
        assertFalse(packageTypeConfig.containsKey("enableNormalizedVersion"));
    }

    private static class StringToObjectHashMap extends HashMap<String, Object> {

    }

    // ===== buildFederationConfig tests =====

    @Test
    void buildFederationConfig_shouldEarlyReturn_whenFeatureFlagDisabled() {
        // Arrange - feature flag is disabled by default
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo", RepoType.Generic);
        CentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        Map<String, Object> newValues = Map.of(
                "url", "http://localhost",
                "members", List.of(
                        Map.of("url", "http://federated/repo", "enabled", true, "mode", "READ_WRITE")
                )
        );

        // Act
        mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor);

        // Assert - federation members should NOT be set because feature flag is disabled
        assertTrue(remoteRepoConfig.getBaseConfig().getFederationConfig().getMembers().isEmpty());
    }

    @Test
    void buildFederationConfig_shouldSetMembers_whenFeatureFlagEnabled() {
        // Arrange - enable the feature flag
        setStringSystemProperty(ConstantValues.remoteRepoFederationEnabled, "true");

        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo", RepoType.Generic);
        CentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("url", "http://localhost");
        newValues.put("members", List.of(
                Map.of("url", "http://federated/repo", "enabled", true, "mode", "READ_WRITE")
        ));

        // Act
        mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor);

        // Assert - federation members should be set
        List<FederatedMember> members = remoteRepoConfig.getBaseConfig().getFederationConfig().getMembers();
        assertEquals(1, members.size());
        assertEquals("http://federated/repo", members.get(0).getUrlOrTemplate());
    }

    // ===== customHttpHeaders (mapper) =====

    @Test
    void mapRemoteRepoConfig_shouldSetEmptyList_whenCustomHttpHeadersNull() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote", RepoType.Generic);
        CentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("url", "http://localhost");
        newValues.put("customHttpHeaders", null);

        mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor);

        List<CustomHttpHeader> headers = remoteRepoConfig.getRepoTypeConfig().getCustomHttpHeaders();
        assertNotNull(headers);
        assertTrue(headers.isEmpty());
    }

    @Test
    void mapRemoteRepoConfig_shouldSetEmptyList_whenCustomHttpHeadersEmptyList() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote", RepoType.Generic);
        CentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("url", "http://localhost");
        newValues.put("customHttpHeaders", new ArrayList<Map<String, Object>>());

        mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor);

        List<CustomHttpHeader> headers = remoteRepoConfig.getRepoTypeConfig().getCustomHttpHeaders();
        assertNotNull(headers);
        assertTrue(headers.isEmpty());
    }

    @Test
    void mapRemoteRepoConfig_shouldMapCustomHttpHeaders_whenOneHeaderInMap() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote", RepoType.Generic);
        CentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("url", "http://localhost");
        newValues.put("customHttpHeaders", List.of(
                Map.of("name", "X-Api-Key", "value", "secret123", "sensitive", false)));

        mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor);

        List<CustomHttpHeader> headers = remoteRepoConfig.getRepoTypeConfig().getCustomHttpHeaders();
        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertEquals("x-api-key", headers.get(0).getName());
        assertEquals("secret123", headers.get(0).getValue());
        assertFalse(headers.get(0).isSensitive());
    }

    @Test
    void mapRemoteRepoConfig_shouldMapCustomHttpHeaders_whenYamlPatchUsesIndexedMap() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote", RepoType.Generic);
        CentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        Map<String, Object> first = new HashMap<>();
        first.put("name", "X-First");
        first.put("value", "a");
        first.put("sensitive", false);
        Map<String, Object> second = new HashMap<>();
        second.put("name", "X-Second");
        second.put("value", "b");
        second.put("sensitive", true);
        Map<String, Object> indexed = new HashMap<>();
        indexed.put("1", second);
        indexed.put("0", first);
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("url", "http://localhost");
        newValues.put("customHttpHeaders", indexed);

        mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor);

        List<CustomHttpHeader> headers = remoteRepoConfig.getRepoTypeConfig().getCustomHttpHeaders();
        assertEquals(2, headers.size());
        assertEquals("x-first", headers.get(0).getName());
        assertEquals("a", headers.get(0).getValue());
        assertEquals("x-second", headers.get(1).getName());
        assertEquals("b", headers.get(1).getValue());
        assertTrue(headers.get(1).isSensitive());
    }

    @Test
    void mapRemoteRepoConfig_shouldMapCustomHttpHeaders_whenSensitiveAsStringTrue() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote", RepoType.Generic);
        CentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("url", "http://localhost");
        newValues.put("customHttpHeaders", List.of(
                Map.of("name", "X-Token", "value", "v", "sensitive", "true")));

        mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor);

        List<CustomHttpHeader> headers = remoteRepoConfig.getRepoTypeConfig().getCustomHttpHeaders();
        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertTrue(headers.get(0).isSensitive());
    }

    @Test
    void mapRemoteRepoConfig_shouldRejectInvalidCustomHttpHeaderNames() {
        setStringSystemProperty(ConstantValues.remoteRepoCustomHttpHeadersEnabled, "true");
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote", RepoType.Generic);
        CentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("url", "http://localhost");
        newValues.put("customHttpHeaders", List.of(
                Map.of("name", "X-Api Key", "value", "v", "sensitive", false)));

        ConfigurationException thrown = assertThrows(ConfigurationException.class,
                () -> mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor));
        assertTrue(thrown.getMessage().contains("RFC 7230"));
    }

    @Test
    void mapRemoteRepoConfig_shouldMapCustomHttpHeaders_whenMultipleHeaders() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote", RepoType.Generic);
        CentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("url", "http://localhost");
        newValues.put("customHttpHeaders", List.of(
                Map.of("name", "X-A", "value", "v1", "sensitive", false),
                Map.of("name", "X-B", "value", "v2", "sensitive", true)));

        mapRemoteRepoConfig(remoteRepoConfig, newValues, descriptor);

        List<CustomHttpHeader> headers = remoteRepoConfig.getRepoTypeConfig().getCustomHttpHeaders();
        assertNotNull(headers);
        assertEquals(2, headers.size());
        assertEquals("x-a", headers.get(0).getName());
        assertEquals("v1", headers.get(0).getValue());
        assertFalse(headers.get(0).isSensitive());
        assertEquals("x-b", headers.get(1).getName());
        assertEquals("v2", headers.get(1).getValue());
        assertTrue(headers.get(1).isSensitive());
    }
}