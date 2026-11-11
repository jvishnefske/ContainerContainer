package org.artifactory.request;

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.DelegatingServletInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static org.artifactory.checksum.ChecksumInfo.TRUSTED_FILE_MARKER;
import static org.artifactory.request.ArtifactoryRequest.CHECKSUM_MD5;
import static org.artifactory.request.ArtifactoryRequest.CHECKSUM_SHA1;
import static org.artifactory.request.ArtifactoryRequest.CHECKSUM_SHA256;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BuildArtifactoryRequestImplTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String INITIAL = "initial";
    private static final String REPLACED = "replaced";
    private static final String TEST_HEADER = "TEST-HEADER";

    private HttpServletRequest servletRequest;
    private BuildArtifactoryRequestImpl request;

    @BeforeEach
    void setup() throws IOException {
        ArtifactoryContext artifactoryContext = Mockito.mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);

        servletRequest = Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("r");

        InputStream initialStream = new ByteArrayInputStream(INITIAL.getBytes());
        DelegatingServletInputStream delegatingServletInputStream = new DelegatingServletInputStream(initialStream);
        when(servletRequest.getInputStream()).thenReturn(delegatingServletInputStream);

        request = new BuildArtifactoryRequestImpl(servletRequest);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", INITIAL})
    void whenBuildManifestFileSizeLimitExceededThenBadRequestException(String str) throws IOException {
        ArtifactorySystemProperties properties = Mockito.mock(ArtifactorySystemProperties.class);
        when(properties.getLongProperty(any())).thenReturn(1L);
        ArtifactoryHome artifactoryHome = Mockito.mock(ArtifactoryHome.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(properties);

        InputStream initialStream = new ByteArrayInputStream(str.getBytes());
        DelegatingServletInputStream delegatingServletInputStream = new DelegatingServletInputStream(initialStream);
        when(servletRequest.getInputStream()).thenReturn(delegatingServletInputStream);

        try (MockedStatic<ArtifactoryHome> contextHelper = mockStatic(ArtifactoryHome.class)) {
            contextHelper.when(ArtifactoryHome::get).thenReturn(artifactoryHome);
            Executable target = () -> new BuildArtifactoryRequestImpl(servletRequest);
            if (str.length() > 1) {
                assertThrows(BadRequestException.class, target);
            } else {
                assertDoesNotThrow(target);
            }
        }
    }

    @Test
    void whenContentReplacedThenLengthUpdated() {
        assertEquals(request.getContentLength(), INITIAL.length());

        request.replaceContent(REPLACED.getBytes());
        assertEquals(request.getContentLength(), REPLACED.length());
    }

    @Test
    void whenNotChecksumHeaderThenValueFromOriginalRequestReturns() {
        when(servletRequest.getHeaders(TEST_HEADER))
                .thenReturn(Collections.enumeration(Collections.singletonList(TEST_HEADER)));
        assertEquals(TEST_HEADER, request.getHeaderValues(TEST_HEADER).nextElement());
    }

    @Test
    void whenChecksumHeaderThenTrustedFileMarkerReturns() {
        assertEquals(TRUSTED_FILE_MARKER, request.getHeaderValues(CHECKSUM_SHA1).nextElement());
        assertEquals(TRUSTED_FILE_MARKER, request.getHeaderValues(CHECKSUM_MD5).nextElement());
        assertEquals(TRUSTED_FILE_MARKER, request.getHeaderValues(CHECKSUM_SHA256).nextElement());
    }
}
