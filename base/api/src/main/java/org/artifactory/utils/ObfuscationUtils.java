package org.artifactory.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;

import static org.artifactory.common.ConstantValues.securityPersonalInfoNonAdminExposure;

/**
 * @author Andrey Tashlitsky
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ObfuscationUtils {

    public static final String UNKNOWN_STRING = "unknown";

    /**
     * Replaces a sensitive string with "unknown" string if the current user is not admin.
     */
    public static String obfuscateIfNeeded(String data) {
        if (shouldObfuscate(data)) {
            return UNKNOWN_STRING;
        }
        return data;
    }

    private static boolean shouldObfuscate(String data) {
        return data != null &&
                !securityPersonalInfoNonAdminExposure.getBoolean() && !getSecurityService().isAdmin();
    }

    private static SecurityService getSecurityService() {
        return ContextHelper.get().beanForType(SecurityService.class);
    }
}
