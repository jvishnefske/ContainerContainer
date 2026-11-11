package org.artifactory.ui.rest.service.onboarding;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.addon.onboarding.OnBoardingException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.onboarding.SupportedRequestModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import jakarta.ws.rs.core.Response;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author BarakH
 */
class QuickstartSupportedServiceTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private OnBoardingAddon onBoardingAddon;
    @Mock
    private ArtifactoryRestRequest<String> request;
    @Mock
    private ArtifactoryRestRequest<SupportedRequestModel> batchRequest;
    private RestResponse restResponse;
    private QuickstartSupportedService quickstartSupportedService;
    private QuickstartSupportedBatchService quickstartSupportedBatchService;
    private AutoCloseable mocksClosable;

    @BeforeEach
    void setUp() {
        mocksClosable = MockitoAnnotations.openMocks(this);
        Mockito.when(addonsManager.addonByType(OnBoardingAddon.class)).thenReturn(onBoardingAddon);
        quickstartSupportedService = Mockito.spy(new QuickstartSupportedService());
        quickstartSupportedService.setAddonsManager(addonsManager);
        quickstartSupportedBatchService = Mockito.spy(new QuickstartSupportedBatchService());
        quickstartSupportedBatchService.setAddonsManager(addonsManager);
        restResponse = new ArtifactoryRestResponse();
    }

    @Test
    void testIsSupported() throws OnBoardingException {
        when(request.getImodel()).thenReturn("repo");
        quickstartSupportedService.execute(request, restResponse);
        Assertions.assertEquals(Response.Status.OK.getStatusCode(), restResponse.getResponseCode());
        verify(onBoardingAddon).assertRepoIsSupported("repo");
    }

    @Test
    void testIsSupportedBatch() throws OnBoardingException {
        SupportedRequestModel model = new SupportedRequestModel();
        model.setRepoKeys(Set.of("repo"));
        when(batchRequest.getImodel()).thenReturn(model);
        quickstartSupportedBatchService.execute(batchRequest, restResponse);
        Assertions.assertEquals(Response.Status.OK.getStatusCode(), restResponse.getResponseCode());
        verify(onBoardingAddon).assertRepoIsSupported(Set.of("repo"));
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