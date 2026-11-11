package org.artifactory.tenant;

import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.multitenant.TenantContextProvider;

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
        AccessClient accessClient = accessService.getAccessClient();
        return accessClient != null ? accessClient.getTenantID().orElse(SINGLE_TENANT) : SINGLE_TENANT;
    }
}
