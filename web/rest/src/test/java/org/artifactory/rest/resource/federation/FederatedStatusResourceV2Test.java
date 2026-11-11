package org.artifactory.rest.resource.federation;

import jakarta.ws.rs.core.Response;
import org.artifactory.federation.FederatedRepoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class FederatedStatusResourceV2Test {
    @Mock
    private FederatedRepoService federatedRepoService;
    @InjectMocks
    private FederatedStatusResourceV2 federatedStatusResource;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testGetFederatedRepoStatusIsBlockedByRtfsService() {
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        federatedStatusResource.resource = new FederatedStatusResource();
        federatedStatusResource.getResource().setFederatedRepoService(federatedRepoService);
        Response federatedRepoStatus = federatedStatusResource.getFederatedRepoStatus("repo");
        assertEquals(SC_METHOD_NOT_ALLOWED, federatedRepoStatus.getStatus());
    }

    @Test
    void testGetFederatedRepoStatusByFedKeyIsBlockedByRtfsService() {
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        federatedStatusResource.resource = new FederatedStatusResource();
        federatedStatusResource.getResource().setFederatedRepoService(federatedRepoService);
        Response federatedRepoStatusByFedKey =
                federatedStatusResource.getFederatedRepoStatusByFedKey("repo", "federatedKey");
        assertEquals(SC_METHOD_NOT_ALLOWED, federatedRepoStatusByFedKey.getStatus());
    }
}