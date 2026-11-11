package org.artifactory.repo.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.artifactory.repo.converter.PackageTypeConfigConverter.PACKAGE_TYPE_CONFIG_KEY;
import static org.artifactory.repo.converter.RepositoryConfigConverterEnum.getRelevantConverters;

/**
 * @author Tamar Rosen
 */
@Component
public class RepositoryConfigBaseConverter implements RepositoriesConfigSubConverter {
    private static final Logger log = LoggerFactory.getLogger(RepositoryConfigBaseConverter.class);
    public static final int VERSION = 1;
    public static final String BASE_CONFIG_KEY = "baseConfig";
    public static final String REPO_TYPE_CONFIG_KEY = "repoTypeConfig";
    public static final String TYPE_KEY = "type";
    public static final String SECURITY_CONFIG_KEY = "securityConfig";

    @Override
    public boolean isInterested(String key) {
        return !PACKAGE_TYPE_CONFIG_KEY.equals(key);
    }

    @Override
    public void convert(Map<String, Object> repositoryConfigMap) {
        Map<String, Object> baseConfig = (Map<String, Object>) repositoryConfigMap.get(BASE_CONFIG_KEY);
        int modelVersion = (int) baseConfig.getOrDefault("modelVersion", 0);
        List<RepositoriesConverter> converters = getRelevantConverters(modelVersion);
        if (converters.isEmpty()) {
            log.debug("No Repository Config converters found between version {} and {}", modelVersion, VERSION);
        } else {
            log.info("Starting Repository Config conversion from {} to {}", modelVersion, VERSION);
            for (RepositoriesConverter converter : converters) {
                converter.convert(repositoryConfigMap);
            }
            log.info("Finished Repository Config conversion from {} to {}", modelVersion, VERSION);
        }
    }
}
