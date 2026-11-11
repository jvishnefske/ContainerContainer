package org.artifactory.engine;

import jakarta.annotation.Nonnull;
import org.apache.commons.collections4.iterators.IteratorEnumeration;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.security.SystemAuthenticationToken;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.entry.LegacyTrafficEntry;
import org.artifactory.traffic.TrafficEntry;
import org.jfrog.storage.binstore.ifc.ArtificialInputStream;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

import static org.artifactory.api.security.access.UserTokenSpec.ARTIFACTORY_FEDERATION_USER_PREFIX;
import static org.artifactory.api.security.access.UserTokenSpec.TOKEN_PREFIX;
import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class UploadServiceTrafficLogTest extends ArtifactoryHomeBoundTest {

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
    public void withoutError() {
        TrafficEntry captured = getTrafficEntry(false);
        assertValid(captured);
    }

    @Test
    public void withError() {
        TrafficEntry captured = getTrafficEntry(true);
        assertValid(captured);
    }

    private void assertValid(TrafficEntry captured) {
        assertEquals(captured.getAction(), TrafficEntry.TrafficAction.UPLOAD);
        assertEquals(captured.getActor(), TrafficEntry.TrafficActor.USER);
        assertEquals(captured.getRedirectTarget(), TrafficEntry.TrafficTarget.DIRECT);
        assertEquals(captured.getUserAddress(), "127.0.0.1");
        assertEquals(captured.getRepoPath(), "a:b");
        assertEquals(captured.getSize(), 42);
        assertEquals(captured.getRequestedSize(), 42);
        assertEquals(captured.getTransferredSize(), 40);
    }

    private TrafficEntry getTrafficEntry(boolean simulateError) {
        prepareAuth("");
        TrafficService trafficService = mock(TrafficService.class);
        CountingInputStream countingInputStream = mock(CountingInputStream.class);
        when(countingInputStream.getByteCount()).thenReturn(40L);
        UploadServiceTestStub uploadService = new UploadServiceTestStub(trafficService,
                inputStream -> countingInputStream);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoKey()).thenReturn("repo");
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        LocalRepo repo = mock(LocalRepo.class);
        RepoResource res = getRepoResource(42L);
        uploadService.simulateError = simulateError;
        try {
            uploadService.uploadItemWithProvidedContent(request, response, repo, res);
        } catch (Exception e) {
            if (!simulateError) {
                fail();
            }
        }
        return verifyAndGetTrafficEntryV2(trafficService);
    }

    private TrafficEntry verifyAndGetTrafficEntryV2(TrafficService trafficService) {
        ArgumentCaptor<TrafficEntry> captor = ArgumentCaptor.forClass(TrafficEntry.class);
        verify(trafficService, times(1))
                .handleTrafficEntry(captor.capture());
        return captor.getValue();
    }

    @Test
    public void testUploadWithArtificialInputStream() throws IOException, RepoRejectException {
        prepareAuth(TOKEN_PREFIX + ARTIFACTORY_FEDERATION_USER_PREFIX + "a_to_b");
        TrafficService trafficService = mock(TrafficService.class);
        ArtificialInputStream stream = mock(ArtificialInputStream.class);
        when(stream.getLength()).thenReturn(55L);
        UploadServiceTestStub uploadService = new UploadServiceTestStub(trafficService, null);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getInputStream()).thenReturn(stream);
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        LocalRepo repo = mock(LocalRepo.class);
        RepoResource res = getRepoResource(55L);
        when(request.getRepoKey()).thenReturn("repo");
        uploadService.uploadItemWithProvidedContent(request, response, repo, res);

        TrafficEntry value = verifyAndGetTrafficEntryV2(trafficService);

        assertEquals(value.getTransferredSize(), 55);
        assertEquals(value.getActor(), TrafficEntry.TrafficActor.FEDERATION);
    }

    @Test
    public void testUploadWithLogShippingAndUserSystem() throws IOException, RepoRejectException {
        prepareAuth(SecurityService.USER_SYSTEM);
        TrafficService trafficService = mock(TrafficService.class);
        ArtificialInputStream stream = mock(ArtificialInputStream.class);
        SystemAuthenticationToken authentication = mock(SystemAuthenticationToken.class);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(stream.getLength()).thenReturn(55L);
        UploadServiceTestStub uploadService = new UploadServiceTestStub(trafficService, null);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getInputStream()).thenReturn(stream);
        when(request.getRepoKey()).thenReturn(LOG_SHIPPING_REPO_KEY);
        when(request.getHeaderValues(HttpHeaders.USER_AGENT))
                .thenReturn(new IteratorEnumeration(List.of("observability", "observability").iterator()));
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        LocalRepo repo = mock(LocalRepo.class);
        RepoResource res = getRepoResource(55L);

        uploadService.uploadItemWithProvidedContent(request, response, repo, res);

        TrafficEntry value = verifyAndGetTrafficEntryV2(trafficService);

        assertEquals(value.getTransferredSize(), 55);
        assertEquals(value.getActor(), TrafficEntry.TrafficActor.OBSERVABILITY);
        assertEquals(value.getAction(), TrafficEntry.TrafficAction.UPLOAD);
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testUploadWithUsageAndUserSystem() throws IOException, RepoRejectException {
        prepareAuth(SecurityService.USER_SYSTEM);
        TrafficService trafficService = mock(TrafficService.class);
        ArtificialInputStream stream = mock(ArtificialInputStream.class);
        SystemAuthenticationToken authentication = mock(SystemAuthenticationToken.class);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(stream.getLength()).thenReturn(55L);
        UploadServiceTestStub uploadService = new UploadServiceTestStub(trafficService, null);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getInputStream()).thenReturn(stream);
        when(request.getRepoKey()).thenReturn(USAGE_REPO_KEY);
        when(request.getHeaderValues(HttpHeaders.USER_AGENT))
                .thenReturn(new IteratorEnumeration(List.of("observability", "observability").iterator()));
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        LocalRepo repo = mock(LocalRepo.class);
        RepoResource res = getRepoResource(55L);

        uploadService.uploadItemWithProvidedContent(request, response, repo, res);

        TrafficEntry value = verifyAndGetTrafficEntryV2(trafficService);

        assertEquals(value.getTransferredSize(), 55);
        assertEquals(value.getActor(), TrafficEntry.TrafficActor.OBSERVABILITY);
        assertEquals(value.getAction(), TrafficEntry.TrafficAction.UPLOAD);
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testUploadFailsWithArtificialInputStream() throws IOException, RepoRejectException {
        prepareAuth(TOKEN_PREFIX + ARTIFACTORY_FEDERATION_USER_PREFIX + "a_to_b");
        TrafficService trafficService = mock(TrafficService.class);
        ArtificialInputStream stream = mock(ArtificialInputStream.class);
        when(stream.getLength()).thenReturn(55L);
        UploadServiceTestStub uploadService = new UploadServiceTestStub(trafficService, null);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getInputStream()).thenReturn(stream);
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        LocalRepo repo = mock(LocalRepo.class);
        RepoResource res = getRepoResource(55L);

        uploadService.uploadItemWithProvidedContent(request, response, repo, res);
        verify(trafficService, times(0))
                .handleTrafficEntry(any(TrafficEntry.class));
        verify(trafficService, times(0))
                .handleTrafficEntry(any(LegacyTrafficEntry.class));
    }

    @Nonnull
    private RepoResource getRepoResource(long size) {
        RepoResource res = mock(RepoResource.class);
        when(res.getSize()).thenReturn(size);
        RepoPath repoPath = new RepoPathImpl("a", "b");
        when(res.getRepoPath()).thenReturn(repoPath);
        return res;
    }

    private void prepareAuth(String userName) {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.currentUsername()).thenReturn(userName);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        homeStub.getArtifactoryProperties()
                .setProperty(ConstantValues.test.getPropertyName(), "true");
    }

    private static class UploadServiceTestStub extends UploadServiceImpl {

        private boolean simulateError;

        public UploadServiceTestStub(TrafficService trafficService,
                Function<InputStream, CountingInputStream> streamFactory) {
            super();
            setTrafficService(trafficService);
            setStreamFactory(streamFactory);
        }

        @Override
        void uploadItemWithContent(ArtifactoryRequest request, ArtifactoryResponse response, LocalRepo repo,
                RepoResource res, InputStream inputStream) throws IOException {
            if (simulateError) {
                throw new IOException();
            }
        }
    }
}
