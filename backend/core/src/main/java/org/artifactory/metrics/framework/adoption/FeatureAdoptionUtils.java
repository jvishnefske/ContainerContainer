package org.artifactory.metrics.framework.adoption;

import lombok.experimental.UtilityClass;
import org.artifactory.common.ConstantValues;

/**
 * @author Oleksandr Legenkiy
 */
@UtilityClass
public class FeatureAdoptionUtils {

    private static final String FEATURE_ADOPTION_DISABLED_PROP = "artifactory.platform.adoption.disabled";

    /**
     * Tells if a feature adoption logging is enabled in Artifactory.
     *
     * @return <code>true</code> if feature adoption is enabled. Otherwise <code>false</code>
     */
    public boolean isFeatureAdoptionEnabled() {
        return !Boolean.parseBoolean(System.getProperty(FEATURE_ADOPTION_DISABLED_PROP, "false")) &&
                ConstantValues.versionQueryEnabled.getBoolean(); // means Call Home is enabled
    }
}
