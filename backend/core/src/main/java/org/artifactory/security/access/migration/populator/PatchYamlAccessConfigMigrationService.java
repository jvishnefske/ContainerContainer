package org.artifactory.security.access.migration.populator;

import org.artifactory.api.context.ContextHelper;

import java.util.Collection;
import java.util.Map;

/**
 * @author morm
 * Migrates all the configuration changes set through the Patch YAML API
 * {@link org.artifactory.rest.resource.system.ConfigResource#patchConfig(String)} to Access by calling all the
 * migration handlers in the system
 *
 */
public class PatchYamlAccessConfigMigrationService {

    /**
     * Migrates all the configuration changes set through the Patch YAML API by calling all the migration handlers in
     * the system
     * @param yamlConfigMap the map containing the PATCH YAML API config changes
     * @return number of resources moved to Access
     */
    public int migrate(Map<String, Object> yamlConfigMap) {
        Collection<PatchYamlAccessConfigMigrationHandler> populators =
                ContextHelper.get().beansForType(PatchYamlAccessConfigMigrationHandler.class).values();
        int movedResources = 0;
        for (PatchYamlAccessConfigMigrationHandler populator : populators) {
            movedResources += populator.migrate(yamlConfigMap);
        }
        return movedResources;
    }
}
