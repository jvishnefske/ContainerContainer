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
    void isEnabled() {
        assertFalse(createArtifactoryDefaultTenantContextProvider(Optional.empty()).isEnabled());
    }

    @Test
    void getTenantId() {
        assertEquals("id", createArtifactoryDefaultTenantContextProvider(Optional.of("id")).getTenantId());
    }

    @Test
    void getTenantIdWithAccessClientNull() {
        AccessService accessService = mock(AccessService.class);
        when(accessService.getAccessClient()).thenReturn(null);
        ArtifactoryDefaultTenantContextProvider artifactoryDefaultTenantContextProvider =
                new ArtifactoryDefaultTenantContextProvider(accessService);
        assertEquals("single-tenant", artifactoryDefaultTenantContextProvider.getTenantId());
    }

    @Test
    void getTenantIdWithNoTenantId() {
        assertEquals("single-tenant", createArtifactoryDefaultTenantContextProvider(Optional.empty()).getTenantId());
    }

    private ArtifactoryDefaultTenantContextProvider createArtifactoryDefaultTenantContextProvider(
            Optional<String> tenantId) {
        AccessService accessService = mock(AccessService.class);
        AccessClient accessClient = mock(AccessClient.class);
        when(accessClient.getTenantID()).thenReturn(tenantId);
        when(accessService.getAccessClient()).thenReturn(accessClient);
        return new ArtifactoryDefaultTenantContextProvider(accessService);
    }
}