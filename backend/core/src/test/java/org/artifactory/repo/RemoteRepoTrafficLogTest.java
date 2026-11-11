package org.artifactory.repo;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.common.RemoteRepoPathImpl;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.remote.interceptor.RemoteRepoInterceptor;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.Request;
import org.artifactory.request.RequestContext;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.traffic.TrafficEntry;
import org.artifactory.traffic.TrafficService;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collections;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class RemoteRepoTrafficLogTest extends ArtifactoryHomeBoundTest {

    ArtifactoryContext context;

    @BeforeClass
    public void init() {
        context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterClass
    public void unbind() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void withoutError() throws IOException, RepoRejectException {
        doTest(false);
    }

    @Test
    public void withError() throws IOException, RepoRejectException {
        doTest(true);
    }

    protected void doTest(boolean simulateError) throws RepoRejectException, IOException {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.currentUsername()).thenReturn("");
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beansForType(RemoteRepoInterceptor.class)).thenReturn(Collections.EMPTY_MAP);
        TrafficService trafficService = mock(TrafficService.class);
        when(context.beanForType(TrafficService.class)).thenReturn(trafficService);
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setChecksumPolicyType(ChecksumPolicyType.IGNORE_AND_GEN);
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        if (simulateError) {
            when(repositoryService.saveResource(any(), any())).thenThrow(new IOException());
        }
        ResearchService researchService = mock(ResearchService.class);
        RemoteRepo oldRemoteRepo = mock(RemoteRepo.class);
        Request request = mock(Request.class);
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        when(requestContext.getRequest()).thenReturn(request);

        RepoResource remoteResource = mock(RepoResource.class);
        RemoteRepoPathImpl remoteRepoPath = new RemoteRepoPathImpl("origin", new RepoPathImpl("a", "b"));
        FileInfoImpl fileInfo = new FileInfoImpl(remoteRepoPath);
        when(remoteResource.getInfo()).thenReturn(fileInfo);
        when(remoteResource.getRepoPath()).thenReturn(remoteRepoPath);
        when(remoteResource.getSize()).thenReturn(50L);
        HttpRepo.TrafficAwareRemoteResourceStreamHandle handle =
                mock(HttpRepo.TrafficAwareRemoteResourceStreamHandle.class);
        when(handle.getTotalBytesRead()).thenReturn(48L);
        when(handle.getRemoteIp()).thenReturn("remote-ip");
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        when(request.getRepoPath()).thenReturn(remoteRepoPath);

        HttpRepoTestStub httpRepo = new HttpRepoTestStub(null, repoConfig, repositoryService, addonsManager,
                researchService, oldRemoteRepo);
        httpRepo.handle = handle;

        try {
            httpRepo.doDownloadAndSave(requestContext, remoteResource);
        } catch (Exception ignored) {
            // empty
        }

        assertCapturedTrafficEntry(trafficService);
    }

    protected void assertCapturedTrafficEntry(TrafficService trafficService) {
        ArgumentCaptor<TrafficEntry> captor = ArgumentCaptor.forClass(TrafficEntry.class);
        verify(trafficService, times(1)).handleTrafficEntry(captor.capture());
        TrafficEntry actual = captor.getValue();

        assertEquals(actual.getAction(), TrafficEntry.TrafficAction.UPLOAD);
        assertEquals(actual.getActor(), TrafficEntry.TrafficActor.USER);
        assertEquals(actual.getRedirectTarget(), TrafficEntry.TrafficTarget.DIRECT);
        assertEquals(actual.getRepoPath(), "a:b");
        assertEquals(actual.getRequestedSize(), 50);
        assertEquals(actual.getSize(), 50);
        assertEquals(actual.getTransferredSize(), 48);
    }

    private static class HttpRepoTestStub extends HttpRepo {

        private HttpRepo.TrafficAwareRemoteResourceStreamHandle handle;

        public HttpRepoTestStub(HttpRepoDescriptor descriptor, RemoteRepoConfig repoConfig,
                InternalRepositoryService repositoryService, AddonsManager addonsManager,
                ResearchService researchService, RemoteRepo oldRemoteRepo) {
            super(descriptor, repoConfig, repositoryService, addonsManager, researchService,
                    oldRemoteRepo, null, null);
        }

        @Override
        protected ResourceStreamHandle getAltContent(RepoPath repoPath) {
            return null;
        }

        @Override
        public ResourceStreamHandle downloadResource(String relPath, RequestContext requestContext) throws IOException {
            return handle;
        }

        @Override
        void addTrafficEntry(RepoResource remoteResource, ResourceStreamHandle handle, long remoteRequestStartTime,
                RepoResource cachedResource, InternalRequestContext requestContext) {
            // empty
        }
    }
}
