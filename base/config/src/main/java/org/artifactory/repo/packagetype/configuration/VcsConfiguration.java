package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.repo.vcs.VcsType;

/**
 * @author Tamar Rosen
 */
@Data
public class VcsConfiguration {
    private long modelVersion;
    private VcsType type = VcsType.GIT;
    private VcsGitConfiguration git = new VcsGitConfiguration();
}
