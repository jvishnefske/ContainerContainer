package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.VcsUrlBuilder;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.jfrog.common.config.validation.NotUnicode;

/**
 * @author Tamar Rosen
 */
@Data
public class VcsGitConfiguration {
    private VcsGitProvider provider = VcsGitProvider.GITHUB;

    @NotUnicode
    private String downloadUrl;

    public VcsGitConfiguration() {
    }

    public VcsGitConfiguration(VcsGitProvider provider) {
        this.provider = provider;
    }

    /**
     * Constructs ResourceDownloadUrl
     *
     * @param user       git user
     * @param repository git repository
     * @param file       a file to download
     * @param version    content branch/tag
     * @return ResourceDownloadUrl
     */
    public String buildResourceDownloadUrl(String user, String repository, String file, String version) {
        return VcsUrlBuilder.resourceDownloadUrl(provider, user, repository, file, version);
    }

    /**
     * Constructs ResourceDownloadUrl
     *
     * @param user       git user
     * @param repository git repository
     * @param version    content branch/tag
     * @return ResourceDownloadUrl
     */
    public String buildReleaseDownloadUrl(String user, String repository, String version, String ballType) {
        return VcsUrlBuilder.releaseDownloadUrl(provider, user, repository, version, ballType);
    }

    /**
     * Constructs RepositoryDownloadUrl
     *
     * @param gitOrg  git user
     * @param gitRepo git repository
     * @param version content branch/tag
     * @param fileExt file ext
     * @return RepositoryDownloadUrl
     */
    public String buildRepositoryDownloadUrl(String gitOrg, String gitRepo, String version, String fileExt) {
        String url = StringUtils.isNotBlank(downloadUrl) ? downloadUrl : provider.getRepositoryDownloadUrl();
        return VcsUrlBuilder.repositoryDownloadUrl(url, gitOrg, gitRepo, version, fileExt);
    }
}
