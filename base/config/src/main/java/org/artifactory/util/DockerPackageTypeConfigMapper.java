package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.DockerApiVersion;
import org.artifactory.repo.packagetype.configuration.DockerConfiguration;

import java.util.Optional;

import static org.artifactory.repo.packagetype.configuration.DockerConfiguration.DEFAULT_DOCKER_TAG_RETENTION;

/**
 * @author Tamar Rosen
 */
public interface DockerPackageTypeConfigMapper extends BasePackageTypeConfigMapper<DockerConfiguration> {
    String DOCKER_TAG_RETENTION = "dockerTagRetention";
    String BLOCK_PUSHING_SCHEMA_1 = "blockPushingSchema1";
    String DOCKER_API_VERSION = "dockerApiVersion";
    String MAX_UNIQUE_TAGS = "maxUniqueTags";
    String DOCKER_PROJECT_ID = "dockerProjectId";
    String RESOLVE_DOCKER_TAGS_BY_TIMESTAMP = "resolveDockerTagsByTimestamp";
    String CACHING_LOCAL_FOREIGN_LAYERS_ENABLED = "cachingLocalForeignLayersEnabled";

    DockerPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        if (packageTypeConfig == null) {
            throw new IllegalArgumentException("Package type config can not be null");
        }
        DockerConfiguration config = new DockerConfiguration();

        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        String dockerTagRetention = packageTypeConfig.get(DOCKER_TAG_RETENTION);
        String blockPushingSchema1 = packageTypeConfig.get(BLOCK_PUSHING_SCHEMA_1);
        String dockerApiVersion = packageTypeConfig.get(DOCKER_API_VERSION);
        String maxUniqueTags = packageTypeConfig.get(MAX_UNIQUE_TAGS);
        String resolveDockerTagsByTimestamp = packageTypeConfig.get(RESOLVE_DOCKER_TAGS_BY_TIMESTAMP);
        String cachingLocalForeignLayersEnabled = packageTypeConfig.get(CACHING_LOCAL_FOREIGN_LAYERS_ENABLED);
        String dockerProjectId = packageTypeConfig.get(DOCKER_PROJECT_ID);

        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        config.setExternalDependencies(ExternalDependenciesPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig));
        config.setDockerTagRetention(StringUtils.isBlank(dockerTagRetention) ? DEFAULT_DOCKER_TAG_RETENTION :
                Integer.parseInt(dockerTagRetention));
        config.setMaxUniqueTags(StringUtils.isBlank(maxUniqueTags) ? 0 : Integer.parseInt(maxUniqueTags));
        config.setResolveDockerTagsByTimestamp(Boolean.parseBoolean(resolveDockerTagsByTimestamp));
        Optional.ofNullable(blockPushingSchema1)
                .map(Boolean::parseBoolean)
                .ifPresent(config::setBlockPushingSchema1);
        Optional.ofNullable(dockerApiVersion)
                .map(DockerApiVersion::valueOf)
                .ifPresent(config::setDockerApiVersion);
        Optional.ofNullable(cachingLocalForeignLayersEnabled)
                .map(Boolean::parseBoolean)
                .ifPresent(config::setCachingLocalForeignLayersEnabled);
        config.setDockerProjectId(StringUtils.isBlank(dockerProjectId) ? StringUtils.EMPTY : dockerProjectId);
        return config;
    };
}