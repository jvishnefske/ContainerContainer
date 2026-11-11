package org.artifactory.util.application;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;

import java.util.Set;

/**
 * @author Natalie Berni
 */
@UtilityClass
@Slf4j
public class ApplicationServiceConfigurationUtil {
    public static final String APPLICATION_SERVICE_ID_PROPERTY_NAME = "application.id";
    public static final String APPLICATION_SERVICE_REVISION_PROP_KEY = "jf.revision";
    public static final String APPLICATION_SERVICE_VCS_URL_PROP_KEY = "jf.vcsUrl";
    public static final String APPLICATION_SERVICE_BRANCH_PROP_KEY = "jf.branch";
    public static final Set<String> APPLICATION_SERVICE_PROPERTIES_NAMES = Set.of(
            APPLICATION_SERVICE_ID_PROPERTY_NAME,
            APPLICATION_SERVICE_REVISION_PROP_KEY,
            APPLICATION_SERVICE_VCS_URL_PROP_KEY,
            APPLICATION_SERVICE_BRANCH_PROP_KEY
    );
    public static boolean isApplicationServiceFeatureEnabled() {
        return ConstantValues.applicationServiceFeatureEnabled.getBoolean();
    }
}
