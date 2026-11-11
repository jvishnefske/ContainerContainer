package org.artifactory.tenant;

import org.artifactory.security.access.AccessService;
import org.jfrog.access.multitenant.TenantContextProvider;

/**
 * @author Ron Malka
 */
public class ArtifactoryDefaultTenantContextProvider implements TenantContextProvider {
    private static final String SINGLE_TENANT = "single-tenant";
    private final AccessService accessService;

    public ArtifactoryDefaultTenantContextProvider(AccessService accessService) {
        this.accessService = accessService;
    }

    @Override
    public boolean isEnabled() {
        // As Artifactory is a single tenant application, the multi tenant always disable.
        return false;
    }

    @Override
    public String getTenantId() {
        return accessService.getAccessClient() != null
                ? accessService.getAccessClient().getTenantID().orElse(SINGLE_TENANT)
                : SINGLE_TENANT;
    }
}
