package org.artifactory.ui.rest.service.admin.security.crowdsso;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.sso.crowd.CrowdAddon;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.admin.security.crowdsso.CrowdIntegration;
import org.jfrog.access.client.model.CrowdSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.UriInfo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestCrowdConnectionServiceTest {

    @Mock
    private AddonsManager addonsManager;

    @Mock
    private CrowdAddon crowdAddon;

    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private ArtifactoryHome artifactoryHome;

    private TestCrowdConnectionService testCrowdConnectionService;

    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        testCrowdConnectionService = new TestCrowdConnectionService();
        testCrowdConnectionService.setAddonsManager(addonsManager);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void executeTestValid() throws Exception {
        RestResponse response = new ArtifactoryRestResponse();
        CrowdIntegration crowdIntegration = CrowdIntegration.builder().enableIntegration(true).build();
        when(httpServletRequest.getContextPath()).thenReturn("jlkja/akdjlka/");
        when(httpServletRequest.getRequestURI()).thenReturn("jlkja/akdjlka/");
        ArtifactoryRestRequest.RequestBuilder requestBuilder
                = new ArtifactoryRestRequest.RequestBuilder(httpServletRequest, mock(Request.class),
                mock(UriInfo.class), mock(HttpHeaders.class));
        requestBuilder.model(crowdIntegration);
        ArtifactoryRestRequest request = new ArtifactoryRestRequest(requestBuilder);
        when(addonsManager.addonByType(CrowdAddon.class)).thenReturn(crowdAddon);
        testCrowdConnectionService.execute(request, response);
        verify(addonsManager).addonByType(CrowdAddon.class);
        verify(crowdAddon).testCrowdConnection(integrationToCrowd(crowdIntegration));
    }

    private CrowdSetting integrationToCrowd(CrowdIntegration crowdIntegration) {
        return CrowdSetting.builder()
                .autoUserCreation(crowdIntegration.isNoAutoUserCreation())
                .password(crowdIntegration.getPassword())
                .applicationName(crowdIntegration.getApplicationName())
                .allowUserToAccessProfile(crowdIntegration.isAllowUserToAccessProfile())
                .customCookieTokenKey(crowdIntegration.getCustomCookieTokenKey())
                .directAuthentication(crowdIntegration.isDirectAuthentication())
                .enable(crowdIntegration.isEnableIntegration())
                .overrideAllGroupsUponLogin(crowdIntegration.isOverrideAllGroupsUponLogin())
                .serverUrl(crowdIntegration.getServerUrl())
                .useDefaultProxy(crowdIntegration.isUseDefaultProxy())
                .sessionValidationInterval(crowdIntegration.getSessionValidationInterval())
                .build();
    }
}
