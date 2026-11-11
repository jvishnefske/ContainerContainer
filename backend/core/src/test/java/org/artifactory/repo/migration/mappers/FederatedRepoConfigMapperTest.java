package org.artifactory.repo.migration.mappers;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.ResourceUtils;
import org.jfrog.common.YamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.repo.migration.mappers.FederatedRepoConfigMapper.mapFederatedRepoConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Hussein Alshqerat
 */
class FederatedRepoConfigMapperTest extends ArtifactoryHomeBoundJUnitTest {

    @ParameterizedTest
    @MethodSource("params")
    void mapLocalRepoConfigTest(String yamlFileName, XrayNotAvailableMode xrayNotAvailableMode) {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("cool-repo", RepoType.Maven);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString(yamlFileName);
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, HashMap.class);
        mapFederatedRepoConfig(federatedRepoConfig, newValues, descriptor);
        assertTrue(federatedRepoConfig.isLocal());
        assertSame(federatedRepoConfig.getSecurityConfig().getXrayNotAvailableMode(), xrayNotAvailableMode);
    }

    @Test
    void mapLocalRepoConfigWithIllegalValuesTest() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("cool-repo", RepoType.Maven);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString("/config/local_repo_invalid_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, HashMap.class);
        assertThrows(IllegalArgumentException.class,
                () -> mapFederatedRepoConfig(federatedRepoConfig, newValues, descriptor));
    }

    @Test
    void setFederatedMembers_shouldUpdateMembers_whenRepoAlreadyHasMembersInNewLocation() {
        // Arrange - repo with existing members in NEW location (simulates repo created via REST API)
        FederatedRepoConfig repo = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        FederatedMember existingMember = new FederatedMember();
        existingMember.setUrlOrTemplate("http://server-a/test-repo");
        existingMember.setEnabled(true);
        repo.setFederatedMembers(List.of(existingMember));

        // Verify initial state - member exists in new location
        assertEquals(1, repo.getFederatedMembers().size());
        assertEquals("http://server-a/test-repo", repo.getFederatedMembers().get(0).getUrlOrTemplate());

        // Act - YAML patch with updated members (adds a second member)
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("federatedMembers", Map.of(
                "0", Map.of("url", "http://server-a/test-repo", "enabled", true),
                "1", Map.of("url", "http://server-b/test-repo-b", "enabled", true)
        ));
        mapFederatedRepoConfig(repo, newValues, mock(CentralConfigDescriptor.class));

        // Assert - members should be updated (not ignored)
        List<FederatedMember> members = repo.getFederatedMembers();
        assertEquals(2, members.size());
        assertTrue(members.stream().anyMatch(m -> "http://server-a/test-repo".equals(m.getUrlOrTemplate())));
        assertTrue(members.stream().anyMatch(m -> "http://server-b/test-repo-b".equals(m.getUrlOrTemplate())));
    }

    private static Stream<Arguments> params() {
        return Stream.of(
                Arguments.of("/config/local_repo_config.yaml", XrayNotAvailableMode.FAIL_OPEN),
                Arguments.of("/config/local_repo_without_xray_failure_mode.yaml", null)
        );
    }

    @Test
    void mapFederatedRepoConfig_shouldSetModificationDate_whenModificationDateProvided() {
        // Arrange
        FederatedRepoConfig repo = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("modificationDate", 1234567890L);

        // Act
        mapFederatedRepoConfig(repo, newValues, mock(CentralConfigDescriptor.class));

        // Assert
        assertEquals(1234567890L, repo.getModificationDate());
    }
}