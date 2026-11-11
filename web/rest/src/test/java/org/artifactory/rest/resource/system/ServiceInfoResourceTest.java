package org.artifactory.rest.resource.system;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.util.AolUtils;
import org.artifactory.util.HttpUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.artifactory.api.request.ArtifactoryHeaders.ORIGINAL_CLIENT_URI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ServiceInfoResourceTest {

    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CoreAddons coreAddons;
    @Mock
    private HttpServletRequest httpServletRequest;
    @InjectMocks
    private ServiceInfoResource serviceInfoResource;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        this.mocks = MockitoAnnotations.openMocks(this);
        when(addonsManager.addonByType(CoreAddons.class)).thenReturn(coreAddons);
        ArtifactoryHome.bind(this.artifactoryHome);
        ArtifactoryContextThreadBinder.bind(this.context);
    }

    @AfterEach
    void tearDown() throws Exception {
        this.mocks.close();
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testGetServerInfo() {
        try (MockedStatic<HttpUtils> httpUtilsMockedStatic = mockStatic(HttpUtils.class);
                MockedStatic<AolUtils> aolUtilsMockedStatic = mockStatic(AolUtils.class)) {

            ReflectionTestUtils.setField(serviceInfoResource, "servletRequest", httpServletRequest);
            when(centralConfigService.getServerName()).thenReturn("testServer");
            when(addonsManager.addonByType(CoreAddons.class).getArtifactoryServerName()).thenReturn("testHostname");
            httpUtilsMockedStatic.when(() -> HttpUtils.getServletContextUrlArtifactoryPreferred(httpServletRequest))
                    .thenReturn("https://baseUrl.io");
            aolUtilsMockedStatic.when(AolUtils::skipDockerDns).thenReturn(true);
            when(httpServletRequest.getHeader(ORIGINAL_CLIENT_URI)).thenReturn("https://npmBaseUrl.com");

            Response response = serviceInfoResource.getServerInfo();

            assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
            ServerInfo serverInfo = (ServerInfo) response.getEntity();
            assertEquals("testServer", serverInfo.getServerId());
            assertEquals("testHostname", serverInfo.getHostname());
            assertEquals("https://baseUrl.io", serverInfo.getBaseUrl());
            assertEquals("https://npmBaseUrl.com", serverInfo.getNpmBaseUrl());
            assertTrue(serverInfo.isSkipDockerDns());
        }
    }
}