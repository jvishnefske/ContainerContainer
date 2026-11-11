package org.artifactory.security.access.migration.populator;

import java.util.Map;

/**
 * @author morm
 */
public interface PatchYamlAccessConfigMigrationHandler {

    /**
     * Migrates accepted yaml config from Artifactory
     * {@link org.artifactory.rest.resource.system.ConfigResource#patchConfig(String)} to Access service
     * @param yamlConfigMap map containing the config changes in yaml format
     * @return number of resources migrated
     */
    int migrate(Map<String,Object> yamlConfigMap);
}
