package org.artifactory.repo;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * @author Eli Skoran
 */
@Data
@GenerateDiffFunction
public class CdnRedirectRepoConfig {
    @MetadataInfo(defaultValue = "false")
    private boolean enabled = false;

    public CdnRedirectRepoConfig() {
    }

    public CdnRedirectRepoConfig(CdnRedirectRepoConfig cdnRedirectRepoConfig) {
        this.enabled = cdnRedirectRepoConfig.isEnabled();
    }
}
