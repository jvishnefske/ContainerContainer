package org.artifactory.download;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.io.checksum.policy.LocalRepoChecksumPolicy;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.TrafficEntry;
import org.jfrog.common.archive.ArchiveType;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Optional;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class FolderArchiveStreamerTrafficTest extends ArtifactoryHomeBoundTest {

    private static final String DATA = "test";
    private static final int SIZE = 8;

    private ArtifactoryContext context;

    private ArchiveEntry archiveEntry;
    private ArchiveOutputStream archiveOutputStream;
    private RepoPath repoPath;
    private Optional<CountingInputStream> countingInputStream;
    private TrafficService trafficService;

    @BeforeMethod
    public void setUp() {
        context = Mockito.mock(ArtifactoryContext.class);
        when(context.beanForType(AuthorizationService.class)).thenReturn(mock(AuthorizationService.class));
        when(context.beanForType(SecurityService.class)).thenReturn(mock(SecurityService.class));
        when(context.beanForType(RepositoryService.class)).thenReturn(mock(RepositoryService.class));
        ArtifactoryContextThreadBinder.bind(context);
        trafficService = Mockito.mock(TrafficService.class);
        when(context.beanForType(TrafficService.class)).thenReturn(trafficService);
        repoPath = new RepoPathImpl("a", "b");
        archiveEntry = mock(ArchiveEntry.class);
        archiveOutputStream = mock(ArchiveOutputStream.class);
        InputStream stream = IOUtils.toInputStream(DATA, Charset.defaultCharset());
        countingInputStream = Optional.of(new CountingInputStream(stream));
    }

    @AfterMethod
    public void afterMethod() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void happyFlow() throws IOException {
        FolderArchiveStreamer streamer = new FolderArchiveStreamer(repoPath, ArchiveType.TGZ,
                1, 1, new LocalRepoChecksumPolicy(), true, true);
        streamer.writeArchiveEntry(archiveEntry, archiveOutputStream, SIZE, repoPath, countingInputStream);

        TrafficEntry captured = getCapturedEntity(trafficService);
        assertValid(captured, SIZE, DATA.length());
    }

    @Test(expectedExceptions = IOException.class)
    public void exceptionWhileProcessing() throws IOException {
        doThrow(new IOException()).when(archiveOutputStream).putArchiveEntry(any());

        FolderArchiveStreamer streamer = new FolderArchiveStreamer(repoPath, ArchiveType.TGZ,
                1, 1, new LocalRepoChecksumPolicy(), true, true);
        streamer.writeArchiveEntry(archiveEntry, archiveOutputStream, SIZE, repoPath, countingInputStream);

        TrafficEntry captured = getCapturedEntity(trafficService);
        assertValid(captured, SIZE, DATA.length());
    }

    private TrafficEntry getCapturedEntity(TrafficService trafficService) {
        ArgumentCaptor<TrafficEntry> trafficEntryV2Captor = ArgumentCaptor.forClass(TrafficEntry.class);
        verify(trafficService, times(1)).handleTrafficEntry(trafficEntryV2Captor.capture());
        TrafficEntry captured = trafficEntryV2Captor.getValue();
        return captured;
    }

    private void assertValid(TrafficEntry captured, long fileSize, long transferred) {
        assertEquals(captured.getAction(), TrafficEntry.TrafficAction.DOWNLOAD);
        assertEquals(captured.getActor(), TrafficEntry.TrafficActor.USER);
        assertEquals(captured.getRedirectTarget(), TrafficEntry.TrafficTarget.DIRECT);
        assertEquals(captured.getRepoPath(), "a:b");
        assertEquals(captured.getSize(), fileSize);
        assertEquals(captured.getRequestedSize(), fileSize);
        assertEquals(captured.getTransferredSize(), transferred);
    }

}