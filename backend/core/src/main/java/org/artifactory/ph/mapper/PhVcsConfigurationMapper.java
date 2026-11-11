package org.artifactory.ph.mapper;

import com.jfrog.ph.acl.vcs.configuration.PhVcsGitConfiguration;
import com.jfrog.ph.acl.vcs.configuration.PhVcsGitProvider;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.repo.VcsGitConfiguration;

/**
 * Mapper from Artifactory VcsGit descriptor to PH VcsGit configuration
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PhVcsConfigurationMapper {

    public static PhVcsGitConfiguration from(VcsGitConfiguration vcsGitConfiguration) {
        PhVcsGitConfiguration phVcsGitConfig = new PhVcsGitConfiguration();
        phVcsGitConfig.setDownloadUrl(vcsGitConfiguration.getDownloadUrl());
        switch (vcsGitConfiguration.getProvider()) {
            case STASH -> phVcsGitConfig.setProvider(PhVcsGitProvider.STASH);
            case ARTIFACTORY -> phVcsGitConfig.setProvider(PhVcsGitProvider.ARTIFACTORY);
            case CUSTOM -> phVcsGitConfig.setProvider(PhVcsGitProvider.CUSTOM);
            case GITHUB -> phVcsGitConfig.setProvider(PhVcsGitProvider.GITHUB);
            case BITBUCKET -> phVcsGitConfig.setProvider(PhVcsGitProvider.BITBUCKET);
            case OLDSTASH -> phVcsGitConfig.setProvider(PhVcsGitProvider.OLDSTASH);
            case GITLAB -> phVcsGitConfig.setProvider(PhVcsGitProvider.GITLAB);
            case GITHUBENTERPRISE -> phVcsGitConfig.setProvider(PhVcsGitProvider.GITHUBENTERPRISE);
            case GOOGLESOURCE -> phVcsGitConfig.setProvider(PhVcsGitProvider.GOOGLESOURCE);
        }
        return phVcsGitConfig;
    }
}
