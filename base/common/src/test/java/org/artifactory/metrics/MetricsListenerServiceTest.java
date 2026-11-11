package org.artifactory.metrics;

import org.apache.http.HttpHeaders;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.metrics.callhome.CallHomeMetrics;
import org.artifactory.util.HttpUtils;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;

@Test
public class MetricsListenerServiceTest {

    @Mock
    RepositoryService repositoryService;

    @Mock
    CallHomeMetrics callHomeMetrics;

    MetricsListenerService metricsListenerService;

    final String TEST_USER_AGENT = "ARTIFACTORY-TEST";
    final String TEST_ACTION = "TEST-ACTION";

    @BeforeClass
    public void setup() {

        List<MetricsInfo> repoInfoList = new ArrayList<>();
        repoInfoList.add(new MetricsInfo("remote", "docker", TEST_ACTION));
        MetricsMetadata mm = new MetricsMetadata();
        mm.setMetricsInfoList(repoInfoList);
        MetricsThreadLocal.bind(mm);

        metricsListenerService = new MetricsListenerService(repositoryService, callHomeMetrics);
    }

    @Test
    public void testTransformActionNameMethod() {
        String originalUploadAction = "UPLOAD";
        String originalDownloadAction = "DOWNLOAD";
        String transformedUploadAction = "UPLOAD_NO_CONTENT";
        String transformedDownloadAction = "DOWNLOAD_NO_CONTENT";

        String noChangeAction = metricsListenerService.transformActionName(originalUploadAction, 100L, 100L);
        assertEquals(originalUploadAction, noChangeAction);

        String noUploadAction = metricsListenerService.transformActionName(originalUploadAction, -1L, 100L);
        assertEquals(transformedUploadAction, noUploadAction);

        String noDownloadAction = metricsListenerService.transformActionName(originalDownloadAction, 0L, -1L);
        assertEquals(transformedDownloadAction, noDownloadAction);
    }

    @Test
    public void testNoContentLengthHeaderUpload() {
        long responseContentLength = 0L;

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader(HttpHeaders.USER_AGENT, TEST_USER_AGENT);

        MetricsListenerService metricsListenerServiceSpy = Mockito.spy(metricsListenerService);
        metricsListenerServiceSpy.send(200, responseContentLength, mockRequest);
        Mockito.verify(metricsListenerServiceSpy, Mockito.times(1))
                .transformActionName(TEST_ACTION, -1L, responseContentLength);
    }

    @Test
    public void testValidContentLengthHeaderUpload() {
        long requestContentLength = 100L;
        long responseContentLength = 0L;

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader(HttpHeaders.USER_AGENT, TEST_USER_AGENT);
        mockRequest.addHeader(HttpHeaders.CONTENT_LENGTH, requestContentLength);

        MetricsListenerService metricsListenerServiceSpy = Mockito.spy(metricsListenerService);
        metricsListenerServiceSpy.send(200, responseContentLength, mockRequest);
        Mockito.verify(metricsListenerServiceSpy, Mockito.times(1))
                .transformActionName(TEST_ACTION, requestContentLength, responseContentLength);
    }

    @Test
    public void testValidContentLengthHeaderDownload() {
        long requestContentLength = 0L;
        long responseContentLength = 100L;

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader(HttpHeaders.USER_AGENT, TEST_USER_AGENT);
        mockRequest.addHeader(HttpHeaders.CONTENT_LENGTH, requestContentLength);

        MetricsListenerService metricsListenerServiceSpy = Mockito.spy(metricsListenerService);
        metricsListenerServiceSpy.send(200, responseContentLength, mockRequest);
        Mockito.verify(metricsListenerServiceSpy, Mockito.times(1))
                .transformActionName(TEST_ACTION, requestContentLength, responseContentLength);
    }

    @Test
    public void testValidReverseProxy() {
        long requestContentLength = 0L;
        long responseContentLength = 100L;

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader(HttpHeaders.USER_AGENT, TEST_USER_AGENT);
        mockRequest.addHeader(HttpHeaders.CONTENT_LENGTH, requestContentLength);
        mockRequest.addHeader(HttpUtils.X_FORWARDED_FOR, "127.0.0.1:8082");

        MetricsListenerService metricsListenerServiceSpy = Mockito.spy(metricsListenerService);
        metricsListenerServiceSpy.send(200, responseContentLength, mockRequest);
        Mockito.verify(metricsListenerServiceSpy, Mockito.times(1))
                .transformActionName(TEST_ACTION, requestContentLength, responseContentLength);
    }

    @AfterClass
    public void tearDown() {
        MetricsThreadLocal.unbind();
    }
}
