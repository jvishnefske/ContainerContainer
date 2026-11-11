package org.artifactory.ui.rest.service.onboarding;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.addon.onboarding.OnBoardingException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.onboarding.OnBoardingParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import jakarta.ws.rs.core.Response;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author BarakH
 */
class StartQuickstartSessionServiceTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private OnBoardingAddon onBoardingAddon;
    @Mock
    private ArtifactoryRestRequest<OnBoardingParams> request;
    private RestResponse restResponse;
    private StartQuickstartSessionService startQuickstartSessionService;
    private AutoCloseable mocksClosable;

    @BeforeEach
    void setUp() {
        mocksClosable = MockitoAnnotations.openMocks(this);
        Mockito.when(addonsManager.addonByType(OnBoardingAddon.class)).thenReturn(onBoardingAddon);
        startQuickstartSessionService = Mockito.spy(new StartQuickstartSessionService());
        startQuickstartSessionService.setAddonsManager(addonsManager);
        restResponse = new ArtifactoryRestResponse();
    }

    @Test
    void testStartWithNullParams() {
        when(request.getImodel()).thenReturn(null);
        startQuickstartSessionService.execute(request, restResponse);
        Assertions.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), restResponse.getResponseCode());
    }

    @Test
    void testStartWithAnError() throws OnBoardingException {
        OnBoardingParams onBoardingParams = new OnBoardingParams();
        onBoardingParams.setRepoKey("repo");
        when(request.getImodel()).thenReturn(onBoardingParams);
        Mockito.when(onBoardingAddon.startOnBoardingTutorial("repo", null, null, null, null))
                .thenThrow(new OnBoardingException("ERROR", SC_BAD_REQUEST));
        startQuickstartSessionService.execute(request, restResponse);
        Assertions.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), restResponse.getResponseCode());
        Assertions.assertEquals("{\"error\":\"ERROR\"}", restResponse.getEntity());
    }

    @Test
    void testStart() throws OnBoardingException {
        OnBoardingParams onBoardingParams = new OnBoardingParams();
        onBoardingParams.setRepoKey("repo");
        when(request.getImodel()).thenReturn(onBoardingParams);
        Mockito.when(onBoardingAddon.startOnBoardingTutorial("repo", null, null, null, null)).thenReturn(true);
        startQuickstartSessionService.execute(request, restResponse);
        Assertions.assertEquals(Response.Status.OK.getStatusCode(), restResponse.getResponseCode());
        verify(onBoardingAddon).startOnBoardingTutorial("repo", null, null, null, null);
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