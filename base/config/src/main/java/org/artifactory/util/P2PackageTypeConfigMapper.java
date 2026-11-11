package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.P2Configuration;

import java.util.List;
import java.util.Map;

/**
 * @author Tamar Rosen
 */
public interface P2PackageTypeConfigMapper extends BasePackageTypeConfigMapper<P2Configuration> {
    String URLS = "urls";
    String P2_ORIGINAL_URL = "p2OriginalUrl";
    String FORCE_P2_AUTHENTICATION = "forceP2Authentication";

    P2PackageTypeConfigMapper INSTANCE = new P2PackageTypeConfigMapper() {
        @Override
        public P2Configuration map(Map<String, String> packageTypeConfig) {
            if (packageTypeConfig == null) {
                throw new IllegalArgumentException("Package type config can not be null");
            }
            P2Configuration config = new P2Configuration();
            String modelVersion = packageTypeConfig.get(MODEL_VERSION);
            String p2OriginalUrl = packageTypeConfig.get(P2_ORIGINAL_URL);
            config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
            config.setP2OriginalUrl(p2OriginalUrl);
            config.setForceP2Authentication(Boolean.parseBoolean(packageTypeConfig.get(FORCE_P2_AUTHENTICATION)));
            if (packageTypeConfig.get(URLS) != null) {
                List<String> urls = jsonStringToList(URLS, packageTypeConfig);
                config.setUrls(urls);
            }

            return config;
        }
    };
}