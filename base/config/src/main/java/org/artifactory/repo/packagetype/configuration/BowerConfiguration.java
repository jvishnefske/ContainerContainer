package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;
import org.artifactory.repo.packagetype.ExternalDependenciesConfig;

/**
 * @author Tamar Rosen
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class BowerConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(skip = true)
    private ExternalDependenciesConfig externalDependencies;
    @MetadataInfo(skip = true)
    private String bowerRegistryUrl = "https://registry.bower.io";
    @MetadataInfo(skip = true)
    private VcsConfiguration vcs;
}
