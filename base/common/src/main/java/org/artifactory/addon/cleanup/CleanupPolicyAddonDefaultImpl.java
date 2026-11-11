package org.artifactory.addon.cleanup;

import org.springframework.stereotype.Component;

/**
 * This is a default "dummy" implementation of the {@link CleanupPolicyAddon}. Its purpose is to provide a default
 * instance of a bean that is going to be used in artifactory-oss only.
 *
 * @author Oleksandr Legenkiy
 */
@Component
public class CleanupPolicyAddonDefaultImpl implements CleanupPolicyAddon {

    @Override
    public boolean isDefault() {
        return true;
    }
}
