package org.artifactory.webapp.main;

import com.google.common.annotations.VisibleForTesting;
import lombok.experimental.UtilityClass;

import java.util.Optional;

/**
 * A utility class to resolve the system YAML path override property.
 *
 * @author Mor Merhav
 */
@UtilityClass
public class SystemYamlOverridePropertyResolver {

    @VisibleForTesting
    static final String ARTIFACTORY_SYSTEM_YAML_OVERRIDE = "rt.system.yaml.override";

    public static String resolve() {
        return Optional.ofNullable(getBySystemProperty()).orElse(getByEnvironmentVariable());
    }

    private static String getBySystemProperty() {
        return System.getProperty(ARTIFACTORY_SYSTEM_YAML_OVERRIDE);
    }

    private static String getByEnvironmentVariable() {
        return System.getenv(ARTIFACTORY_SYSTEM_YAML_OVERRIDE);
    }
}