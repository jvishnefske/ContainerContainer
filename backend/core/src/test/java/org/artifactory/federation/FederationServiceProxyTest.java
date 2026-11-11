package org.artifactory.federation;

import org.artifactory.federated.service.FederationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class})
class FederationServiceProxyTest {

    @Mock
    FederatedRepoService federatedRepoService;

    @InjectMocks
    FederationServiceTestProxy federationServiceProxy = new FederationServiceTestProxy(
            new DummyFederationService("rtfs"),
            new DummyFederationService("legacy"));

    @Test
    void getServiceReturnsRtfsWhenReady() {
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        assertEquals("rtfs", federationServiceProxy.getService().id);
    }

    @Test
    void getServiceReturnsLegacyWhenNotReady() {
        when(federatedRepoService.isMigrationOver()).thenReturn(false);
        assertEquals("legacy", federationServiceProxy.getService().id);
    }

    static class DummyFederationService implements FederationService {
        public String id;
        public DummyFederationService(String id) {
            this.id = id;
        }
    }

    static class FederationServiceTestProxy extends FederationServiceProxy<DummyFederationService> {

        FederatedRepoService federatedRepoService;

        protected FederationServiceTestProxy(DummyFederationService rtfsService, DummyFederationService legacyService) {
            super(rtfsService, legacyService);
        }

        @Override
        protected boolean shouldUseFederationService() {
            return federatedRepoService.isMigrationOver();
        }
    }
}