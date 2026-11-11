package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.YumConfiguration;

import java.util.Optional;

public interface YumPackageTypeConfigMapper extends BasePackageTypeConfigMapper<YumConfiguration> {
    String CALCULATE_YUM_METADATA = "calculateYumMetadata";
    String YUM_ROOT_DEPTH = "yumRootDepth";
    String ENABLE_FILE_LISTS_INDEXING = "enableFileListsIndexing";
    String YUM_GROUP_FILE_NAMES = "yumGroupFileNames";

    YumPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        if (packageTypeConfig == null) {
            throw new IllegalArgumentException("Package type config can not be null");
        }
        YumConfiguration config = new YumConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));

        Optional.ofNullable(packageTypeConfig.get(CALCULATE_YUM_METADATA))
                .map(Boolean::parseBoolean)
                .ifPresent(config::setCalculateYumMetadata);
        Optional.ofNullable(packageTypeConfig.get(YUM_ROOT_DEPTH))
                .map(Integer::parseInt)
                .ifPresent(config::setYumRootDepth);
        Optional.ofNullable(packageTypeConfig.get(ENABLE_FILE_LISTS_INDEXING))
                .map(Boolean::parseBoolean)
                .ifPresent(config::setEnableFileListsIndexing);
        Optional.ofNullable(packageTypeConfig.get(YUM_GROUP_FILE_NAMES))
                .ifPresent(config::setYumGroupFileNames);
        return config;
    };
}