package org.artifactory.api.request;

import org.artifactory.repo.RepoPath;

/**
 * @author Andrey Tashlitsky
 */
public class ReplicationChecksumDeployRequest extends InternalArtifactoryRequest {

    public ReplicationChecksumDeployRequest(RepoPath repoPath, boolean disableRedirect) {
        super(repoPath, disableRedirect);
    }
}
