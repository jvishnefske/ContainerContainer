package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;

/**
 * @author Volodymyr Shulga
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class HuggingFaceMLConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(skip = true)
    private boolean mlRepoLayout;
}
