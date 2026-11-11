package org.artifactory.rest.resource.mpu;

import org.apache.http.HttpHeaders;
import org.artifactory.mpu.MultipartStatusResult;
import org.artifactory.mpu.MultipartUploadService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.mpu.entity.MultipartUploadData;
import org.jfrog.storage.mpu.entity.MultipartUploadStatus;
import org.jfrog.storage.mpu.entity.MultipartUploadTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static jakarta.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class MultipartUploadResourceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private MultipartUploadService mpuService;
    @InjectMocks
    private MultipartUploadResource mpuResource;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testCreate() {
        when(mpuService.generateToken("cool-repo", "cool-path", 1)).thenReturn("cool-token");
        try (Response response = mpuResource.create("cool-repo", "cool-path", 1)) {
            assertEquals(SC_OK, response.getStatus());
            CreateResponse createResponse = (CreateResponse) response.getEntity();
            assertEquals("cool-token", createResponse.token());
            verify(mpuService).generateToken("cool-repo", "cool-path", 1);
        }
    }

    @ParameterizedTest
    @MethodSource("create")
    void testCreateNulls(String repoKey, String repoPath) {
        assertThrows(BadRequestException.class, () -> mpuResource.create(repoKey, repoPath, 1));
        verify(mpuService, times(0)).generateToken(anyString(), anyString(), anyInt());
    }

    public static Stream<Arguments> create() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(null, ""),
                Arguments.of("", null)
        );
    }

    @Test
    void testUrlPart() {
        when(mpuService.urlPart(0)).thenReturn("cool-url");
        try (Response response = mpuResource.urlPart(0)) {
            assertEquals(SC_OK, response.getStatus());
            UrlResponse urlResponse = (UrlResponse) response.getEntity();
            assertEquals("cool-url", urlResponse.url());
            verify(mpuService).urlPart(0);
        }
    }

    @Test
    void testUrlPartNull() {
        assertThrows(BadRequestException.class, () -> mpuResource.urlPart(null));
        verify(mpuService, times(0)).urlPart(anyInt());
    }

    @Test
    void testComplete() throws Exception {
        mpuResource.complete(null, "cool-sha1");
        verify(mpuService).complete(null, "cool-sha1");
    }

    @Test
    void testCompleteNulls() throws Exception {
        assertThrows(BadRequestException.class, () -> mpuResource.complete(null, null));
        verify(mpuService, times(0)).complete(any(), anyString());
    }

    @Test
    void testAbort() {
        try (Response response = mpuResource.abort()) {
            assertEquals(SC_NO_CONTENT, response.getStatus());
        }
    }

    @Test
    void testStatus() {
        MultipartUploadData data = mock(MultipartUploadData.class);
        when(data.getProgress()).thenReturn(0);
        when(mpuService.status()).thenReturn(
                new MultipartStatusResult(new MultipartUploadTask(MultipartUploadStatus.Parts, data), "", null));
        mpuResource.status();
        verify(mpuService).status();
    }

    @Test
    void testStatusWithError() {
        MultipartUploadData data = mock(MultipartUploadData.class);
        when(data.getProgress()).thenReturn(0);
        when(mpuService.status()).thenReturn(
                new MultipartStatusResult(new MultipartUploadTask(MultipartUploadStatus.Parts, data), "Error", null));
        mpuResource.status();
        verify(mpuService).status();
    }

    @ParameterizedTest
    @MethodSource("userAgentProvider")
    void testIsSupportedByCliVersion(String userAgent, boolean expected) {
        when(mpuService.isFeatureEnabled()).thenReturn(true);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getHeaders(HttpHeaders.USER_AGENT)).thenReturn(
                Collections.enumeration(List.of(userAgent)));

        mpuResource.isSupported(httpServletRequest);
        verify(mpuService, times(expected ? 1 : 0)).isFeatureEnabled();
    }

    private static Stream<Arguments> userAgentProvider() {
        return Stream.of(
                Arguments.of("jfrog-cli-go/2.62.0", false),
                Arguments.of("jfrog-cli-go/2.63.0", true),
                Arguments.of("jfrog-cli-go/2.64.0", true),
                Arguments.of("some-other-agent", true),
                Arguments.of("", true)
        );
    }
}
