package org.artifactory.repo;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * @author Yuval Reches
 */
@Data
@GenerateDiffFunction
public class DownloadRedirectRepoConfig {
    @MetadataInfo(defaultValue = "false")
    private boolean enabled = false;

    public DownloadRedirectRepoConfig() {
    }

    public DownloadRedirectRepoConfig(DownloadRedirectRepoConfig downloadRedirectRepoConfig) {
        this.enabled = downloadRedirectRepoConfig.isEnabled();
    }
}
