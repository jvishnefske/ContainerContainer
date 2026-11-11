package org.artifactory.repo.db.importexport;

import org.apache.commons.io.input.CountingInputStream;
import org.artifactory.api.config.ImportSettingsImpl;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.TrafficEntry;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryTreeElement;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

public class DbRepoImportHandlerTrafficLogTest extends ArtifactoryHomeBoundTest {

    private static final long TRANSFERRED_SIZE = 24L;
    private static final long ENTITY_SIZE = 48L;

    private ArtifactoryStorageContext context;
    private TrafficService trafficService;
    private InputStream stream;
    private MutableVfsFile mutableFile;

    private DbRepoImportHandler handler;
    private ArgumentCaptor<TrafficEntry> trafficEntryV2Captor;

    @BeforeClass
    public void init() {
        context = mock(ArtifactoryStorageContext.class);
        ArtifactoryBinaryService binaryService = mock(ArtifactoryBinaryService.class);
        when(context.beanForType(ArtifactoryBinaryService.class)).thenReturn(binaryService);
        BinaryTreeElement<Map<String, String>> rootTreeElement = new BinaryTreeElement<>();
        Map<String, String> map = new HashMap<>();
        map.put("type", "external-file");
        map.put("externalDir", "externalDir");
        rootTreeElement.setData(map);
        BinaryProvidersInfo<Map<String, String>> binaryProvidersInfo = new BinaryProvidersInfo<>("",
                rootTreeElement);
        when(binaryService.getBinaryProvidersInfo()).thenReturn(binaryProvidersInfo);
        ArtifactoryContextThreadBinder.bind(context);
        ImportSettingsImpl settings = new ImportSettingsImpl(null);
        handler = new DbRepoImportHandler(null, settings, null, "127.0.0.1");
        CountingInputStream countingInputStream = mock(CountingInputStream.class);
        when(countingInputStream.getByteCount()).thenReturn(TRANSFERRED_SIZE);
        handler.setStreamFactory(inputStream -> countingInputStream);
        stream = mock(InputStream.class);
    }

    @AfterClass
    public void unbind() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @BeforeMethod
    public void setUp() {
        mutableFile = mock(MutableVfsFile.class);
        when(mutableFile.length()).thenReturn(ENTITY_SIZE);
        RepoPath repoPath = new RepoPathImpl("repoKey", "path");
        when(mutableFile.getRepoPath()).thenReturn(repoPath);
        trafficService = mock(TrafficService.class);
        when(context.beanForType(TrafficService.class)).thenReturn(trafficService);
        trafficEntryV2Captor = ArgumentCaptor.forClass(TrafficEntry.class);
    }

    @Test
    public void happyFlow() {
        handler.fillBinaryDataFromStream(stream, mutableFile);
        verify(trafficService, times(1)).handleTrafficEntry(trafficEntryV2Captor.capture());
        TrafficEntry entry = trafficEntryV2Captor.getValue();
        assertTrafficEntry(entry);
    }

    @Test
    public void error() {
        handler.fillBinaryDataFromStream(stream, mutableFile);
        doThrow(new RuntimeException()).when(mutableFile).fillBinaryData(any());
        verify(trafficService, times(1)).handleTrafficEntry(trafficEntryV2Captor.capture());
        TrafficEntry entry = trafficEntryV2Captor.getValue();
        assertTrafficEntry(entry);
    }

    protected void assertTrafficEntry(TrafficEntry actual) {
        assertEquals(actual.getAction(), TrafficEntry.TrafficAction.UPLOAD);
        assertEquals(actual.getActor(), TrafficEntry.TrafficActor.USER);
        assertEquals(actual.getRedirectTarget(), TrafficEntry.TrafficTarget.DIRECT);
        assertEquals(actual.getRepoPath(), "repoKey:path");
        assertEquals(actual.getRequestedSize(), ENTITY_SIZE);
        assertEquals(actual.getSize(), ENTITY_SIZE);
        assertEquals(actual.getTransferredSize(), TRANSFERRED_SIZE);
    }

}