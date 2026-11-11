package org.artifactory.descriptor.repo.vcs;

/**
 * @author Yuval Ohayon
 */
public class VcsGitHubEnterpriseProviderConfiguration extends VcsProviderConfiguration {
    public VcsGitHubEnterpriseProviderConfiguration() {
        super("GitHubEnterprise",
                "",
                "api/v3/repos/{0}/{1}/contents/{2}?ref={3}"
        );
    }

}
