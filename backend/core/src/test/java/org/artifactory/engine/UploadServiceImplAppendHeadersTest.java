package org.artifactory.engine;

import org.artifactory.addon.plugin.upload.UploadCtx;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Shashank Taliwal
 */
public class UploadServiceImplAppendHeadersTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private ArtifactoryResponse response;
    private AutoCloseable mocks;
    private UploadServiceImpl uploadServiceImpl;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        uploadServiceImpl = new UploadServiceImpl();
        doNothing().when(response).setHeader(anyString(), anyString());
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
    }

    // Test methods

    @ParameterizedTest
    @MethodSource("source")
    public void testAppendHeaders(UploadCtx uploadCtx, int times) {
        uploadServiceImpl.appendHeaders(uploadCtx, response);
        verify(response, times(times)).setHeader(anyString(), anyString());
    }

    private static Stream<Arguments> source() {
        UploadCtx emptyHeadersUploadCtx = new UploadCtx();
        emptyHeadersUploadCtx.setHeaders(Map.of());
        UploadCtx uploadCtx = new UploadCtx();
        uploadCtx.setHeaders(Map.of("foo", "bar"));
        UploadCtx NullHeadersUploadCtx = new UploadCtx();
        return Stream.of(
                Arguments.of(NullHeadersUploadCtx, 0),
                Arguments.of(emptyHeadersUploadCtx, 0),
                Arguments.of(uploadCtx, 1)
        );
    }
}