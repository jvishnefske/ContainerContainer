package org.artifactory.repo.migration.mappers;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.NugetGeneralConfig;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.exception.InvalidOperationException;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.service.NugetRepoConfigValidator;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.DoesNotExistException;
import org.jfrog.common.ResourceUtils;
import org.jfrog.common.YamlUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.repo.migration.mappers.VirtualRepoConfigMapper.mapVirtualRepoConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
@ExtendWith(MockitoExtension.class)
class VirtualRepoConfigMapperTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private RepositoryConfigService repositoryConfigService;

    @BeforeEach
    void setUp() {
        getBound().setProperty(
                ConstantValues.virtualRepoFederationEnabled.getPropertyName(),
                String.valueOf(false));
    }

    @Test
    void mapVirtualRepoConfigTest() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("cool-repo", RepoType.Maven);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString("/config/virtual_repo_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, HashMap.class);
        adjustRepositoriesFormatToYml(newValues);
        mapVirtualRepoConfig(virtualRepoConfig, newValues, descriptor, repositoryConfigService);
        assertFalse(virtualRepoConfig.isReal());
    }

    @Test
    void mapVirtualRepoConfig_shouldNotSetFederationMembers_whenFlagDisabled() {
        // Arrange
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("cool-repo", RepoType.Maven);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        HashMap<String, Object> newValues = new HashMap<>();
        newValues.put("members", List.of(
                Map.of("url", "http://server-a/artifactory/cool-repo", "enabled", true)
        ));

        // Act
        mapVirtualRepoConfig(virtualRepoConfig, newValues, descriptor, repositoryConfigService);

        // Assert
        assertFalse(virtualRepoConfig.getBaseConfig().isFederated());
        assertTrue(virtualRepoConfig.getBaseConfig().getFederationConfig().getMembers().isEmpty());
    }

    @Test
    void mapVirtualRepoConfig_shouldSetFederationMembers_whenMembersProvided() {
        // Arrange
        getBound().setProperty(
                ConstantValues.virtualRepoFederationEnabled.getPropertyName(),
                String.valueOf(true));
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("cool-repo", RepoType.Maven);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        HashMap<String, Object> newValues = new HashMap<>();
        newValues.put("members", List.of(
                Map.of("url", "http://server-a/artifactory/cool-repo", "enabled", true),
                Map.of("url", "http://server-b/artifactory/cool-repo", "enabled", false)
        ));

        // Act
        mapVirtualRepoConfig(virtualRepoConfig, newValues, descriptor, repositoryConfigService);

        // Assert
        assertEquals(2, virtualRepoConfig.getBaseConfig().getFederationConfig().getMembers().size());
        assertEquals("http://server-a/artifactory/cool-repo",
                virtualRepoConfig.getBaseConfig().getFederationConfig().getMembers().get(0).getUrlOrTemplate());
        assertTrue(virtualRepoConfig.getBaseConfig().getFederationConfig().getMembers().get(0).isEnabled());
        assertEquals("http://server-b/artifactory/cool-repo",
                virtualRepoConfig.getBaseConfig().getFederationConfig().getMembers().get(1).getUrlOrTemplate());
        assertFalse(virtualRepoConfig.getBaseConfig().getFederationConfig().getMembers().get(1).isEnabled());
    }

    @Test
    void mapNugetVirtualRepoConfigTestWhenNormalisationIsEnabled() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("cool-repo", RepoType.NuGet);

        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor =
                new RepositoriesGeneralConfigDescriptor();
        NugetGeneralConfig nugetGeneralConfig = new NugetGeneralConfig();
        nugetGeneralConfig.setEnableGlobalNormalization(false);
        repositoriesGeneralConfigDescriptor.setNuget(nugetGeneralConfig);
        configDescriptor.setRepositoriesGeneralConfig(repositoriesGeneralConfigDescriptor);

        String patchYaml = ResourceUtils.getResourceAsString("/config/nuget_virtual_repo_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, HashMap.class);
        adjustRepositoriesFormatToYml(newValues);
        mapVirtualRepoConfig(virtualRepoConfig, newValues, configDescriptor, repositoryConfigService);

        assertTrue(Boolean.parseBoolean(virtualRepoConfig.getPackageTypeConfig().get("enableNormalizedVersion")));
        assertTrue(Boolean.parseBoolean(virtualRepoConfig.getPackageTypeConfig().get("forceNugetAuthentication")));
    }

    private static void adjustRepositoriesFormatToYml(HashMap<String, Object> newValues) {
        ArrayList repositoriesNames = (ArrayList) newValues.remove("repositories");
        HashMap<String, String> repositories = new LinkedHashMap<>();
        for (int i = 0; i < repositoriesNames.size(); i++) {
            repositories.put("{" + i + "}", repositoriesNames.get(i).toString());
        }
        newValues.putIfAbsent("repositories", repositories);
    }

    @Test
    void mapNugetVirtualRepoConfigTestWhenNormalisationIsEnabledInVirtualAndSelectedRepoIsNotEnabled() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("cool-repo", RepoType.NuGet);
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor =
                new RepositoriesGeneralConfigDescriptor();
        NugetGeneralConfig nugetGeneralConfig = new NugetGeneralConfig();
        nugetGeneralConfig.setEnableGlobalNormalization(false);
        repositoriesGeneralConfigDescriptor.setNuget(nugetGeneralConfig);
        configDescriptor.setRepositoriesGeneralConfig(repositoriesGeneralConfigDescriptor);

        String patchYaml = ResourceUtils.getResourceAsString("/config/nuget_virtual_repo_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, HashMap.class);

        LocalRepoConfig localRepoConfig = mock(LocalRepoConfig.class);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        Map<String, String> localPackageTypeConfig = new HashMap<>();
        Map<String, String> remotePackageTypeConfig = new HashMap<>();
        localPackageTypeConfig.put("enableNormalizedVersion", "true");
        remotePackageTypeConfig.put("enableNormalizedVersion", "false"); // mismatch

        when(repositoryConfigService.getRepoConfigByKey("my-local-repo-1")).thenReturn(localRepoConfig);
        when(repositoryConfigService.getRepoConfigByKey("my-remote-repo-1")).thenReturn(remoteRepoConfig);
        when(localRepoConfig.getPackageTypeConfig()).thenReturn(localPackageTypeConfig);
        when(remoteRepoConfig.getPackageTypeConfig()).thenReturn(remotePackageTypeConfig);
        adjustRepositoriesFormatToYml(newValues);

        mapVirtualRepoConfig(virtualRepoConfig, newValues, configDescriptor, repositoryConfigService);

        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                () -> NugetRepoConfigValidator.validateNugetPackageTypeConfig(virtualRepoConfig,
                        repositoryConfigService));
        assertEquals("Nuget Repository cool-repo must have only normalized repositories", ex.getMessage());
    }

    /**
     * Test should throw doesnot exists exception, when any one of the selected repo  is not available in the
     * repo config
     */
    @Test
    void mapNugetVirtualRepoConfigTestWhenNormalisationIsEnabledInVirtualAndSelectedRepoIsNotFound() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("cool-repo", RepoType.NuGet);
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor =
                new RepositoriesGeneralConfigDescriptor();
        NugetGeneralConfig nugetGeneralConfig = new NugetGeneralConfig();
        nugetGeneralConfig.setEnableGlobalNormalization(false);
        repositoriesGeneralConfigDescriptor.setNuget(nugetGeneralConfig);
        configDescriptor.setRepositoriesGeneralConfig(repositoriesGeneralConfigDescriptor);

        String patchYaml = ResourceUtils.getResourceAsString("/config/nuget_virtual_repo_config.yaml");
        LocalRepoConfig localRepoConfig = mock(LocalRepoConfig.class);
        Map<String, String> localPackageTypeConfig = new HashMap<>();
        localPackageTypeConfig.put("enableNormalizedVersion", "true");
        when(repositoryConfigService.getRepoConfigByKey("my-local-repo-1")).thenReturn(localRepoConfig);
        when(localRepoConfig.getPackageTypeConfig()).thenReturn(localPackageTypeConfig);
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, HashMap.class);
        adjustRepositoriesFormatToYml(newValues);

        mapVirtualRepoConfig(virtualRepoConfig, newValues, configDescriptor, repositoryConfigService);

        DoesNotExistException doesNotExistException = assertThrows(DoesNotExistException.class,
                () -> NugetRepoConfigValidator.validateNugetPackageTypeConfig(virtualRepoConfig,
                        repositoryConfigService));
        assertEquals("Selected Repo keymy-remote-repo-1 not found", doesNotExistException.getMessage());
    }

    @Test
    void mapNugetVirtualRepoConfigTestWhenEnableNormalizedVersionIsNotPresent() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("cool-repo", RepoType.NuGet);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString("/config/nuget_virtual_repo_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, HashMap.class);
        newValues.remove("enableNormalizedVersion");
        adjustRepositoriesFormatToYml(newValues);

        mapVirtualRepoConfig(virtualRepoConfig, newValues, descriptor, repositoryConfigService);

        assertFalse(virtualRepoConfig.getPackageTypeConfig().containsKey("enableNormalizedVersion"));
    }

    @Test
    void mapVirtualRepoConfigWithIllegalValuesTest() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("cool-repo", RepoType.Maven);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString("/config/virtual_repo_invalid_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, HashMap.class);

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                () -> mapVirtualRepoConfig(virtualRepoConfig, newValues, descriptor, repositoryConfigService));
        assertEquals("No values under repositories", illegalArgumentException.getMessage());
    }
}