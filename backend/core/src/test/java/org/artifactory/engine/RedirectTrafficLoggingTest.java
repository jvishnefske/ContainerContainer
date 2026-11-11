package org.artifactory.engine;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.ControlledCacheRedirectService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.Request;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.traffic.TrafficEntry;
import org.artifactory.traffic.TrafficService;
import org.jfrog.storage.binstore.ifc.RedirectType;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

public class RedirectTrafficLoggingTest extends ArtifactoryHomeBoundTest {

    @DataProvider(name = "data")
    public Object[][] data() {
        return new Object[][] {
                {
                        false, "", TrafficEntry.TrafficActor.USER, "a:b", 7, 7, 7,
                        TrafficEntry.TrafficTarget.S3, TrafficEntry.TrafficAction.REDIRECT
                },
                {
                        false, "Range: bytes=0-1", TrafficEntry.TrafficActor.USER, "a:b", 2, 7, 2,
                        TrafficEntry.TrafficTarget.S3, TrafficEntry.TrafficAction.REDIRECT
                },
                {
                        true, "", TrafficEntry.TrafficActor.USER, "a:b", 7, 7, 7,
                        TrafficEntry.TrafficTarget.S3, TrafficEntry.TrafficAction.REDIRECT
                },
                {
                        true, "Range: bytes=0-1", TrafficEntry.TrafficActor.USER, "a:b", 7, 7, 7,
                        TrafficEntry.TrafficTarget.S3, TrafficEntry.TrafficAction.REDIRECT
                }
        };
    }

    @BeforeMethod
    public void setup() {
        ArtifactoryContext artifactoryContext = Mockito.mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterMethod
    public void afterMethod() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test(dataProvider = "data")
    public void testName(
            boolean ignoreRangeHeader,
            String rangeHeader,
            TrafficEntry.TrafficActor actor,
            String repoPathStr,
            int requestedSize,
            int size,
            int transferredSize,
            TrafficEntry.TrafficTarget target,
            TrafficEntry.TrafficAction action
    ) throws IOException {
        homeStub.getArtifactoryProperties()
                .setProperty(ConstantValues.trafficIgnoreRangeHeaderWhenCalcBinarySizeForRedirect.getPropertyName(),
                        Boolean.toString(ignoreRangeHeader));

        SecurityService securityService = mock(SecurityService.class);
        TrafficService trafficService = mock(TrafficService.class);
        ControlledCacheRedirectService controlledCacheRedirectService = mock(ControlledCacheRedirectService.class);
        when(controlledCacheRedirectService.shouldRedirect(any(), any(), any())).thenReturn(false);
        DownloadServiceImpl downloadService = new DownloadServiceImpl();
        downloadService.trafficService = trafficService;
        downloadService.securityService = securityService;
        downloadService.controlledCacheRedirectService = controlledCacheRedirectService;
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoPath repoPath = new RepoPathImpl("a", "b");
        FileInfoImpl fileInfo = new FileInfoImpl(repoPath);
        fileInfo.setSize("content".length());
        RepoResource resource = new FileResource(fileInfo);
        ResourceStreamHandle handle = mock(ResourceStreamHandle.class);
        Request request = mock(Request.class);
        if (!rangeHeader.isEmpty()) {
            when(request.getHeader("Range")).thenReturn(rangeHeader);
        }
        when(requestContext.getRequest()).thenReturn(request);
        Repo responseRepo = mock(Repo.class);

        downloadService.sendStreamOrRedirect(requestContext, response, resource, repoPath, handle,
                "signedUrl", request, responseRepo, RedirectType.S3, true);

        assertCapturedTrafficEntry(trafficService, actor, repoPathStr, requestedSize, size, transferredSize, target,
                action);
    }

    protected void assertCapturedTrafficEntry(TrafficService trafficService, TrafficEntry.TrafficActor actor,
            String repoPathStr, int requestedSize, int size, int transferredSize, TrafficEntry.TrafficTarget target,
            TrafficEntry.TrafficAction action) {
        ArgumentCaptor<TrafficEntry> captor = ArgumentCaptor.forClass(TrafficEntry.class);
        verify(trafficService, times(1)).handleTrafficEntry(captor.capture());
        TrafficEntry actual = captor.getValue();

        assertTrafficEntry(actual, actor, repoPathStr, requestedSize, size, transferredSize, target, action);
    }

    protected void assertTrafficEntry(TrafficEntry actual, TrafficEntry.TrafficActor actor, String repoPathStr,
            int requestedSize, int size, int transferredSize, TrafficEntry.TrafficTarget target,
            TrafficEntry.TrafficAction action) {
        assertEquals(actual.getAction(), action);
        assertEquals(actual.getActor(), actor);
        assertEquals(actual.getRedirectTarget(), target);
        assertEquals(actual.getRepoPath(), repoPathStr);
        assertEquals(actual.getRequestedSize(), requestedSize);
        assertEquals(actual.getSize(), size);
        assertEquals(actual.getTransferredSize(), transferredSize);
    }

}
