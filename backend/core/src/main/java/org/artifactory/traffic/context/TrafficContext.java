package org.artifactory.traffic.context;

import lombok.Data;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPath;

/**
 * @author Andrey Tashlitsky
 */
@Data
public class TrafficContext {

    private String originRepoKey;
    private String originProjectKey;
    private RepoConfig originRepoConfig;
    private String username;
    private RepoPath repoPath;
    private boolean trafficLogV3Flow;
}
