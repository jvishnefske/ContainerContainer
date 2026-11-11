package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;

/**
 * @author Tamar Rosen
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class PypiConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(skip = true)
    private String indexContextPath;
    @MetadataInfo(skip = true)
    private String packagesContextPath;
    @MetadataInfo(skip = true)
    private String pyPIRegistryUrl = "https://pypi.org";
    @MetadataInfo(skip = true)
    private String repositorySuffix = "simple";
}
