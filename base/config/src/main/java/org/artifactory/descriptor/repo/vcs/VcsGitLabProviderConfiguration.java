package org.artifactory.descriptor.repo.vcs;

/**
 * @author Yuval Ohayon
 */
public class VcsGitLabProviderConfiguration extends VcsProviderConfiguration {
    public VcsGitLabProviderConfiguration() {
        super("GitLab", "",
                "api/v4/projects/{0}%2F{1}" +
                        "/repository/archive.{3}?sha={2}",
                "api/v4/projects/{0}%2F{1}" +
                        "/repository/files/{2}/raw?ref={3}"
        );
    }
}