package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;
import org.artifactory.util.PackageTypeConfigFields;

/**
 * @author Leela Padmaja
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class HexConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;

    @MetadataInfo(skip = true)
    private String hexRepositoryUrl = PackageTypeConfigFields.HEX_REGISTRY_URL.getDefaultValue();

    @MetadataInfo(skip = true)
    private String hexPublicKey = PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY.getDefaultValue();
}
