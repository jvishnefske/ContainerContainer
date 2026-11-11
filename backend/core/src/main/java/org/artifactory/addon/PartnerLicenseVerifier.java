package org.artifactory.addon;

import org.artifactory.api.context.ContextHelper;

public class PartnerLicenseVerifier {
    public static void assertPartnerLicense() {
        if (ContextHelper.get().beanForType(AddonsManager.class).isPartnerLicense()) {
            throw new UnsupportedOperationException("Artifactory is running with a partner license," +
                    " this feature isn't supported");
        }
    }
}
