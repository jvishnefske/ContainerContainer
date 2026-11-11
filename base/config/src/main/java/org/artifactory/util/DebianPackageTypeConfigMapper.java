package org.artifactory.util;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.DebianConfiguration;

import java.util.List;
import java.util.Map;

/**
 * @author Tamar Rosen
 */
public interface DebianPackageTypeConfigMapper
        extends BasePackageTypeConfigMapper<DebianConfiguration> {
    String DEBIAN_TRIVIAL_LAYOUT = "debianTrivialLayout";
    String OPTIONAL_INDEX_COMPRESSION_FORMATS = "optionalIndexCompressionFormats";
    String DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS = "debianOptionalIndexCompressionFormats";
    String DDEB_SUPPORTED = "ddebSupported";
    String DEBIAN_DEFAULT_ARCHITECTURES = "debianDefaultArchitectures";

    DebianPackageTypeConfigMapper INSTANCE = new DebianPackageTypeConfigMapper() {
        @Override
        public DebianConfiguration map(Map<String, String> packageTypeConfig) {
            if (packageTypeConfig == null) {
                throw new IllegalArgumentException("Package type config can not be null");
            }
            DebianConfiguration config = new DebianConfiguration();
            String modelVersion = packageTypeConfig.get(MODEL_VERSION);
            String debianTrivialLayout = packageTypeConfig.get(DEBIAN_TRIVIAL_LAYOUT);
            String ddebSupported = packageTypeConfig.get(DDEB_SUPPORTED);

            config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
            config.setDebianTrivialLayout(
                    !StringUtils.isBlank(debianTrivialLayout) && Boolean.parseBoolean(debianTrivialLayout));
            config.setDdebSupported(!StringUtils.isBlank(ddebSupported) && Boolean.parseBoolean(ddebSupported));
            config.setDebianDefaultArchitectures(
                    ObjectUtils.defaultIfNull(packageTypeConfig.get(DEBIAN_DEFAULT_ARCHITECTURES),
                            config.getDebianDefaultArchitectures()));

            if (packageTypeConfig.get(OPTIONAL_INDEX_COMPRESSION_FORMATS) != null) {
                List<String> optionalIndexCompressionFormats =
                        jsonStringToList(OPTIONAL_INDEX_COMPRESSION_FORMATS, packageTypeConfig);
                config.setOptionalIndexCompressionFormats(optionalIndexCompressionFormats);
            }
            if (packageTypeConfig.get(DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS) != null) {
                List<String> debianOptionalIndexCompressionFormats =
                        jsonStringToList(DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS, packageTypeConfig);
                config.setDebianOptionalIndexCompressionFormats(debianOptionalIndexCompressionFormats);
            }
            return config;
        }
    };
}