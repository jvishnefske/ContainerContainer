package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;

/**
 * @author Tamar Rosen
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class CargoConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(defaultValue = "false")
    private boolean cargoAnonymousAccess;
    @MetadataInfo(defaultValue = "true")
    private boolean cargoInternalIndex = true;
    @MetadataInfo(skip = true)
    private GitConfiguration git;
}
