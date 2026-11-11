package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.repo.ArtifactoryFederationServiceException;
import org.artifactory.repo.FederationPriorityUpdateRtfsResponse;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationPriority;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.repo.RepoPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 *  unit tests for update federation priority service
 *  @author : Tomer Ben Kereth
 */
class FederatedUpdatePriorityServiceTest {

    FederatedRepoService federatedRepoService;

    @BeforeEach
    public void setup() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        federatedRepoService = mock(FederatedRepoService.class);
        when(context.beanForType(FederatedRepoService.class)).thenReturn(federatedRepoService);
    }
    @Test
     void testExecuteSuccess() throws ArtifactoryFederationServiceException {
        UpdateFederationPriorityService service = new UpdateFederationPriorityService();
        FederationPriority federationPriority = new FederationPriority("testRepo", "high");
        ArtifactoryRestRequest<FederationPriority> request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn(federationPriority);

        when(federatedRepoService.updateFederationPriority("testRepo", RepoPriority.HIGH))
                .thenReturn(FederationPriorityUpdateRtfsResponse.builder().message("success").build());
        service.execute(request, response);

        // Assert
        verify(federatedRepoService, times(1))
                .updateFederationPriority("testRepo", RepoPriority.HIGH);
        assertEquals(200,response.getResponseCode());
    }

    @Test
    void testExecuteSuccessStandard() throws ArtifactoryFederationServiceException {
        UpdateFederationPriorityService service = new UpdateFederationPriorityService();
        FederationPriority federationPriority = new FederationPriority("testRepo", "standard");
        ArtifactoryRestRequest<FederationPriority> request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn(federationPriority);

        when(federatedRepoService.updateFederationPriority("testRepo", RepoPriority.STANDARD))
                .thenReturn(FederationPriorityUpdateRtfsResponse.builder().message("success").build());
        service.execute(request, response);

        // Assert
        verify(federatedRepoService, times(1))
                .updateFederationPriority("testRepo", RepoPriority.STANDARD);
        assertEquals(200,response.getResponseCode());
    }

    @Test
    void testExecuteFailureUnknown() throws ArtifactoryFederationServiceException {
        UpdateFederationPriorityService service = new UpdateFederationPriorityService();
        FederationPriority federationPriority = new FederationPriority("testRepo", "wrongPriority");
        ArtifactoryRestRequest<FederationPriority> request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn(federationPriority);

        when(federatedRepoService.updateFederationPriority("testRepo", RepoPriority.UNKNOWN))
                .thenThrow(new ArtifactoryFederationServiceException("test exception"));
        service.execute(request, response);

        // Assert
        verify(federatedRepoService, times(1))
                .updateFederationPriority("testRepo", RepoPriority.UNKNOWN);

    }

    @Test
    void testExecuteFailureNullPriority() throws ArtifactoryFederationServiceException {
        UpdateFederationPriorityService service = new UpdateFederationPriorityService();
        FederationPriority federationPriority = new FederationPriority("testRepo", null);
        ArtifactoryRestRequest<FederationPriority> request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn(federationPriority);

        when(federatedRepoService.updateFederationPriority("testRepo", RepoPriority.UNKNOWN))
                .thenThrow(new ArtifactoryFederationServiceException("test exception"));
        service.execute(request, response);

        // Assert
        verify(federatedRepoService, times(1))
                .updateFederationPriority("testRepo", RepoPriority.UNKNOWN);
        assertEquals(400,response.getResponseCode());
    }

    @Test
    void testExecuteFailure() throws ArtifactoryFederationServiceException {
        UpdateFederationPriorityService service = new UpdateFederationPriorityService();
        FederationPriority federationPriority = new FederationPriority("testRepo", "HIGH");
        ArtifactoryRestRequest<FederationPriority> request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn(federationPriority);

        doThrow(new ArtifactoryFederationServiceException("test exception"))
                .when(federatedRepoService).updateFederationPriority("testRepo", RepoPriority.HIGH);
        service.execute(request, response);

        // Assert
        verify(federatedRepoService, times(1))
                .updateFederationPriority("testRepo", RepoPriority.HIGH);
        assertEquals(400,response.getResponseCode());
    }





}