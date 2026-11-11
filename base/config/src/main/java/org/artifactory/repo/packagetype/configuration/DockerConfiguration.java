package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;
import org.artifactory.repo.packagetype.DockerApiVersion;
import org.artifactory.repo.packagetype.ExternalDependenciesConfig;

/**
 * @author Uriah Levy
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class DockerConfiguration {
    @MetadataInfo(skip = true)
    public static final int DEFAULT_DOCKER_TAG_RETENTION = 1;
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(defaultValue = "1")
    private int dockerTagRetention = DEFAULT_DOCKER_TAG_RETENTION;
    @MetadataInfo(defaultValue = "true")
    private boolean blockPushingSchema1 = true;
    @MetadataInfo(defaultValue = "V2")
    private DockerApiVersion dockerApiVersion = DockerApiVersion.V2;
    @MetadataInfo(defaultValue = "0")
    private int maxUniqueTags;
    @MetadataInfo(skip = true)
    private boolean resolveDockerTagsByTimestamp;
    @MetadataInfo(skip = true)
    private boolean cachingLocalForeignLayersEnabled = false;
    @MetadataInfo(skip = true)
    private ExternalDependenciesConfig externalDependencies;
    @MetadataInfo(skip = true)
    private String dockerProjectId;
}
