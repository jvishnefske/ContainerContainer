package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.NuGetConfiguration;

import java.util.Map;

/**
 * @author Mor Merhav
 */
public interface NuGetPackageTypeConfigMapper extends BasePackageTypeConfigMapper<NuGetConfiguration> {

    String FEED_CONTEXT_PATH = "feedContextPath";
    String DOWNLOAD_CONTEXT_PATH = "downloadContextPath";
    String V3_FEED_URL = "v3FeedUrl";
    String FORCE_NUGET_AUTHENTICATION = "forceNugetAuthentication";
    String SYMBOL_SERVER_URL = "symbolServerUrl";
    String ENABLE_NORMALIZED_VERSION = "enableNormalizedVersion";

    NuGetPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        NuGetConfiguration config = new NuGetConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        config.setFeedContextPath(packageTypeConfig.get(FEED_CONTEXT_PATH));
        config.setDownloadContextPath(packageTypeConfig.get(DOWNLOAD_CONTEXT_PATH));
        config.setV3FeedUrl(packageTypeConfig.get(V3_FEED_URL));
        config.setForceNugetAuthentication(Boolean.parseBoolean(packageTypeConfig.get(FORCE_NUGET_AUTHENTICATION)));
        config.setEnableNormalizedVersion(Boolean.parseBoolean(packageTypeConfig.get(ENABLE_NORMALIZED_VERSION)));
        config.setSymbolServerUrl(packageTypeConfig.get(SYMBOL_SERVER_URL));

        return config;
    };

    @Override
    NuGetConfiguration map(Map<String, String> packageTypeConfig);
}