package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;

/**
 * @author Tamar Rosen
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class NuGetConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(skip = true)
    private String feedContextPath = "";
    @MetadataInfo(skip = true)
    private String downloadContextPath = "";
    @MetadataInfo(skip = true)
    private String v3FeedUrl = "";
    @MetadataInfo(defaultValue = "false")
    private boolean forceNugetAuthentication;
    @MetadataInfo(defaultValue = "false")
    private boolean enableNormalizedVersion;
    @MetadataInfo(skip = true)
    private String symbolServerUrl = "";
}
