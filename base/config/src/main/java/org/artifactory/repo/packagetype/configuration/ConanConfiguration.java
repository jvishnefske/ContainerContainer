package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;

/**
 * @author Tamar Rosen
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class ConanConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(defaultValue = "false")
    private boolean forceConanAuthentication;
}
