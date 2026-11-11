package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.CocoaPodsConfiguration;
import org.artifactory.repo.packagetype.configuration.VcsGitConfiguration;
import org.artifactory.repo.vcs.VcsGitProvider;

import java.util.Map;
import java.util.Optional;

public interface CocoaPodsPackageTypeConfigMapper extends BasePackageTypeConfigMapper<CocoaPodsConfiguration> {

    String SPECS_REPO_DOWNLOAD_PROVIDER = "specsRepoDownloadProvider";
    String SPECS_REPO_DOWNLOAD_URL = "specsRepoDownloadUrl";
    String COCOA_PODS_SPECS_URL = "cocoaPodsSpecsRepoUrl";
    String COCOA_PODS_FORCE_MODIFY_INDEX = "cocoaPodsForceModifyIndex";

    CocoaPodsConfiguration map(Map<String, String> packageTypeConfig);

    CocoaPodsPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        if (packageTypeConfig == null) {
            throw new IllegalArgumentException("Package type config can not be null");
        }
        CocoaPodsConfiguration config = new CocoaPodsConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        config.setExternalDependencies(ExternalDependenciesPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig));
        config.setCocoaPodsForceModifyIndex(Boolean.parseBoolean(packageTypeConfig.get(COCOA_PODS_FORCE_MODIFY_INDEX)));
        VcsGitConfiguration specRepoGitConfiguration = new VcsGitConfiguration();
        Optional.ofNullable(packageTypeConfig.get(SPECS_REPO_DOWNLOAD_PROVIDER))
                .ifPresent(value -> specRepoGitConfiguration.setProvider(VcsGitProvider.valueOf(value)));
        Optional.ofNullable(packageTypeConfig.get(SPECS_REPO_DOWNLOAD_URL))
                .ifPresent(specRepoGitConfiguration::setDownloadUrl);
        config.setSpecRepoProvider(specRepoGitConfiguration);

        config.setVcs(VcsPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig));

        Optional.ofNullable(packageTypeConfig.get(COCOA_PODS_SPECS_URL))
                .ifPresent(config::setCocoaPodsSpecsRepoUrl);

        Optional.ofNullable(packageTypeConfig.get(PackageTypeConfigFields.COCOA_PODS_CDN_URL.getKey()))
                .ifPresent(config::setCocoaPodsCdnUrl);
        return config;
    };
}