package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.VcsConfiguration;
import org.artifactory.repo.packagetype.configuration.VcsGitConfiguration;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.artifactory.repo.vcs.VcsType;

import java.util.Map;
import java.util.Optional;

public interface VcsPackageTypeConfigMapper extends BasePackageTypeConfigMapper<VcsConfiguration> {

    String VCS_GIT_PROVIDER = "vcsGitProvider";
    String VCS_GIT_DOWNLOAD_URL = "vcsGitDownloadUrl";
    String VCS_TYPE = "vcsType";

    VcsConfiguration map(Map<String, String> packageTypeConfig);

    VcsPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        if (packageTypeConfig == null) {
            throw new IllegalArgumentException("Package type config can not be null");
        }

        VcsConfiguration vcsConfiguration = new VcsConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        vcsConfiguration.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        VcsGitConfiguration vcsGitConfiguration = new VcsGitConfiguration();
        Optional.ofNullable(packageTypeConfig.get(VCS_GIT_PROVIDER))
                .ifPresent(value -> vcsGitConfiguration.setProvider(VcsGitProvider.valueOf(value)));

        Optional.ofNullable(packageTypeConfig.get(VCS_GIT_DOWNLOAD_URL))
                .ifPresent(vcsGitConfiguration::setDownloadUrl);

        vcsConfiguration.setGit(vcsGitConfiguration);

        Optional.ofNullable(packageTypeConfig.get(VCS_TYPE))
                .ifPresent(value -> vcsConfiguration.setType(VcsType.valueOf(value)));
        return vcsConfiguration;
    };
}