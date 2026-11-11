package org.artifactory.addon.archive.v2;

import org.springframework.stereotype.Component;

/**
 * This is a default "dummy" implementation of the {@link ArchivePolicyAddon}. Its purpose is to provide a default
 * instance of a bean that is going to be used in artifactory-oss only.
 *
 * @author Kunal Mazumdar
 */
@Component
public class ArchivePolicyAddonDefaultImpl implements ArchivePolicyAddon {

    @Override
    public boolean isDefault() {
        return true;
    }
}
