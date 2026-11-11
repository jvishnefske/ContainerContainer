package org.artifactory.maven.index;

import org.apache.commons.io.input.CountingInputStream;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.fs.RepoResource;
import org.artifactory.mime.MavenNaming;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.TrafficEntry;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

public class MavenIndexManagerTrafficTest extends ArtifactoryHomeBoundTest {

    public static final boolean FORCE_REMOTE_DOWNLOAD = true;

    @Mock
    private InternalArtifactoryContext context;
    private AutoCloseable mocks;

    @BeforeMethod
    private void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void loadIndex() throws IOException, RepoRejectException {
        TrafficService trafficService = Mockito.mock(TrafficService.class);
        when(context.beanForType(TrafficService.class)).thenReturn(trafficService);
        InternalRepositoryService repositoryService = createRepositoryServiceMock();
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(repositoryService);
        RemoteRepo repo = createRepositoryMock();
        HttpRepo.TrafficAwareRemoteResourceStreamHandle indexHandle = createIndexHandleMock();
        when(repo.conditionalRetrieveResource(MavenNaming.NEXUS_INDEX_GZ_PATH, FORCE_REMOTE_DOWNLOAD))
                .thenReturn(indexHandle);
        HttpRepo.TrafficAwareRemoteResourceStreamHandle propertiesHandle = createPropertiesHandleMock();
        when(repo.downloadResource(MavenNaming.NEXUS_INDEX_PROPERTIES_PATH)).thenReturn(propertiesHandle);

        MavenIndexManager manager = new Stub(repo);
        CountingInputStream cis = mock(CountingInputStream.class);
        when(cis.getByteCount()).thenReturn(10L);
        manager.inputStreamFactory = i -> cis;
        manager.fetchRemoteIndex(FORCE_REMOTE_DOWNLOAD);
        manager.doSaveIndexFiles();

        ArgumentCaptor<TrafficEntry> captor = ArgumentCaptor.forClass(TrafficEntry.class);
        verify(trafficService, times(2)).handleTrafficEntry(captor.capture());
        List<TrafficEntry> captured = captor.getAllValues();
        assertTrafficEntry(captured.get(0), TrafficEntry.TrafficAction.UPLOAD, "127.0.0.1",
                "a:.index/nexus-maven-repository-index.gz", 11, 11,
                10);
        assertTrafficEntry(captured.get(1), TrafficEntry.TrafficAction.UPLOAD, "127.0.0.1",
                "a:.index/nexus-maven-repository-index.properties", 11, 11, 10);
    }

    private InternalRepositoryService createRepositoryServiceMock() throws RepoRejectException, IOException {
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);

        RepoResource indexResource = mock(RepoResource.class);
        RepoPath indexFolderRepoPath = new RepoPathImpl("a", MavenNaming.NEXUS_INDEX_DIR);
        when(indexResource.getSize()).thenReturn(148L);
        when(indexResource.getRepoPath()).thenReturn(indexFolderRepoPath);

        RepoResource propertiesResource = mock(RepoResource.class);
        RepoPath propertiesFolderRepoPath = new RepoPathImpl("a", MavenNaming.NEXUS_INDEX_PROPERTIES);
        when(propertiesResource.getSize()).thenReturn(149L);
        when(propertiesResource.getRepoPath()).thenReturn(propertiesFolderRepoPath);

        return repositoryService;
    }

    private RemoteRepo createRepositoryMock() {
        RemoteRepo repo = mock(RemoteRepo.class);
        when(repo.isStoreArtifactsLocally()).thenReturn(true);
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        RepoPath repoPath = new RepoPathImpl("a", MavenNaming.NEXUS_INDEX_DIR);
        when(localCacheRepo.getRepoPath(MavenNaming.NEXUS_INDEX_DIR)).thenReturn(repoPath);
        when(repo.getLocalCacheRepo()).thenReturn(localCacheRepo);
        return repo;
    }

    private void assertTrafficEntry(TrafficEntry actual, TrafficEntry.TrafficAction action, String address,
                                    String repoPathStr,
                                    int requestedSize, int size, int transferredSize) {
        assertEquals(actual.getAction(), action);
        assertEquals(actual.getActor(), TrafficEntry.TrafficActor.ARTIFACTORY);
        assertEquals(actual.getRedirectTarget(), TrafficEntry.TrafficTarget.DIRECT);
        assertEquals(actual.getUserAddress(), address);
        assertEquals(actual.getRepoPath(), repoPathStr);
        assertEquals(actual.getRequestedSize(), requestedSize);
        assertEquals(actual.getSize(), size);
        assertEquals(actual.getTransferredSize(), transferredSize);
    }

    private HttpRepo.TrafficAwareRemoteResourceStreamHandle createPropertiesHandleMock() {
        HttpRepo.TrafficAwareRemoteResourceStreamHandle handle =
                mock(HttpRepo.TrafficAwareRemoteResourceStreamHandle.class);
        when(handle.getRemoteIp()).thenReturn("127.0.0.1");
        when(handle.getTotalBytesRead()).thenReturn(48L);
        when(handle.getSize()).thenReturn(48L);
        RepoPath repoPath = new RepoPathImpl("a", "properties");
        when(handle.getRepoPath()).thenReturn(repoPath);
        return handle;
    }

    private HttpRepo.TrafficAwareRemoteResourceStreamHandle createIndexHandleMock() {
        HttpRepo.TrafficAwareRemoteResourceStreamHandle handle =
                mock(HttpRepo.TrafficAwareRemoteResourceStreamHandle.class);
        when(handle.getRemoteIp()).thenReturn("127.0.0.2");
        when(handle.getTotalBytesRead()).thenReturn(49L);
        when(handle.getSize()).thenReturn(49L);
        RepoPath repoPath = new RepoPathImpl("a", "index");
        when(handle.getRepoPath()).thenReturn(repoPath);
        return handle;
    }

    private static class Stub extends MavenIndexManager {

        public Stub(RealRepo indexedRepo) {
            super(indexedRepo);
        }

        @Override
        protected void copyLarge(InputStream input, FileOutputStream output) throws IOException {
            // empty
        }

        @Override
        protected void createRepoIndex(File tempIndex, File tempProperties) throws FileNotFoundException {
            RepoIndex repoIndex = mock(RepoIndex.class);
            ResourceStreamHandle handle = mock(ResourceStreamHandle.class);
            when(handle.getSize()).thenReturn(11L);
            when(repoIndex.getIndexHandle()).thenReturn(handle);
            when(repoIndex.getPropertiesHandle()).thenReturn(handle);
            this.repoIndex = repoIndex;
        }
    }

}