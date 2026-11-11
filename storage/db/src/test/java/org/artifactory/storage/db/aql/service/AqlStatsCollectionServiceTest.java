package org.artifactory.storage.db.aql.service;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.aql.result.AqlJsonMultiStreamer;
import org.artifactory.aql.result.AqlJsonRemoteStreamer;
import org.artifactory.aql.result.AqlJsonStreamer;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Andrey Tashlitsky
 */
public class AqlStatsCollectionServiceTest extends ArtifactoryHomeBoundTest {

    private AqlStatsCollectionServiceImpl aqlStatsCollectionService;

    @BeforeMethod
    public void beforeMethod() {
        aqlStatsCollectionService = spy(new AqlStatsCollectionServiceImpl());
        aqlStatsCollectionService.clearStats();
    }

    @Test
    public void testCollectRemoteSearchStatsRemoteSearchesPerRepo() {
        AqlJsonMultiStreamer aqlRestResult = mock(AqlJsonMultiStreamer.class);
        populateRemoteSearchStatsData(4, 1, 2, aqlRestResult);
        aqlStatsCollectionService.collectRemoteSearchStats(aqlRestResult);
        Map<String, AtomicLong> remoteSearchesPerRepo = aqlStatsCollectionService.getRemoteSearchesPerRepo().asMap();
        assertEquals(remoteSearchesPerRepo.size(), 3);
        assertEquals(remoteSearchesPerRepo.get("generic-remote-1").get(), 4);
        assertEquals(remoteSearchesPerRepo.get("generic-remote-2").get(), 1);
        assertEquals(remoteSearchesPerRepo.get("generic-remote-3").get(), 2);
        populateRemoteSearchStatsData(2, 3, 4, aqlRestResult);
        aqlStatsCollectionService.collectRemoteSearchStats(aqlRestResult);
        remoteSearchesPerRepo = aqlStatsCollectionService.getRemoteSearchesPerRepo().asMap();
        assertEquals(remoteSearchesPerRepo.size(), 3);
        assertEquals(remoteSearchesPerRepo.get("generic-remote-1").get(), 6);
        assertEquals(remoteSearchesPerRepo.get("generic-remote-2").get(), 4);
        assertEquals(remoteSearchesPerRepo.get("generic-remote-3").get(), 6);
        populateRemoteSearchStatsData(3, 4, 1, aqlRestResult);
        aqlStatsCollectionService.collectRemoteSearchStats(aqlRestResult);
        remoteSearchesPerRepo = aqlStatsCollectionService.getRemoteSearchesPerRepo().asMap();
        assertEquals(remoteSearchesPerRepo.size(), 3);
        assertEquals(remoteSearchesPerRepo.get("generic-remote-1").get(), 9);
        assertEquals(remoteSearchesPerRepo.get("generic-remote-2").get(), 8);
        assertEquals(remoteSearchesPerRepo.get("generic-remote-3").get(), 7);
    }

    @Test
    public void testCollectRemoteSearchStatsStatusCodes() {
        AqlJsonMultiStreamer aqlRestResult = mock(AqlJsonMultiStreamer.class);
        populateStatusCodeData(aqlRestResult);
        aqlStatsCollectionService.collectRemoteSearchStats(aqlRestResult);
        Map<String, AtomicLong> remoteSearchesPerRepo = aqlStatsCollectionService.getRemoteSearchesPerRepo().asMap();
        assertEquals(remoteSearchesPerRepo.size(), 4);
        Map<String, AtomicLong> responseCodeStats = aqlStatsCollectionService.getResponseCodeStats();
        assertEquals(responseCodeStats.get("2xx").get(), 1);
        assertEquals(responseCodeStats.get("3xx").get(), 1);
        assertEquals(responseCodeStats.get("4xx").get(), 1);
        assertEquals(responseCodeStats.get("5xx").get(), 1);
        populateStatusCodeData(aqlRestResult);
        aqlStatsCollectionService.collectRemoteSearchStats(aqlRestResult);
        remoteSearchesPerRepo = aqlStatsCollectionService.getRemoteSearchesPerRepo().asMap();
        assertEquals(remoteSearchesPerRepo.size(), 4);
        responseCodeStats = aqlStatsCollectionService.getResponseCodeStats();
        assertEquals(responseCodeStats.get("2xx").get(), 2);
        assertEquals(responseCodeStats.get("3xx").get(), 2);
        assertEquals(responseCodeStats.get("4xx").get(), 2);
        assertEquals(responseCodeStats.get("5xx").get(), 2);
        populateStatusCodeData(aqlRestResult);
        aqlStatsCollectionService.collectRemoteSearchStats(aqlRestResult);
        remoteSearchesPerRepo = aqlStatsCollectionService.getRemoteSearchesPerRepo().asMap();
        assertEquals(remoteSearchesPerRepo.size(), 4);
        responseCodeStats = aqlStatsCollectionService.getResponseCodeStats();
        assertEquals(responseCodeStats.get("2xx").get(), 3);
        assertEquals(responseCodeStats.get("3xx").get(), 3);
        assertEquals(responseCodeStats.get("4xx").get(), 3);
        assertEquals(responseCodeStats.get("5xx").get(), 3);
    }

    @Test
    public void testFeatureDisabled() {
        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();
        artifactoryProperties.setProperty(ConstantValues.aqlTransitiveCollectStatsEnabled.getPropertyName(), "false");
        AqlJsonMultiStreamer aqlRestResult = mock(AqlJsonMultiStreamer.class);
        List<AqlJsonStreamer> aqlJsonStreamers = new ArrayList<>();
        AqlJsonRemoteStreamer aqlJsonRemoteStreamer = mock(AqlJsonRemoteStreamer.class);
        CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(aqlJsonRemoteStreamer.getResponse()).thenReturn(httpResponse);
        when(aqlJsonRemoteStreamer.getBaseRepoName()).thenReturn("generic-remote-1");
        aqlJsonStreamers.add(aqlJsonRemoteStreamer);
        when(aqlRestResult.getStreamers()).thenReturn(aqlJsonStreamers);
        doCallRealMethod().when(aqlStatsCollectionService).collectRemoteSearchStats(aqlRestResult);
        aqlStatsCollectionService.collectRemoteSearchStats(aqlRestResult);
        Map<String, AtomicLong> remoteSearchesPerRepo = aqlStatsCollectionService.getRemoteSearchesPerRepo().asMap();
        assertEquals(remoteSearchesPerRepo.size(), 0);
        Map<String, AtomicLong> responseCodeStats = aqlStatsCollectionService.getResponseCodeStats();
        assertEquals(responseCodeStats.size(), 0);
    }

    private void populateRemoteSearchStatsData(long numOfQueries1, long numOfQueries2, long numOfQueries3,
            AqlJsonMultiStreamer aqlRestResult) {
        Map<String, Long> dataMap =
                Map.of("generic-remote-1", numOfQueries1, "generic-remote-2", numOfQueries2, "generic-remote-3",
                        numOfQueries3);
        List<AqlJsonStreamer> aqlJsonStreamers = new ArrayList<>();
        dataMap.forEach((key, value) -> {
            for (int i = 1; i <= value; i++) {
                AqlJsonRemoteStreamer aqlJsonRemoteStreamer = mock(AqlJsonRemoteStreamer.class);
                CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
                StatusLine statusLine = mock(StatusLine.class);
                when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
                when(httpResponse.getStatusLine()).thenReturn(statusLine);
                when(aqlJsonRemoteStreamer.getResponse()).thenReturn(httpResponse);
                when(aqlJsonRemoteStreamer.getBaseRepoName()).thenReturn(key);
                aqlJsonStreamers.add(aqlJsonRemoteStreamer);
            }
        });
        when(aqlRestResult.getStreamers()).thenReturn(aqlJsonStreamers);
        doCallRealMethod().when(aqlStatsCollectionService).collectRemoteSearchStats(aqlRestResult);
    }

    private void populateStatusCodeData(AqlJsonMultiStreamer aqlRestResult) {
        List<AqlJsonStreamer> aqlJsonStreamers = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            AqlJsonRemoteStreamer aqlJsonRemoteStreamer = mock(AqlJsonRemoteStreamer.class);
            CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
            StatusLine statusLine = mock(StatusLine.class);
            switch (i) {
                case 1:
                    when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
                    break;
                case 2:
                    when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_NOT_FOUND);
                    break;
                case 3:
                    when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_MOVED_TEMPORARILY);
                    break;
                case 4:
                    when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_BAD_GATEWAY);
                    break;
            }
            when(httpResponse.getStatusLine()).thenReturn(statusLine);
            when(aqlJsonRemoteStreamer.getResponse()).thenReturn(httpResponse);
            when(aqlJsonRemoteStreamer.getBaseRepoName()).thenReturn("generic-remote-" + i);
            aqlJsonStreamers.add(aqlJsonRemoteStreamer);
        }
        when(aqlRestResult.getStreamers()).thenReturn(aqlJsonStreamers);
        doCallRealMethod().when(aqlStatsCollectionService).collectRemoteSearchStats(aqlRestResult);
    }
}
