package org.artifactory.rest.resource.xray;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicStatusLine;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.addon.xray.XrayScanBuild;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.model.xray.XrayScanBuildModel;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.StringInputStream;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import jakarta.ws.rs.core.Response;

import java.io.IOException;

import static com.jfrog.build.acl.util.BuildConstants.BUILD_INFO_REPO_KEY_SUFFIX;
import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
public class XrayResourceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private AddonsManager addonsManagerMock;
    @Mock
    private XrayAddon xrayAddon;
    @Mock
    private CloseableHttpResponse responseMock;
    @Mock
    private BuildFacade buildFacadeMock;

    @InjectMocks
    private XrayResource xrayResource;
    private MockitoSession mockitoSession;

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();
        when(addonsManagerMock.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    public void testScanBuildDefaultBuildRepoScanBuildSuccess() throws IOException {
        XrayScanBuildModel model = mockDefaultBuildContext();
        mockXrayScanBuildResponse(HttpStatus.SC_OK);

        Response response = xrayResource.scanBuild(model);

        Assert.assertEquals(response.getStatus(), HttpStatus.SC_OK);
    }

    @Test
    public void testScanBuildDefaultProjectRepoScanBuildSuccess() throws IOException {
        String defaultProject = ProjectsService.DEFAULT_PROJECT;
        String buildRepoName = BUILDINFO_REPO_KEY;
        when(buildFacadeMock.getPreferredBuildRepo(null, defaultProject))
                .thenReturn(buildRepoName);
        when(buildFacadeMock.canReadBuild(null, null, buildRepoName))
                .thenReturn(true);
        XrayScanBuildModel xrayScanBuildModel = new XrayScanBuildModel();
        xrayScanBuildModel.setProject(defaultProject);
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("proto", 1, 0), HttpStatus.SC_OK, "OK");
        when(responseMock.getStatusLine()).thenReturn(statusLine);
        HttpEntity httpEntity = new InputStreamEntity(new StringInputStream(""));
        when(responseMock.getEntity()).thenReturn(httpEntity);
        XrayScanBuild xrayScanBuild = XrayResourceHelper.toModel(xrayScanBuildModel);
        xrayScanBuild.setBuildRepo(buildRepoName);
        when(xrayAddon.scanBuild(xrayScanBuild)).thenReturn(responseMock);

        Response response = xrayResource.scanBuild(xrayScanBuildModel);

        Assert.assertEquals(response.getStatus(), HttpStatus.SC_OK);
    }

    @Test
    public void testScanBuildCustomBuildRepoNoXrayProjectsSupportScanBuildFailure() {
        String buildRepoName = "custom" + BUILD_INFO_REPO_KEY_SUFFIX;
        when(buildFacadeMock.getPreferredBuildRepo(buildRepoName, null))
                .thenReturn(buildRepoName);
        when(buildFacadeMock.canReadBuild(null, null, buildRepoName))
                .thenReturn(true);
        XrayScanBuildModel xrayScanBuildModel = new XrayScanBuildModel();
        xrayScanBuildModel.setBuildRepo(buildRepoName);
        when(xrayAddon.isXrayVersionSupportProjects()).thenReturn(false);

        Response response = xrayResource.scanBuild(xrayScanBuildModel);

        Assert.assertEquals(response.getStatus(), HttpStatus.SC_BAD_REQUEST);
        Assert.assertEquals(response.getEntity(),
                "Scanning non-default build repositories is unsupported by Xray version lower than: " +
                        XrayAddon.XRAY_MIN_VER_WITH_PROJECTS_SUPPORT);
    }

    @Test
    public void testScanBuildCustomProjectNoXrayProjectsSupportScanBuildFailure() {
        String projectKey = "customProject";
        String buildRepoName = projectKey + BUILD_INFO_REPO_KEY_SUFFIX;
        when(buildFacadeMock.getPreferredBuildRepo(null, projectKey))
                .thenReturn(buildRepoName);
        when(buildFacadeMock.canReadBuild(null, null, buildRepoName))
                .thenReturn(true);
        XrayScanBuildModel xrayScanBuildModel = new XrayScanBuildModel();
        xrayScanBuildModel.setProject(projectKey);
        when(xrayAddon.isXrayVersionSupportProjects()).thenReturn(false);

        Response response = xrayResource.scanBuild(xrayScanBuildModel);

        Assert.assertEquals(response.getStatus(), HttpStatus.SC_BAD_REQUEST);
        Assert.assertEquals(response.getEntity(),
                "Scanning non-default build repositories is unsupported by Xray version lower than: " +
                        XrayAddon.XRAY_MIN_VER_WITH_PROJECTS_SUPPORT);
    }

    @Test
    public void testScanBuildCustomBuildRepoXrayProjectsSupportScanBuildSuccess() throws IOException {
        String buildRepoName = "custom" + BUILD_INFO_REPO_KEY_SUFFIX;
        when(buildFacadeMock.getPreferredBuildRepo(buildRepoName, null))
                .thenReturn(buildRepoName);
        when(buildFacadeMock.canReadBuild(null, null, buildRepoName))
                .thenReturn(true);
        XrayScanBuildModel xrayScanBuildModel = new XrayScanBuildModel();
        xrayScanBuildModel.setBuildRepo(buildRepoName);

        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("proto", 1, 0), HttpStatus.SC_OK, "OK");
        when(responseMock.getStatusLine()).thenReturn(statusLine);
        HttpEntity httpEntity = new InputStreamEntity(new StringInputStream(""));
        when(responseMock.getEntity()).thenReturn(httpEntity);
        when(xrayAddon.isXrayVersionSupportProjects()).thenReturn(true);
        XrayScanBuild xrayScanBuild = XrayResourceHelper.toModel(xrayScanBuildModel);
        when(xrayAddon.scanBuild(xrayScanBuild)).thenReturn(responseMock);

        Response response = xrayResource.scanBuild(xrayScanBuildModel);

        Assert.assertEquals(response.getStatus(), HttpStatus.SC_OK);
    }

    @Test
    public void testScanBuildCustomProjectXrayProjectsSupportScanBuildSuccess() throws IOException {
        String projectKey = "customProject";
        String buildRepoName = projectKey + BUILD_INFO_REPO_KEY_SUFFIX;
        when(buildFacadeMock.getPreferredBuildRepo(null, projectKey))
                .thenReturn(buildRepoName);
        when(buildFacadeMock.canReadBuild(null, null, buildRepoName))
                .thenReturn(true);
        XrayScanBuildModel xrayScanBuildModel = new XrayScanBuildModel();
        xrayScanBuildModel.setProject(projectKey);

        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("proto", 1, 0), HttpStatus.SC_OK, "OK");
        when(responseMock.getStatusLine()).thenReturn(statusLine);
        HttpEntity httpEntity = new InputStreamEntity(new StringInputStream(""));
        when(responseMock.getEntity()).thenReturn(httpEntity);
        when(xrayAddon.isXrayVersionSupportProjects()).thenReturn(true);
        XrayScanBuild xrayScanBuild = XrayResourceHelper.toModel(xrayScanBuildModel);
        xrayScanBuild.setBuildRepo(buildRepoName);
        when(xrayAddon.scanBuild(xrayScanBuild)).thenReturn(responseMock);

        Response response = xrayResource.scanBuild(xrayScanBuildModel);

        Assert.assertEquals(response.getStatus(), HttpStatus.SC_OK);
    }

    @Test
    public void testScanBuildBlankProjectBlankBuildRepoDefaultBuildRepoSuccess() throws IOException {
        String buildRepoName = BUILDINFO_REPO_KEY;
        when(buildFacadeMock.canReadBuild(null, null, buildRepoName))
                .thenReturn(true);
        when(buildFacadeMock.getPreferredBuildRepo(any(), any())).thenReturn(buildRepoName);

        XrayScanBuildModel xrayScanBuildModel = new XrayScanBuildModel();
        xrayScanBuildModel.setProject(null);
        xrayScanBuildModel.setBuildRepo(buildRepoName);

        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("proto", 1, 0), HttpStatus.SC_OK, "OK");
        when(responseMock.getStatusLine()).thenReturn(statusLine);
        HttpEntity httpEntity = new InputStreamEntity(new StringInputStream(""));
        when(responseMock.getEntity()).thenReturn(httpEntity);
        XrayScanBuild xrayScanBuild = XrayResourceHelper.toModel(xrayScanBuildModel);
        when(xrayAddon.scanBuild(xrayScanBuild)).thenReturn(responseMock);

        Response response = xrayResource.scanBuild(xrayScanBuildModel);

        Assert.assertEquals(response.getStatus(), HttpStatus.SC_OK);

        xrayScanBuildModel.setProject(StringUtils.EMPTY);
        xrayScanBuildModel.setBuildRepo(StringUtils.EMPTY);

        response = xrayResource.scanBuild(xrayScanBuildModel);

        Assert.assertEquals(response.getStatus(), HttpStatus.SC_OK);
    }

    @Test
    public void testScanBuildXrayReturns404PropagatesStatusToCaller() throws IOException {
        final XrayScanBuildModel model = mockDefaultBuildContext();
        mockXrayScanBuildResponse(HttpStatus.SC_NOT_FOUND);

        Response response = xrayResource.scanBuild(model);

        Assert.assertEquals(response.getStatus(), HttpStatus.SC_NOT_FOUND);
        Assert.assertEquals(response.getEntity(), "Scan summary report is not available");
    }

    @Test
    public void testScanBuildXrayReturnsBadGatewayPropagatesStatusToCaller() throws IOException {
        final XrayScanBuildModel model = mockDefaultBuildContext();
        mockXrayScanBuildResponse(HttpStatus.SC_BAD_GATEWAY);

        Response response = xrayResource.scanBuild(model);

        Assert.assertEquals(response.getStatus(), HttpStatus.SC_BAD_GATEWAY);
        Assert.assertEquals(response.getEntity(), "Scan summary report is not available");
    }

    @Test
    public void testScanBuildXrayReturnsNullResponseFallsBackTo500() throws IOException {
        final XrayScanBuildModel model = mockDefaultBuildContext();
        when(xrayAddon.scanBuild(any())).thenReturn(null);

        Response response = xrayResource.scanBuild(model);

        Assert.assertEquals(response.getStatus(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        Assert.assertEquals(response.getEntity(), "Scan summary report is not available");
    }

    @DataProvider
    public Object[][] notifyPolicyChangeScenarios() {
        return new Object[][] {
                {false, true, false, Response.Status.BAD_REQUEST.getStatusCode(),
                        "Xray is disabled. notifyPolicyChange is not supported when Xray integration is disabled."},
                {false, false, false, Response.Status.BAD_REQUEST.getStatusCode(),
                        "Xray is disabled. notifyPolicyChange is not supported when Xray integration is disabled."},

                {true, true, true, Response.Status.CREATED.getStatusCode(), null},
                {true, false, false, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                        "Encountered error on notification. Check logs for more information."}
        };
    }

    @Test(dataProvider = "notifyPolicyChangeScenarios")
    public void testNotifyPolicyChange(boolean xrayEnabled,
            boolean internal,
            boolean addonResult,
            int expectedStatus,
            String expectedEntity) {
        when(xrayAddon.isXrayEnabled()).thenReturn(xrayEnabled);

        if (xrayEnabled) {
            when(xrayAddon.createPolicyChangeNotification(internal)).thenReturn(addonResult);
        }

        Response response = xrayResource.createPolicyChangeNotification(internal);

        Assert.assertEquals(response.getStatus(), expectedStatus);
        if (expectedEntity != null) {
            Assert.assertEquals(response.getEntity(), expectedEntity);
        }

        if (!xrayEnabled) {
            verify(xrayAddon, never()).createPolicyChangeNotification(Mockito.anyBoolean());
        } else {
            verify(xrayAddon).createPolicyChangeNotification(internal);
        }
    }

    private XrayScanBuildModel mockDefaultBuildContext() {
        when(buildFacadeMock.getPreferredBuildRepo(BUILDINFO_REPO_KEY, null))
                .thenReturn(BUILDINFO_REPO_KEY);
        when(buildFacadeMock.canReadBuild(null, null, BUILDINFO_REPO_KEY))
                .thenReturn(true);
        XrayScanBuildModel model = new XrayScanBuildModel();
        model.setBuildRepo(BUILDINFO_REPO_KEY);
        return model;
    }

    private void mockXrayScanBuildResponse(int statusCode) throws IOException {
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("proto", 1, 0), statusCode, "");
        when(responseMock.getStatusLine()).thenReturn(statusLine);
        HttpEntity httpEntity = new InputStreamEntity(new StringInputStream(""));
        when(responseMock.getEntity()).thenReturn(httpEntity);
        when(xrayAddon.scanBuild(any())).thenReturn(responseMock);
    }

}