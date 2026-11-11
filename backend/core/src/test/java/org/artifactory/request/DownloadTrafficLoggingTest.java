package org.artifactory.request;

import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.input.NullInputStream;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.security.SecurityService;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.TrafficEntry;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.LongConsumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

public class DownloadTrafficLoggingTest extends ArtifactoryHomeBoundTest {

    @DataProvider(name = "data")
    public Object[][] data() {
        return new Object[][] {
                {
                        "content", "", 7, TrafficEntry.TrafficActor.USER, "a:b", 7, 7, 7,
                        TrafficEntry.TrafficTarget.DIRECT, TrafficEntry.TrafficAction.DOWNLOAD
                },
                {
                        "content", "Range: bytes=0-1", 7, TrafficEntry.TrafficActor.USER, "a:b", 2, 7, 7,
                        TrafficEntry.TrafficTarget.DIRECT, TrafficEntry.TrafficAction.DOWNLOAD
                },
                {
                        "content", "1", 5, TrafficEntry.TrafficActor.USER, "a:b", 7, 7, 5,
                        TrafficEntry.TrafficTarget.DIRECT, TrafficEntry.TrafficAction.DOWNLOAD
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
    public void testUpdateResponseForProperties(String content,
            String rangeHeader,
            long readCount,
            TrafficEntry.TrafficActor actor,
            String repoPathStr,
            int requestedSize,
            int size,
            int transferredSize,
            TrafficEntry.TrafficTarget target,
            TrafficEntry.TrafficAction action
    ) throws IOException {
        TrafficService trafficService = Mockito.mock(TrafficService.class);
        SecurityService securityService = mock(SecurityService.class);
        RequestResponseHelper helper = new RequestResponseHelper(trafficService, securityService,
                inputStream -> new StreamStub(inputStream, readCount));
        ArtifactoryResponse response = createResponseMock(readCount);
        RepositoryService repositoryService = Mockito.mock(RepositoryService.class);
        when(ContextHelper.get().getRepositoryService()).thenReturn(repositoryService);
        RepoPath repoPath = new RepoPathImpl("a", "b");
        FileInfoImpl fileInfo = new FileInfoImpl(repoPath);
        fileInfo.setSize(content.length());
        RepoResource res = new FileResource(fileInfo);
        InternalRequestContext requestContext = Mockito.mock(InternalRequestContext.class);
        Request request = Mockito.mock(Request.class);
        if (!rangeHeader.isEmpty()) {
            when(request.getHeader(HttpHeaders.RANGE)).thenReturn(rangeHeader);
        }
        when(requestContext.getRequest()).thenReturn(request);

        helper.updateResponseForProperties(response, res, content, MediaType.APPLICATION_XML, requestContext);

        assertCapturedTrafficEntry(trafficService, actor, repoPathStr + "?propertiesXml", requestedSize, size,
                transferredSize,
                target,
                action);
    }

    @Test(dataProvider = "data")
    public void testDownloadSendBodyResponse(
            String content,
            String rangeHeader,
            long readCount,
            TrafficEntry.TrafficActor actor,
            String repoPathStr,
            int requestedSize,
            int size,
            int transferredSize,
            TrafficEntry.TrafficTarget target,
            TrafficEntry.TrafficAction action
    ) throws IOException {
        TrafficService trafficService = Mockito.mock(TrafficService.class);
        SecurityService securityService = mock(SecurityService.class);
        RequestResponseHelper helper = new RequestResponseHelper(trafficService, securityService,
                inputStream -> new StreamStub(inputStream, readCount));
        ArtifactoryResponse response = createResponseMock(readCount);
        RepositoryService repositoryService = Mockito.mock(RepositoryService.class);
        when(ContextHelper.get().getRepositoryService()).thenReturn(repositoryService);
        RepoPath repoPath = new RepoPathImpl("a", "b");
        FileInfoImpl fileInfo = new FileInfoImpl(repoPath);
        fileInfo.setSize(content.length());
        RepoResource res = new FileResource(fileInfo);
        ResourceStreamHandle handle = Mockito.mock(ResourceStreamHandle.class);
        when(handle.getInputStream()).thenReturn(new NullInputStream());
        InternalRequestContext requestContext = Mockito.mock(InternalRequestContext.class);
        Request request = Mockito.mock(Request.class);
        if (!rangeHeader.isEmpty()) {
            when(request.getHeader(HttpHeaders.RANGE)).thenReturn(rangeHeader);
        }
        when(requestContext.getRequest()).thenReturn(request);

        helper.sendBodyResponse(response, res, handle, requestContext, true);

        assertCapturedTrafficEntry(trafficService, actor, repoPathStr, requestedSize, size, transferredSize,
                target,
                action);
    }

    @Test(dataProvider = "data")
    public void testChecksumSendBodyResponse(
            String content,
            String rangeHeader,
            long readCount,
            TrafficEntry.TrafficActor actor,
            String repoPathStr,
            int requestedSize,
            int size,
            int transferredSize,
            TrafficEntry.TrafficTarget target,
            TrafficEntry.TrafficAction action
    ) throws IOException {
        TrafficService trafficService = Mockito.mock(TrafficService.class);
        SecurityService securityService = mock(SecurityService.class);
        RequestResponseHelper helper = new RequestResponseHelper(trafficService, securityService,
                inputStream -> new StreamStub(inputStream, readCount));
        ArtifactoryResponse response = createResponseMock(readCount);
        RepoPath repoPath = new RepoPathImpl("a", "b");
        Request request = Mockito.mock(Request.class);
        if (!rangeHeader.isEmpty()) {
            when(request.getHeader(HttpHeaders.RANGE)).thenReturn(rangeHeader);
        }

        helper.sendBodyResponseForChecksum(response, repoPath, content, request, false);

        assertCapturedTrafficEntry(trafficService, actor, repoPathStr, requestedSize, size, transferredSize,
                target,
                action);
    }

    private ArtifactoryResponse createResponseMock(long readCount) throws IOException {
        ArtifactoryResponse response = Mockito.mock(ArtifactoryResponse.class);
        doAnswer(invocation -> {
            LongConsumer callback = invocation.getArgument(1);
            callback.accept(readCount);
            return null;
        }).when(response).sendStream(any(), any());
        return response;
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

    private static class StreamStub extends CountingInputStream {

        private final long byteCount;

        public StreamStub(InputStream in, long byteCount) {
            super(in);
            this.byteCount = byteCount;
        }

        @Override
        public synchronized long getByteCount() {
            return this.byteCount;
        }
    }

}
