package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;
import org.jfrog.common.config.validation.NotUnicode;

/**
 * @author Uriah Levy
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class YumConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(defaultValue = "false")
    private boolean calculateYumMetadata;
    @MetadataInfo(defaultValue = "false")
    private boolean enableFileListsIndexing;
    @MetadataInfo(defaultValue = "0")
    private int yumRootDepth;
    @NotUnicode
    @MetadataInfo
    private String yumGroupFileNames;
}
