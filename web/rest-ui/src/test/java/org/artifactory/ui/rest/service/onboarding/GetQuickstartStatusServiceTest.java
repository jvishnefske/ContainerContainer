package org.artifactory.ui.rest.service.onboarding;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.addon.onboarding.OnBoardingException;
import org.artifactory.addon.onboarding.model.OnBoardingRecord;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.onboarding.OnBoardingResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetQuickstartStatusServiceTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private OnBoardingAddon onBoardingAddon;
    @Mock
    private ArtifactoryRestRequest<String> request;
    private RestResponse restResponse;
    private GetQuickstartStatusService getQuickstartStatusService;
    private AutoCloseable mocksClosable;

    @BeforeEach
    void setUp() {
        mocksClosable = MockitoAnnotations.openMocks(this);
        Mockito.when(addonsManager.addonByType(OnBoardingAddon.class)).thenReturn(onBoardingAddon);
        getQuickstartStatusService = Mockito.spy(new GetQuickstartStatusService());
        getQuickstartStatusService.setAddonsManager(addonsManager);
        restResponse = new ArtifactoryRestResponse();
    }

    @Test
    void testGetStatus() throws OnBoardingException {
        when(request.getImodel()).thenReturn("repo");
        OnBoardingRecord onBoardingRecord = new OnBoardingRecord();
        onBoardingRecord.setAuthenticated(true);
        onBoardingRecord.setDownloaded(true);
        onBoardingRecord.setUploaded(true);
        Mockito.when(onBoardingAddon.getStatus("repo")).thenReturn(onBoardingRecord);
        getQuickstartStatusService.execute(request, restResponse);

        Assertions.assertEquals(Response.Status.OK.getStatusCode(), restResponse.getResponseCode());
        OnBoardingResponse responseModel = (OnBoardingResponse) restResponse.getIModel();
        assertTrue(responseModel.getIsAuthenticated());
        assertTrue(responseModel.getIsPublished());
        assertTrue(responseModel.getIsDownloaded());
        verify(onBoardingAddon).getStatus("repo");
    }

    @Test
    void testGetStatusNegative() throws OnBoardingException {
        when(request.getImodel()).thenReturn("repo");
        OnBoardingRecord onBoardingRecord = new OnBoardingRecord();
        Mockito.when(onBoardingAddon.getStatus("repo")).thenReturn(onBoardingRecord);
        getQuickstartStatusService.execute(request, restResponse);

        Assertions.assertEquals(Response.Status.OK.getStatusCode(), restResponse.getResponseCode());
        OnBoardingResponse responseModel = (OnBoardingResponse) restResponse.getIModel();
        assertFalse(responseModel.getIsAuthenticated());
        assertFalse(responseModel.getIsPublished());
        assertFalse(responseModel.getIsDownloaded());
        verify(onBoardingAddon).getStatus("repo");
    }

    @AfterEach
    void tearDown() {
        if (mocksClosable != null) {
            try {
                mocksClosable.close();
            } catch (Exception ignore) {}
        }
    }

}