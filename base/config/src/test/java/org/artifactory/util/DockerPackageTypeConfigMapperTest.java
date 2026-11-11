package org.artifactory.util;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.DockerApiVersion;
import org.artifactory.repo.packagetype.configuration.DockerConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.artifactory.repo.packagetype.configuration.DockerConfiguration.DEFAULT_DOCKER_TAG_RETENTION;

/**
 * @author Tamar Rosen
 */
class DockerPackageTypeConfigMapperTest {
    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(DockerPackageTypeConfigMapper.MODEL_VERSION, "1",
                        DockerPackageTypeConfigMapper.DOCKER_TAG_RETENTION, "2",
                        DockerPackageTypeConfigMapper.BLOCK_PUSHING_SCHEMA_1, "false",
                        DockerPackageTypeConfigMapper.DOCKER_API_VERSION, "V1",
                        DockerPackageTypeConfigMapper.MAX_UNIQUE_TAGS, "1",
                        DockerPackageTypeConfigMapper.RESOLVE_DOCKER_TAGS_BY_TIMESTAMP, "true",
                        DockerPackageTypeConfigMapper.DOCKER_PROJECT_ID, "projectId",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED, "true",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF,
                        "remote-repo",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS,
                        "[\"1\",\"2\",\"3\"]");

        DockerConfiguration dockerConfiguration = DockerPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertEquals(1, dockerConfiguration.getModelVersion());
        Assertions.assertEquals(2, dockerConfiguration.getDockerTagRetention());
        Assertions.assertFalse(dockerConfiguration.isBlockPushingSchema1());
        Assertions.assertEquals(DockerApiVersion.V1, dockerConfiguration.getDockerApiVersion());
        Assertions.assertEquals(1, dockerConfiguration.getMaxUniqueTags());
        Assertions.assertTrue(dockerConfiguration.isResolveDockerTagsByTimestamp());
        Assertions.assertTrue(dockerConfiguration.getExternalDependencies().isEnabled());
        Assertions.assertEquals("remote-repo", dockerConfiguration.getExternalDependencies().getRemoteRepoRef());
        Assertions.assertEquals("1", dockerConfiguration.getExternalDependencies().getPatterns().get(0));
        Assertions.assertEquals("2", dockerConfiguration.getExternalDependencies().getPatterns().get(1));
        Assertions.assertEquals("3", dockerConfiguration.getExternalDependencies().getPatterns().get(2));
        Assertions.assertEquals("projectId", dockerConfiguration.getDockerProjectId());
    }

    @Test
    void testAllNullsMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(DockerPackageTypeConfigMapper.MODEL_VERSION, null);
        packageTypeConfig.put(DockerPackageTypeConfigMapper.DOCKER_TAG_RETENTION, null);
        packageTypeConfig.put(DockerPackageTypeConfigMapper.BLOCK_PUSHING_SCHEMA_1, null);
        packageTypeConfig.put(DockerPackageTypeConfigMapper.DOCKER_API_VERSION, null);
        packageTypeConfig.put(DockerPackageTypeConfigMapper.MAX_UNIQUE_TAGS, null);
        packageTypeConfig.put(DockerPackageTypeConfigMapper.RESOLVE_DOCKER_TAGS_BY_TIMESTAMP, null);
        packageTypeConfig.put(DockerPackageTypeConfigMapper.DOCKER_PROJECT_ID, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, null);

        DockerConfiguration dockerConfiguration = DockerPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(0L, dockerConfiguration.getModelVersion());
        Assertions.assertEquals(DEFAULT_DOCKER_TAG_RETENTION, dockerConfiguration.getDockerTagRetention());
        Assertions.assertTrue(dockerConfiguration.isBlockPushingSchema1());
        Assertions.assertEquals(DockerApiVersion.V2, dockerConfiguration.getDockerApiVersion());
        Assertions.assertEquals(0, dockerConfiguration.getMaxUniqueTags());
        Assertions.assertFalse(dockerConfiguration.isResolveDockerTagsByTimestamp());
        Assertions.assertFalse(dockerConfiguration.getExternalDependencies().isEnabled());
        Assertions.assertNull(dockerConfiguration.getExternalDependencies().getRemoteRepoRef());
        Assertions.assertNull(dockerConfiguration.getExternalDependencies().getPatterns());
        Assertions.assertEquals(StringUtils.EMPTY, dockerConfiguration.getDockerProjectId());
    }

    @Test
    void testEmptyListMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, "[]");

        DockerConfiguration dockerConfiguration = DockerPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertNotNull(dockerConfiguration.getExternalDependencies().getPatterns());
        Assertions.assertTrue(dockerConfiguration.getExternalDependencies().getPatterns().isEmpty());
    }
}