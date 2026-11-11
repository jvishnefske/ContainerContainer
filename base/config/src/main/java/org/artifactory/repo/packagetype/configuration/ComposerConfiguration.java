package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;

/**
 * @author Tamar Rosen
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class ComposerConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(defaultValue = "false")
    private boolean enableComposerV1Indexing = false;
    @MetadataInfo(skip = true)
    private String composerRegistryUrl = "https://packagist.org";
    @MetadataInfo(skip = true)
    private VcsConfiguration vcs;
}
