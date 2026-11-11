package org.artifactory.engine;

import jakarta.annotation.Nonnull;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.api.request.ArtifactoryRequestBase;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.RepoResource;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class IsNoneMatchTest extends ArtifactoryHomeBoundJUnitTest {
    @ParameterizedTest
    @MethodSource("data")
    public void testingEtagMatchWhenConstantValueIsSet(String etag, String sha1, String md5, boolean includeMd5,
            boolean expected) {
        setStringSystemProperty(ConstantValues.includeMd5IntoETagCheck, Boolean.toString(includeMd5));
        DownloadServiceImpl downloadService = new DownloadServiceImpl();
        RequestStub request = new RequestStub();
        request.setHeader("If-None-Match", etag);
        RepoResource resource = Mockito.mock(RepoResource.class);
        RepoResourceInfo repoResourceInfo = Mockito.mock(RepoResourceInfo.class);
        when(repoResourceInfo.getSha1()).thenReturn(sha1);
        when(repoResourceInfo.getMd5()).thenReturn(md5);
        when(resource.getInfo()).thenReturn(repoResourceInfo);
        assertEquals(downloadService.isNonMatch(request, resource), expected);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testingEtagMatchWhenHeaderIsPassed(String etag, String sha1, String md5, boolean includeMd5header,
            boolean expected) {
        RequestStub request = new RequestStub();
        DownloadServiceImpl downloadService = new DownloadServiceImpl();
        request.setHeader("If-None-Match", etag);
        request.setHeader(ArtifactoryHeaders.IF_NONE_MATCH_MD5, String.valueOf(includeMd5header));
        RepoResource resource = Mockito.mock(RepoResource.class);
        RepoResourceInfo repoResourceInfo = Mockito.mock(RepoResourceInfo.class);
        when(repoResourceInfo.getSha1()).thenReturn(sha1);
        when(repoResourceInfo.getMd5()).thenReturn(md5);
        when(resource.getInfo()).thenReturn(repoResourceInfo);
        assertEquals(downloadService.isNonMatch(request, resource), expected);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testingEtagMatchWhenBothHeaderAndConstantValuesMatch(String etag, String sha1, String md5,
            boolean includeMd5,
            boolean expected) {
        RequestStub request = new RequestStub();
        DownloadServiceImpl downloadService = new DownloadServiceImpl();
        setStringSystemProperty(ConstantValues.includeMd5IntoETagCheck, Boolean.toString(includeMd5));
        request.setHeader("If-None-Match", etag);
        request.setHeader(ArtifactoryHeaders.IF_NONE_MATCH_MD5, String.valueOf(includeMd5));
        RepoResource resource = Mockito.mock(RepoResource.class);
        RepoResourceInfo repoResourceInfo = Mockito.mock(RepoResourceInfo.class);
        when(repoResourceInfo.getSha1()).thenReturn(sha1);
        when(repoResourceInfo.getMd5()).thenReturn(md5);
        when(resource.getInfo()).thenReturn(repoResourceInfo);
        assertEquals(downloadService.isNonMatch(request, resource), expected);
    }

    @ParameterizedTest
    @MethodSource("oppositeValues")
    public void testingEtagMatchWhenHeaderAndConstantValuesDontMatch(String etag, String sha1, String md5,
            boolean includeMd5,
            boolean expected) {
        RequestStub request = new RequestStub();
        DownloadServiceImpl downloadService = new DownloadServiceImpl();
        setStringSystemProperty(ConstantValues.includeMd5IntoETagCheck, Boolean.toString(!includeMd5));
        request.setHeader("If-None-Match", etag);
        request.setHeader(ArtifactoryHeaders.IF_NONE_MATCH_MD5, String.valueOf(includeMd5));
        RepoResource resource = Mockito.mock(RepoResource.class);
        RepoResourceInfo repoResourceInfo = Mockito.mock(RepoResourceInfo.class);
        when(repoResourceInfo.getSha1()).thenReturn(sha1);
        when(repoResourceInfo.getMd5()).thenReturn(md5);
        when(resource.getInfo()).thenReturn(repoResourceInfo);
        assertEquals(downloadService.isNonMatch(request, resource), expected);
    }


    public static Object[][] data() {
        return new Object[][] {
                // legacy cases
                {"sha-1", "sha-1", "md5", false, false},
                {"sha-1", "sha-2", "md5", false, true},
                {"md5", "sha-1", "md5", false, true},
                {"md6", "sha-1", "md5", false, true},
                // include md5 enabled
                {"sha-1", "sha-1", "md5", true, false},
                {"md5", "sha-1", "md5", true, false},
                {"\"md5\"", "sha-1", "md5", true, false},
                {"W/\"md5\"", "sha-1", "md5", true, false},
                {"W/md5", "sha-1", "md5", true, false},
                {"sha-1", "sha-2", "md5", true, true},
                {"md5", "sha-1", "md6", true, true},
        };
    }

    public static Object[][] oppositeValues() {
        return new Object[][] {
                // legacy cases
                {"sha-1", "sha-1", "md5", false, false},
                {"sha-1", "sha-2", "md5", false, true},
                {"md5", "sha-1", "md5", false, false},
                {"md6", "sha-1", "md5", false, true},
                // include md5 enabled
                {"sha-1", "sha-1", "md5", true, false},
                {"md5", "sha-1", "md5", true, false},
                {"\"md5\"", "sha-1", "md5", true, false},
                {"W/\"md5\"", "sha-1", "md5", true, false},
                {"W/md5", "sha-1", "md5", true, false},
                {"sha-1", "sha-2", "md5", true, true},
                {"md5", "sha-1", "md6", true, true},
                // Reversing value
                {"sha-1", "sha-1", "md5", true, false},
                {"sha-1", "sha-2", "md5", true, true},
                {"md5", "sha-1", "md5", true, false},
                {"md6", "sha-1", "md5", true, true},
                // include md5 enabled
                {"sha-1", "sha-1", "md5", false, false},
                {"md5", "sha-1", "md5", false, false},
                {"\"md5\"", "sha-1", "md5", false, false},
                {"W/\"md5\"", "sha-1", "md5", false, false},
                {"W/md5", "sha-1", "md5", false, false},
                {"sha-1", "sha-2", "md5", false, true},
                {"md5", "sha-1", "md6", false, true},
        };
    }


    private static class RequestStub extends ArtifactoryRequestBase {

        Map<String, List<String>> headers = new HashMap<>();

        @Override
        public boolean isRecursive() {
            return false;
        }

        @Override
        public boolean isFromAnotherArtifactory() {
            return false;
        }

        @Override
        public boolean isHeadOnly() {
            return false;
        }

        @Override
        public long getLastModified() {
            return 0;
        }

        @Override
        public long getIfModifiedSince() {
            return 0;
        }

        @Override
        public boolean hasIfModifiedSince() {
            return false;
        }

        @Override
        public String getServletContextUrl() {
            return null;
        }

        @Override
        public String getUri() {
            return null;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return null;
        }

        @Override
        public long getContentLength() {
            return 0;
        }

        @Override
        public String getClientAddress() {
            return null;
        }

        @Override
        public Enumeration<String> getHeadersKeys() {
            return null;
        }

        @Override
        public Enumeration<String> getHeaderValues(@Nonnull String headerName) {
            return Collections.enumeration(headers.getOrDefault(headerName, emptyList()));
        }

        public void setHeader(String headerName, String headerValue) {
            headers.put(headerName, List.of(headerValue));
        }
    }
}
