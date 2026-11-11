package org.artifactory.repo.migration.mappers;


import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.ResourceUtils;
import org.jfrog.common.YamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.stream.Stream;

import static org.artifactory.repo.migration.mappers.LocalRepoConfigMapper.mapLocalRepoConfig;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Hussein Alshqerat
 */
class LocalRepoConfigMapperTest extends ArtifactoryHomeBoundJUnitTest {

    @ParameterizedTest
    @MethodSource("params")
    void mapLocalRepoConfigTest(String yamlFileName, XrayNotAvailableMode xrayNotAvailableMode) {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("cool-repo", RepoType.Maven);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString(yamlFileName);
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, HashMap.class);
        mapLocalRepoConfig(localRepoConfig, newValues, descriptor);
        assertTrue(localRepoConfig.isLocal());
        assertSame(localRepoConfig.getSecurityConfig().getXrayNotAvailableMode(), xrayNotAvailableMode);
    }

    @Test
    void mapLocalRepoConfigWithIllegalValuesTest() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("cool-repo", RepoType.Maven);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        String patchYaml = ResourceUtils.getResourceAsString("/config/local_repo_invalid_config.yaml");
        HashMap<String, Object> newValues = YamlUtils.getInstance().readValue(patchYaml, HashMap.class);
        assertThrows(IllegalArgumentException.class, () -> mapLocalRepoConfig(localRepoConfig, newValues, descriptor));
    }

    private static Stream<Arguments> params() {
        return Stream.of(
                Arguments.of("/config/local_repo_config.yaml", XrayNotAvailableMode.FAIL_OPEN),
                Arguments.of("/config/local_repo_without_xray_failure_mode.yaml", null)
        );
    }
}