package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.download.FolderDownloadService;
import org.artifactory.api.repo.exception.BlackedOutException;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.download.FolderDownloadException;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.InternalArtifactoryResponse;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.StreamRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
public class DownloadFolderArchiveServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;

    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());
        ArtifactoryContextThreadBinder.bind(context);
        // ArtifactoryHome.bind(getOrCreateArtifactoryHomeStub());
        when(context.beanForType(AddonsManager.class)).thenReturn(mock(AddonsManager.class));
    }

    @AfterEach
    public void tearDown() throws Exception {
        // ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testExecute() throws IOException {
        FolderDownloadService folderDownloadService = Mockito.mock(FolderDownloadService.class);
        SecurityService securityService = Mockito.mock(SecurityService.class);
        DownloadFolderArchiveService downloadFolderArchiveService =
                Mockito.spy(new DownloadFolderArchiveService(folderDownloadService, securityService));
        ArtifactoryRestRequest artifactoryRestRequest = Mockito.mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse restResponse = new ArtifactoryRestResponse();
        Mockito.when(artifactoryRestRequest.getQueryParamByKey("repoKey")).thenReturn("test");
        InternalArtifactoryResponse internalArtifactoryResponse = Mockito.mock(InternalArtifactoryResponse.class);
        Mockito.when(internalArtifactoryResponse.isError()).thenReturn(true);
        Mockito.when(internalArtifactoryResponse.getStatusMessage()).thenReturn("status message");
        Mockito.when(internalArtifactoryResponse.getStatus()).thenReturn(403);
        Mockito.doReturn(internalArtifactoryResponse).when(downloadFolderArchiveService)
                .interceptResponse(RepoPathFactory.create("test"));
        downloadFolderArchiveService.execute(artifactoryRestRequest, restResponse);
        Assert.assertEquals(restResponse.getFeedbackMsg().getError(), "status message");
        Assert.assertEquals(restResponse.getResponseCode(), 403);
    }

    @Test
    public void testExecuteWithExceptionDuringInterception() throws IOException {
        FolderDownloadService folderDownloadService = Mockito.mock(FolderDownloadService.class);
        SecurityService securityService = Mockito.mock(SecurityService.class);
        DownloadFolderArchiveService downloadFolderArchiveService =
                Mockito.spy(new DownloadFolderArchiveService(folderDownloadService, securityService));
        ArtifactoryRestRequest artifactoryRestRequest = Mockito.mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse restResponse = new ArtifactoryRestResponse();
        Mockito.when(artifactoryRestRequest.getQueryParamByKey("repoKey")).thenReturn("test");
        Mockito.doThrow(new IOException("IOException")).when(downloadFolderArchiveService)
                .interceptResponse(RepoPathFactory.create("test"));
        downloadFolderArchiveService.execute(artifactoryRestRequest, restResponse);
        Assert.assertEquals(restResponse.getFeedbackMsg().getError(), "IOException");
        Assert.assertEquals(restResponse.getResponseCode(), HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void testExecuteNoAnonymousAccess() throws IOException {
        FolderDownloadService folderDownloadService = Mockito.mock(FolderDownloadService.class);
        FolderDownloadConfigDescriptor folderDownloadConfigDescriptor =
                Mockito.mock(FolderDownloadConfigDescriptor.class);
        Mockito.when(folderDownloadConfigDescriptor.isEnabledForAnonymous()).thenReturn(false);
        Mockito.when(folderDownloadService.getFolderDownloadConfig()).thenReturn(folderDownloadConfigDescriptor);
        SecurityService securityService = Mockito.mock(SecurityService.class);
        Mockito.when(securityService.isAnonymous()).thenReturn(true);
        DownloadFolderArchiveService downloadFolderArchiveService =
                Mockito.spy(new DownloadFolderArchiveService(folderDownloadService, securityService));
        ArtifactoryRestRequest artifactoryRestRequest = Mockito.mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse restResponse = new ArtifactoryRestResponse();
        Mockito.when(artifactoryRestRequest.getQueryParamByKey("repoKey")).thenReturn("test");
        InternalArtifactoryResponse internalArtifactoryResponse = Mockito.mock(InternalArtifactoryResponse.class);
        Mockito.when(internalArtifactoryResponse.isError()).thenReturn(false);
        Mockito.doReturn(internalArtifactoryResponse).when(downloadFolderArchiveService)
                .interceptResponse(RepoPathFactory.create("test"));
        downloadFolderArchiveService.execute(artifactoryRestRequest, restResponse);
        Assert.assertEquals(restResponse.getFeedbackMsg().getError(),
                "You must be logged in to download a folder or repository.");
        Assert.assertEquals(restResponse.getResponseCode(), 403);
    }

    @Test
    public void testExecuteWithBlackedOutException() throws BlackedOutException {
        // given
        String repoKey = "repoKey";
        FolderDownloadService folderDownloadService = Mockito.mock(FolderDownloadService.class);
        when(folderDownloadService.downloadFolder(any(), any(), anyBoolean()))
                .thenThrow(new BlackedOutException(mock(RepoConfig.class), RepoPathFactory.create(repoKey)));

        SecurityService securityService = Mockito.mock(SecurityService.class);
        DownloadFolderArchiveService downloadFolderArchiveService =
                spy(new DownloadFolderArchiveService(folderDownloadService, securityService));

        ArtifactoryRestRequest artifactoryRestRequest = Mockito.mock(ArtifactoryRestRequest.class);
        when(artifactoryRestRequest.getQueryParamByKey(repoKey)).thenReturn("test");
        StreamRestResponse restResponse = new StreamRestResponse();

        // when
        downloadFolderArchiveService.execute(artifactoryRestRequest, restResponse);

        // then
        Assert.assertEquals(restResponse.getFeedbackMsg().getError(),
                "The repository 'null' is blacked out and cannot serve artifact 'repoKey:'.");
        Assert.assertEquals(restResponse.getResponseCode(), HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testExecuteWithFolderDownloadException() throws BlackedOutException {
        // given
        String repoKey = "repoKey";
        String errorMessage = "message";
        FolderDownloadService folderDownloadService = Mockito.mock(FolderDownloadService.class);
        when(folderDownloadService.downloadFolder(any(), any(), anyBoolean()))
                .thenThrow(new FolderDownloadException(errorMessage, HttpStatus.SC_BAD_REQUEST));

        SecurityService securityService = Mockito.mock(SecurityService.class);
        DownloadFolderArchiveService downloadFolderArchiveService =
                spy(new DownloadFolderArchiveService(folderDownloadService, securityService));

        ArtifactoryRestRequest artifactoryRestRequest = Mockito.mock(ArtifactoryRestRequest.class);
        when(artifactoryRestRequest.getQueryParamByKey(repoKey)).thenReturn("test");
        StreamRestResponse restResponse = new StreamRestResponse();

        // when
        downloadFolderArchiveService.execute(artifactoryRestRequest, restResponse);

        // then
        Assert.assertEquals(restResponse.getFeedbackMsg().getError(), errorMessage);
        Assert.assertEquals(restResponse.getResponseCode(), HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void testExecuteWithException() throws BlackedOutException {
        // given
        String repoKey = "repoKey";
        String errorMessage = "message";
        FolderDownloadService folderDownloadService = Mockito.mock(FolderDownloadService.class);
        when(folderDownloadService.downloadFolder(any(), any(), anyBoolean()))
                .thenThrow(new NullPointerException(errorMessage));

        SecurityService securityService = Mockito.mock(SecurityService.class);
        DownloadFolderArchiveService downloadFolderArchiveService =
                spy(new DownloadFolderArchiveService(folderDownloadService, securityService));

        ArtifactoryRestRequest artifactoryRestRequest = Mockito.mock(ArtifactoryRestRequest.class);
        when(artifactoryRestRequest.getQueryParamByKey(repoKey)).thenReturn("test");
        StreamRestResponse restResponse = new StreamRestResponse();

        // when
        downloadFolderArchiveService.execute(artifactoryRestRequest, restResponse);

        // then
        Assert.assertEquals(restResponse.getFeedbackMsg().getError(), errorMessage);
        Assert.assertEquals(restResponse.getResponseCode(), HttpStatus.SC_OK);
    }

    @Test
    public void testInterceptResponse() throws IOException {
        DownloadFolderArchiveService downloadFolderArchiveService =
                Mockito.spy(new DownloadFolderArchiveService(null, null));
        AddonsManager addonsManager = Mockito.mock(DummyInterceptorManager.class, InvocationOnMock::callRealMethod);
        try {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            Mockito.when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
            ArtifactoryContextThreadBinder.bind(context);
            InternalArtifactoryResponse internalArtifactoryResponse =
                    downloadFolderArchiveService.interceptResponse(RepoPathFactory.create("test"));
            Assert.assertEquals(internalArtifactoryResponse.getStatus(), 429);
            Assert.assertTrue(internalArtifactoryResponse.isError());
            Assert.assertEquals(internalArtifactoryResponse.getStatusMessage(), "got an error");
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    private abstract static class DummyInterceptorManager implements AddonsManager {

        @Override
        public void interceptResponse(ArtifactoryResponse response,
                ArtifactoryRequest request, boolean checkReadOnly) throws IOException {
            response.sendError(429, "got an error", LoggerFactory.getLogger(DownloadFolderArchiveServiceTest.class));
        }
    }
}