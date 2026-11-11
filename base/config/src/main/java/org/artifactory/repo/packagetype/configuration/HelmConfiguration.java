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
public class HelmConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(skip = true)
    private String chartsBaseUrl;
    @MetadataInfo(skip = true)
    private boolean useNamespaces;
    @MetadataInfo(skip = true)
    private ExternalDependenciesConfig externalDependencies;
    @MetadataInfo(skip = true)
    private boolean forceMetadataNameVersion;
    @MetadataInfo(skip = true)
    private boolean forceNonDuplicateChart;
}
