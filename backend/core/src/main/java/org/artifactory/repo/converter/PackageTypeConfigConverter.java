package org.artifactory.repo.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.artifactory.repo.converter.PackageConfigConverterEnum.getRelevantConverters;

/**
 * @author Tamar Rosen
 */
@Component
public class PackageTypeConfigConverter implements RepositoriesConfigSubConverter {
    private static final Logger log = LoggerFactory.getLogger(PackageTypeConfigConverter.class);
    public static final String PACKAGE_TYPE_CONFIG_KEY = "packageTypeConfig";
    private static final int VERSION = 1;

    @Override
    public boolean isInterested(String key) {
        return PACKAGE_TYPE_CONFIG_KEY.equals(key);
    }

    @Override
    public void convert(Map<String, Object> repositoryConfigMap) {
        Map<String, String> packageTypeConfig = (Map<String, String>) repositoryConfigMap.get(PACKAGE_TYPE_CONFIG_KEY);
        if (packageTypeConfig == null) {
            return;
        }
        int modelVersion = Integer.parseInt(packageTypeConfig.getOrDefault("modelVersion", "0"));
        List<PackagesConverter> converters = getRelevantConverters(modelVersion);
        if (converters.isEmpty()) {
            log.debug("No Packages Config converters found between version {} and {}", modelVersion, VERSION);
        } else {
            log.info("Starting Packages Config conversion from {} to {}", modelVersion, VERSION);
            for (PackagesConverter converter : converters) {
                if (converter.isInterested(modelVersion)) {
                    converter.convert(packageTypeConfig);
                } else {
                    log.info("Not running Packages Config converter '{}' (converter criteria not met)",
                            converter.getClass().getSimpleName());
                }
            }
            log.info("Finished Packages Config conversion from {} to {}", modelVersion, VERSION);
        }
    }
}
