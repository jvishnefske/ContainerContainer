package org.artifactory.tenant;

import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.AccessClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactoryDefaultTenantContextProviderTest {

    @Test
    void isEnabled_alwaysReturnsFalse() {
        AccessService accessService = mock(AccessService.class);
        ArtifactoryDefaultTenantContextProvider provider = new ArtifactoryDefaultTenantContextProvider(accessService);
        assertFalse(provider.isEnabled());
    }

    @Test
    void getTenantId_whenAccessClientIsNull_returnsSingleTenant() {
        AccessService accessService = mock(AccessService.class);
        when(accessService.getAccessClient()).thenReturn(null);
        ArtifactoryDefaultTenantContextProvider provider = new ArtifactoryDefaultTenantContextProvider(accessService);
        assertEquals("single-tenant", provider.getTenantId());
    }

    @Test
    void getTenantId_whenAccessClientReturnsTenantId_returnsThatTenantId() {
        AccessService accessService = mock(AccessService.class);
        AccessClient accessClient = mock(AccessClient.class);
        when(accessClient.getTenantID()).thenReturn(Optional.of("my-tenant-id"));
        when(accessService.getAccessClient()).thenReturn(accessClient);
        ArtifactoryDefaultTenantContextProvider provider = new ArtifactoryDefaultTenantContextProvider(accessService);
        assertEquals("my-tenant-id", provider.getTenantId());
    }

    @Test
    void getTenantId_whenAccessClientReturnsEmptyTenantId_returnsSingleTenant() {
        AccessService accessService = mock(AccessService.class);
        AccessClient accessClient = mock(AccessClient.class);
        when(accessClient.getTenantID()).thenReturn(Optional.empty());
        when(accessService.getAccessClient()).thenReturn(accessClient);
        ArtifactoryDefaultTenantContextProvider provider = new ArtifactoryDefaultTenantContextProvider(accessService);
        assertEquals("single-tenant", provider.getTenantId());
    }
}
