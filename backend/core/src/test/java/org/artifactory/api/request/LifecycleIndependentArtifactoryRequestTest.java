package org.artifactory.api.request;

import lombok.SneakyThrows;
import org.apache.commons.collections4.iterators.IteratorEnumeration;
import org.apache.commons.io.input.NullInputStream;
import org.artifactory.factory.InfoFactory;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.common.StreamSupportUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertEqualsNoOrder;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class LifecycleIndependentArtifactoryRequestTest extends ArtifactoryHomeBoundTest {
    private LifecycleIndependentArtifactoryRequest lifecycleIndependentArtifactoryRequest;
    private LifecycleIndependentArtifactoryRequest lifecycleIndependentArtifactoryRequestFromInternal;
    private RepoPath repoPath;
    private final InfoFactory factory = InfoFactoryHolder.get();
    private final Properties properties = (Properties) factory.createProperties();
    private final String[] parameterValues = new String[] {"vals"};
    private final InputStream inputStream = new NullInputStream(0);
    private final Map<String, String[]> parameters =  Map.of("key", new String[]{"val1", "val2"});

    @SneakyThrows
    @BeforeClass
    public void setUp() {
        ArtifactoryRequest artifactoryRequestMock = mock(ArtifactoryRequest.class);
        ArtifactoryRequest internalArtifactoryRequestMock = mock(InternalArtifactoryRequest.class);
        setupMock(artifactoryRequestMock);
        setupMock(internalArtifactoryRequestMock);

        lifecycleIndependentArtifactoryRequest = new LifecycleIndependentArtifactoryRequest(artifactoryRequestMock);
        lifecycleIndependentArtifactoryRequestFromInternal =
                new LifecycleIndependentArtifactoryRequest(internalArtifactoryRequestMock);
    }

    private void setupMock(ArtifactoryRequest artifactoryRequestMock) throws IOException {
        when(artifactoryRequestMock.getRepoKey()).thenReturn("repoKey", "");
        when(artifactoryRequestMock.getPath()).thenReturn("path", "");
        when(artifactoryRequestMock.isMetadata()).thenReturn(true, false);
        when(artifactoryRequestMock.getName()).thenReturn("name", "");
        when(artifactoryRequestMock.isDirectoryRequest()).thenReturn(true, false);
        when(artifactoryRequestMock.shouldSkipPackageUsageTrack()).thenReturn(true, false);
        when(artifactoryRequestMock.isChecksum()).thenReturn(false, true);
        when(artifactoryRequestMock.hasIfModifiedSince()).thenReturn(true, false);
        when(artifactoryRequestMock.getParameters()).thenReturn(parameters).thenReturn(Map.of());
        when(artifactoryRequestMock.isZipResourceRequest()).thenReturn(true, false);
        when(artifactoryRequestMock.hasIfNoneMatch()).thenReturn(false, true);
        when(artifactoryRequestMock.getClientAddress()).thenReturn("clientAddress", "");
        when(artifactoryRequestMock.isRecursive()).thenReturn(true, false);
        when(artifactoryRequestMock.getModificationTime()).thenReturn(2013944L, 0L);
        when(artifactoryRequestMock.isFromAnotherArtifactory()).thenReturn(false, true);
        when(artifactoryRequestMock.isHeadOnly()).thenReturn(true, false);
        when(artifactoryRequestMock.getLastModified()).thenReturn(563752346L, 0L);
        when(artifactoryRequestMock.getIfModifiedSince()).thenReturn(22076L, 0L);
        when(artifactoryRequestMock.getHeadersKeys())
                .thenReturn(new IteratorEnumeration(Stream.of("header").iterator()),
                        new IteratorEnumeration(Stream.empty().iterator()));
        when(artifactoryRequestMock.getHeaderValues("header"))
                .thenReturn(new IteratorEnumeration(Stream.of("bob","bob","bob3").iterator()),
                        new IteratorEnumeration(Stream.empty().iterator()));
        when(artifactoryRequestMock.getServletContextUrl()).thenReturn("url", "");
        when(artifactoryRequestMock.getUri()).thenReturn("uri", "");
        when(artifactoryRequestMock.getProperties()).thenReturn(properties).thenReturn(null);
        when(artifactoryRequestMock.hasProperties()).thenReturn(true, false);
        when(artifactoryRequestMock.getParameter("parameter")).thenReturn("moo");
        when(artifactoryRequestMock.getParameterValues("parameters")).thenReturn(parameterValues);
        when(artifactoryRequestMock.getInputStream()).thenReturn(inputStream).thenReturn(null);
        when(artifactoryRequestMock.getContentLength()).thenReturn(1337L, 0L);
        when(artifactoryRequestMock.getZipResourcePath()).thenReturn("Zip", "");
        repoPath = factory.createRepoPath("repoKey", "repoPath");
        when(artifactoryRequestMock.getRepoPath()).thenReturn(repoPath).thenReturn(null);
    }

    @Test
    public void testGetRepoKey() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getRepoKey(), "repoKey");
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getRepoKey(), "repoKey");
    }

    @Test
    public void testGetPath() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getPath(), "path");
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getPath(), "path");
    }

    @Test
    public void testIsMetadata() {
        assertTrue(lifecycleIndependentArtifactoryRequest.isMetadata());
        assertTrue(lifecycleIndependentArtifactoryRequestFromInternal.isMetadata());
    }

    @Test
    public void testIsRecursive() {
        assertTrue(lifecycleIndependentArtifactoryRequest.isRecursive());
        assertTrue(lifecycleIndependentArtifactoryRequestFromInternal.isRecursive());
    }

    @Test
    public void testGetModificationTime() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getModificationTime(), 2013944L);
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getModificationTime(), 2013944L);
    }

    @Test
    public void testGetName() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getName(), "name");
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getName(), "name");
    }

    @Test
    public void testIsDirectoryRequest() {
        assertTrue(lifecycleIndependentArtifactoryRequest.isDirectoryRequest());
        assertTrue(lifecycleIndependentArtifactoryRequestFromInternal.isDirectoryRequest());
    }

    @Test
    public void testShouldSkipPackageUsageTrack() {
        assertTrue(lifecycleIndependentArtifactoryRequest.shouldSkipPackageUsageTrack());
        assertTrue(lifecycleIndependentArtifactoryRequestFromInternal.shouldSkipPackageUsageTrack());
    }

    @Test
    public void testGetHeadersKeys() {
        assertEquals(StreamSupportUtils.enumerationToStream(lifecycleIndependentArtifactoryRequest.getHeadersKeys())
                .toList(), List.of("header"));
        assertEquals(StreamSupportUtils.enumerationToStream(lifecycleIndependentArtifactoryRequestFromInternal
                        .getHeadersKeys()).toList(), List.of("header"));
    }

    @Test
    public void testGetHeaderValues() {
        assertEqualsNoOrder(StreamSupportUtils
                        .enumerationToStream(lifecycleIndependentArtifactoryRequest.getHeaderValues("header"))
                        .toList(), List.of("bob", "bob3"));
        assertEqualsNoOrder(StreamSupportUtils
                        .enumerationToStream(lifecycleIndependentArtifactoryRequestFromInternal
                                .getHeaderValues("header")).toList(), List.of("bob", "bob3"));
    }

    @Test
    public void testGetRepoPath() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getRepoPath(), repoPath);
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getRepoPath(), repoPath);
    }

    @Test
    public void testIsChecksum() {
        assertFalse(lifecycleIndependentArtifactoryRequest.isChecksum());
        assertFalse(lifecycleIndependentArtifactoryRequestFromInternal.isChecksum());
    }

    @Test
    public void testIsFromAnotherArtifactory() {
        assertFalse(lifecycleIndependentArtifactoryRequest.isFromAnotherArtifactory());
        assertFalse(lifecycleIndependentArtifactoryRequestFromInternal.isFromAnotherArtifactory());
    }

    @Test
    public void testIsHeadOnly() {
        assertTrue(lifecycleIndependentArtifactoryRequest.isHeadOnly());
        assertTrue(lifecycleIndependentArtifactoryRequestFromInternal.isHeadOnly());
    }

    @Test
    public void testGetLastModified() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getLastModified(), 563752346L);
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getLastModified(), 563752346L);
    }

    @Test
    public void testGetIfModifiedSince() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getIfModifiedSince(), 22076L);
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getIfModifiedSince(), 22076L);
    }

    @Test
    public void testHasIfModifiedSince() {
        assertTrue(lifecycleIndependentArtifactoryRequest.hasIfModifiedSince());
        assertTrue(lifecycleIndependentArtifactoryRequestFromInternal.hasIfModifiedSince());
    }

    @Test
    public void testIsNewerThan() {
        assertTrue(lifecycleIndependentArtifactoryRequest.isNewerThan(2013844L));
        assertFalse(lifecycleIndependentArtifactoryRequest.isNewerThan(2014044L));
        assertTrue(lifecycleIndependentArtifactoryRequestFromInternal.isNewerThan(2013844L));
        assertFalse(lifecycleIndependentArtifactoryRequestFromInternal.isNewerThan(2014044L));
    }

    @Test
    public void testGetServletContextUrl() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getServletContextUrl(), "url");
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getServletContextUrl(), "url");
    }

    @Test
    public void testGetUri() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getUri(), "uri");
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getUri(), "uri");
    }

    @Test
    public void testGetProperties() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getProperties(), properties);
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getProperties(), properties);
    }

    @Test
    public void testHasProperties() {
        assertTrue(lifecycleIndependentArtifactoryRequest.hasProperties());
        assertTrue(lifecycleIndependentArtifactoryRequestFromInternal.hasProperties());
    }

    @Test
    public void testGetParameters() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getParameters(), parameters);
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getParameters(), parameters);
    }

    @Test
    public void testGetParameter() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getParameter("key"), "val1");
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getParameter("parameter"), "moo");
    }

    @Test
    public void testGetParameterValues() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getParameterValues("key"), parameters.get("key"));
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getParameterValues("parameters"),
                parameterValues);
    }

    @Test
    public void testGetInputStream() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getInputStream(), inputStream);
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getInputStream(), inputStream);
    }

    @Test
    public void testGetContentLength() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getContentLength(), 1337L);
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getContentLength(), 1337L);
    }

    @Test
    public void testGetClientAddress() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getClientAddress(), "clientAddress");
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getClientAddress(), "clientAddress");
    }

    @Test
    public void testGetZipResourcePath() {
        assertEquals(lifecycleIndependentArtifactoryRequest.getZipResourcePath(), "Zip");
        assertEquals(lifecycleIndependentArtifactoryRequestFromInternal.getZipResourcePath(), "Zip");
    }

    @Test
    public void testIsZipResourceRequest() {
        assertTrue(lifecycleIndependentArtifactoryRequest.isZipResourceRequest());
        assertTrue(lifecycleIndependentArtifactoryRequestFromInternal.isZipResourceRequest());
    }

    @Test
    public void testIsNoneMatch() {
        assertTrue(lifecycleIndependentArtifactoryRequest.isNoneMatch("etag"));
        assertTrue(lifecycleIndependentArtifactoryRequestFromInternal.isNoneMatch("etag"));
    }

    @Test
    public void testHasIfNoneMatch() {
        assertFalse(lifecycleIndependentArtifactoryRequest.hasIfNoneMatch());
        assertFalse(lifecycleIndependentArtifactoryRequestFromInternal.hasIfNoneMatch());
    }
}
