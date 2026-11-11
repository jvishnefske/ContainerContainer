package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.model.artifact.BaseArtifact;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
@Slf4j
public class SetXrayAllowDownloadServiceTest {

    @InjectMocks
    private SetXrayAllowDownloadService setXrayAllowDownloadService;
    @Spy
    private AddonsManager addonsManager;
    @Spy
    private SecurityService securityService;
    private AutoCloseable mocks;

    @BeforeMethod
    public void initMocks() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test(dataProvider = "isAdminAllowDownloadProvider", description = "RTFACT-19062")
    public void testExecute(boolean isAdmin) {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);
        ArtifactoryRestResponse artifactoryRestResponse = Mockito.mock(ArtifactoryRestResponse.class);
        BaseArtifact baseArtifact = new BaseArtifact();
        baseArtifact.setRepoKey("docker-local");
        baseArtifact.setPath("test/t/manifest.json");
        when(request.getImodel()).thenReturn(baseArtifact);
        when(response.responseCode(HttpServletResponse.SC_FORBIDDEN)).thenReturn(artifactoryRestResponse);
        when(artifactoryRestResponse.buildResponse()).thenReturn(Response.noContent().build());
        Mockito.doReturn(isAdmin).when(securityService).isAdmin();
        XrayAddon xrayAddon = mock(XrayAddon.class);
        doReturn(false).when(xrayAddon).setAlertIgnored(any());
        doReturn(xrayAddon).when(addonsManager).addonByType(XrayAddon.class);
        try {
            setXrayAllowDownloadService.execute(request, response);
        } catch (Exception e) {
            log.error("setXrayAllowDownloadService test caught an unexpected exception", e);
            throw e;
        }
        verify(addonsManager.addonByType(XrayAddon.class), isAdmin ? times(1) : times(0)).setAlertIgnored(any());
    }

    @DataProvider
    public Object[][] isAdminAllowDownloadProvider() {
        return new Object[][] {
                {true},
                {false}
        };
    }
}