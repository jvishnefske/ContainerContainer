/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo.remote.browse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.repo.http.GuaveCacheConnectionManagersHolder;
import org.artifactory.repo.http.IdleConnectionMonitorService;
import org.artifactory.repo.http.IdleConnectionMonitorServiceImpl;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.test.mock.SimpleMockServer;
import org.artifactory.util.HttpClientConfigurator;
import org.artifactory.util.StringInputStream;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link S3RepositoryBrowser}. Some tests are disabled because they require internet connection. They
 * should be executed by developers for debugging only.
 *
 * @author Yossi Shaul
 */
@Test
public class S3RepositoryBrowserTest extends ArtifactoryHomeBoundTest {
    private static final Logger log = LoggerFactory.getLogger(S3RepositoryBrowserTest.class);

    static {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.getLogger(S3RepositoryBrowserTest.class).setLevel(Level.INFO);
    }

    private SimpleMockServer server;
    private CloseableHttpClient client;
    private S3RepositoryBrowser s3Browser;

    public void detectRootLocal() throws IOException {
        String rootUrl = s3Browser.detectRootUrl(server.getBaseUrl() + testResourcesPath + "s3-nosuchkey.xml");
        assertEquals(rootUrl, server.getBaseUrl(), "Wrong root url detected");
    }

    @BeforeMethod
    public void beforeTestMethod() {
        homeStub.setProperty(ConstantValues.artifactoryProductVersion,
                ArtifactoryServiceVersion.getCurrent().getVersion());
        homeStub.setProperty(ConstantValues.idleConnectionMonitorInterval, "10");
        homeStub.setProperty(ConstantValues.disableIdleConnectionMonitoring, "false");
        IdleConnectionMonitorService idleConnectionMonitorService =
                new IdleConnectionMonitorServiceImpl(new GuaveCacheConnectionManagersHolder());

        ArtifactoryContext contextMock = mock(InternalArtifactoryContext.class);
        when(contextMock.beanForType(IdleConnectionMonitorService.class))
                .thenReturn(idleConnectionMonitorService);
        when(contextMock.beanForType(DenyListCacheHolderService.class))
                .thenReturn(mock(DenyListCacheHolderService.class));
        ArtifactoryContextThreadBinder.bind(contextMock);

        server = new SimpleMockServer(new S3Handler());
        server.start();
        homeStub.setProperty(ConstantValues.artifactoryServiceVersion, "TEST");

        client = new HttpClientConfigurator().connectionTimeout(30000).socketTimeout(2000).noRetry().build();

        HttpExecutor httpExecutor = method -> {
            log.info("Executing " + method.getURI() + " on dummy S3 repo!");
            return client.execute(method);
        };
        s3Browser = new S3RepositoryBrowser(httpExecutor);
    }

    @AfterMethod
    public void shutdown() throws IOException {
        if (client != null) {
            client.close();
        }
        server.stop();
        ArtifactoryContextThreadBinder.unbind();
    }

    public void listContentThrowIOException() throws IOException {
        HttpExecutor client = mock(HttpExecutor.class);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        HttpEntity entity = mock(HttpEntity.class);
        StatusLine statusLine = mock(StatusLine.class);

        when(client.executeMethod(any())).thenReturn(response);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream("<abc>value</abc>".getBytes()));
        when(response.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(SC_NOT_FOUND);

        S3RepositoryBrowser browser = new S3RepositoryBrowser(client);
        String url = "https://maven.fabric.io/public/";

        assertThrows(IOException.class, () -> browser.listContent(url));
    }

    public void listRemoteItems() throws IOException {
        ReflectionTestUtils.setField(s3Browser, "rootUrl", server.getBaseUrl());
        List<RemoteItem> result = s3Browser.listContent(
                server.getBaseUrl() + testResourcesPath + "s3-prefix-org.springframework.xml");
        assertEquals(result.size(), 35, "Unexpected content count");

        RemoteItem directoryItem = result.get(0);
        assertEquals(directoryItem.getUrl(), server.getBaseUrl() + "maven/bundles/release/org/springframework/batch/");
        assertTrue(directoryItem.isDirectory());
        assertEquals(directoryItem.getSize(), 0, "No size for directories");
        assertEquals(directoryItem.getLastModified(), 0);

        // files after folders
        assertEquals(result.get(33).getUrl(),
                server.getBaseUrl() + "maven/bundles/release/org/springframework/roo_$folder$");
        assertFalse(result.get(33).isDirectory());
        RemoteItem fileItem = result.get(34);
        assertEquals(fileItem.getUrl(), server.getBaseUrl() + "maven/bundles/release/org/springframework/my.txt");
        assertFalse(fileItem.isDirectory());
        assertEquals(fileItem.getSize(), 23);
        assertEquals(fileItem.getLastModified(), 1305442594000L);
    }

    public void listRemoteItemsFolderAndFilesWithSameName() throws IOException {
        // some s3 repositories (e.g., terracotta http://repo.terracotta.org/?delimiter=/&prefix=maven2/) has files and
        // folders with the same name (for instance file named 'org' and directory named 'org/' under the same
        //directory)
        // in such a case we prefer the directory and don't return the file
        ReflectionTestUtils.setField(s3Browser, "rootUrl", server.getBaseUrl());
        List<RemoteItem> result = s3Browser.listContent(server.getBaseUrl() + testResourcesPath + "s3-terracotta.xml");
        assertEquals(result.size(), 6, "Unexpected content count");
    }

    @Test
    public void isS3RepositoryAWS() {
        boolean s3Repository = S3RepositoryBrowser.isS3Repository(
                "https://lang-python.s3.amazonaws.com/", client, "repo", "user", true);
        assertTrue(s3Repository, "Expected true for S3 repository");
    }

    @Test
    public void isS3RepositoryGS() {
        boolean s3Repository = S3RepositoryBrowser.isS3Repository(
                "https://storage.googleapis.com/golang/ ", client, "repo", "user", true);
        assertTrue(s3Repository, "Expected true for S3 repository");
    }

    @Test
    public void isS3RepositoryTerracotta() {
        boolean s3Repository =
                S3RepositoryBrowser.isS3Repository("http://repo.terracotta.org/", client, "repo", "user", true);
        assertFalse(s3Repository, "Expected false for S3 repository - content-type = text/html");
    }

    @Test
    public void isS3RepositoryNonS3() {
        boolean s3Repository =
                S3RepositoryBrowser.isS3Repository("http://repo.jfrog.org/artifactory/repo/org", client, "repo",
                        "user", true);
        assertFalse(s3Repository, "Expected false for S3 repository");
    }

    @Test(enabled = false)
    public void isS3RepositorySpring() {
        boolean s3Repository = S3RepositoryBrowser.isS3Repository("`", client, "repo", "user", true);
        assertTrue(s3Repository, "Expected true for S3 repository");
    }

    @Test(enabled = false)
    public void detectRootUsingRoot() throws IOException {
        String rootUrl = s3Browser.detectRootUrl("http://repository.springsource.com/");
        assertEquals(rootUrl, "http://repository.springsource.com/");
    }

    @Test(enabled = false)
    public void detectRootUsingRootNoSlash() throws IOException {
        String rootUrl = s3Browser.detectRootUrl("http://repository.springsource.com");
        assertEquals(rootUrl, "http://repository.springsource.com/");
    }

    @Test(enabled = false)
    public void detectRootTerracottaUsingRoot() throws IOException {
        String rootUrl = s3Browser.detectRootUrl("http://repo.terracotta.org/");
        assertEquals(rootUrl, "http://repo.terracotta.org/");
    }

    @Test(enabled = false)
    public void detectRootTerracottaNoSlash() throws IOException {
        String rootUrl = s3Browser.detectRootUrl("http://repo.terracotta.org/maven2");
        assertEquals(rootUrl, "http://repo.terracotta.org/");
    }

    @Test(enabled = false)
    public void detectRootTerracottaExistingSubFolder() throws IOException {
        String rootUrl = s3Browser.detectRootUrl("http://repo.terracotta.org/maven2/org/");
        assertEquals(rootUrl, "http://repo.terracotta.org/");
    }

    @Test(enabled = false)
    public void detectRootTerracottaExistingSubFolderNoSlash() throws IOException {
        String rootUrl = s3Browser.detectRootUrl("http://repo.terracotta.org/maven2/org");
        assertEquals(rootUrl, "http://repo.terracotta.org/");
    }

    @Test(enabled = false)
    public void detectRootTerracottaNonExistingSubFolder() throws IOException {
        String rootUrl = s3Browser.detectRootUrl("http://repo.terracotta.org/maven2/blabla/");
        assertEquals(rootUrl, "http://repo.terracotta.org/");
    }

    @Test(enabled = false)
    public void detectRootTerracottaNonExistingSubFolderNoSlash() throws IOException {
        String rootUrl = s3Browser.detectRootUrl("http://repo.terracotta.org/maven2/blabla");
        assertEquals(rootUrl, "http://repo.terracotta.org/");
    }

    @Test(enabled = false)
    public void detectRoot() throws IOException {
        String rootUrl = s3Browser.detectRootUrl("http://repo.terracotta.org/org/test");
        assertEquals(rootUrl, "http://repo.terracotta.org/");
    }

    @Test
    public void detectRootUrlGcsNoKeyWithHttpRepo() throws IOException {
        String gcsBrowseUrl = "https://maven-central.storage.googleapis.com/com/amazonaws/aws-java-sdk-bom/";
        String expectedRoot = "https://maven-central.storage.googleapis.com/";

        HttpRepo mockHttpRepo = mock(HttpRepo.class);
        when(mockHttpRepo.getUrl()).thenReturn(expectedRoot);

        S3RepositoryBrowser browser = new S3RepositoryBrowser(gcsNoKeyExecutor(), mockHttpRepo);
        String detectedRoot = browser.detectRootUrl(gcsBrowseUrl);

        assertEquals(detectedRoot, expectedRoot,
                "rootUrl must equal httpRepo.getUrl() when GCS omits the Key element");
    }

    @Test
    public void detectRootUrlGcsNoKeyWithoutHttpRepo() throws IOException {
        String gcsBrowseUrl = "https://maven-central.storage.googleapis.com/com/amazonaws/aws-java-sdk-bom/";
        String expectedRoot = "https://maven-central.storage.googleapis.com/";

        S3RepositoryBrowser browser = new S3RepositoryBrowser(gcsNoKeyExecutor());
        String detectedRoot = browser.detectRootUrl(gcsBrowseUrl);

        assertEquals(detectedRoot, expectedRoot,
                "rootUrl must be scheme+authority when httpRepo is absent and GCS omits the Key element");
    }

    @Test
    public void detectRootUrlGcsNoKeyHttpRepoUrlWithoutTrailingSlash() throws IOException {
        String gcsBrowseUrl = "https://maven-central.storage.googleapis.com/com/amazonaws/";
        String httpRepoUrl = "https://maven-central.storage.googleapis.com";
        String expectedRoot = "https://maven-central.storage.googleapis.com/";

        HttpRepo mockHttpRepo = mock(HttpRepo.class);
        when(mockHttpRepo.getUrl()).thenReturn(httpRepoUrl);

        S3RepositoryBrowser browser = new S3RepositoryBrowser(gcsNoKeyExecutor(), mockHttpRepo);

        assertEquals(browser.detectRootUrl(gcsBrowseUrl), expectedRoot,
                "rootUrl must have trailing slash appended when httpRepo.getUrl() lacks one");
    }

    /** No httpRepo, URI parses but has no scheme/authority — must warn and fall back to copyUrl. */
    @Test
    public void resolveGcsFallbackRootUrlMissingSchemeFallsBackToCopyUrl() {
        S3RepositoryBrowser browser = new S3RepositoryBrowser(method -> {
            throw new AssertionError("HTTP should not be called");
        });
        String relativeUrl = "/just/a/relative/path";
        String result = (String) ReflectionTestUtils.invokeMethod(browser, "resolveGcsFallbackRootUrl", relativeUrl);

        assertEquals(result, relativeUrl, "must return copyUrl as-is when URI has no scheme or authority");
    }

    /** No httpRepo and URI.create throws — must warn and fall back to copyUrl. */
    @Test
    public void resolveGcsFallbackRootUrlInvalidUriFallsBackToCopyUrl() {
        S3RepositoryBrowser browser = new S3RepositoryBrowser(method -> {
            throw new AssertionError("HTTP should not be called");
        });
        String invalidUrl = "http://[not-valid";
        String result = (String) ReflectionTestUtils.invokeMethod(browser, "resolveGcsFallbackRootUrl", invalidUrl);

        assertEquals(result, invalidUrl, "must return copyUrl as-is when URI.create throws");
    }

    @Test
    public void detectRootUrlInvalidArgumentNotPro() throws IOException {
        S3RepositoryBrowser browser = new S3RepositoryBrowser(
                s3ErrorExecutor("<Error><Code>InvalidArgument</Code>" +
                        "<Message>Unsupported Authorization Type</Message></Error>")) {
            @Override
            protected boolean isPro() { return false; }
        };

        assertNull(browser.detectRootUrl("https://s3.amazonaws.com/my-bucket/"),
                "rootUrl must be null when not Pro");
    }

    @Test
    public void detectRootUrlInvalidArgumentProUnknownMessage() throws IOException {
        S3RepositoryBrowser browser = new S3RepositoryBrowser(
                s3ErrorExecutor("<Error><Code>InvalidArgument</Code>" +
                        "<Message>Some unrecognised message</Message></Error>")) {
            @Override
            protected boolean isPro() { return true; }
        };

        assertNull(browser.detectRootUrl("https://s3.amazonaws.com/my-bucket/"),
                "rootUrl must be null when message does not match UNSUPPORTED_AUTHORIZATION_TYPE");
    }

    @Test(expectedExceptions = IOException.class)
    public void detectRootUrlUnknownErrorCodeThrowsException() throws IOException {
        S3RepositoryBrowser browser = new S3RepositoryBrowser(
                s3ErrorExecutor("<Error><Code>SomeUnknownCode</Code></Error>"));
        browser.detectRootUrl("https://s3.amazonaws.com/my-bucket/");
    }

    private static HttpExecutor gcsNoKeyExecutor() throws IOException {
        return s3ErrorExecutor("<Error><Code>NoSuchKey</Code><Message>No key</Message></Error>");
    }

    private static HttpExecutor s3ErrorExecutor(String errorXml) throws IOException {
        HttpExecutor mockExecutor = mock(HttpExecutor.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        HttpEntity mockEntity = mock(HttpEntity.class);
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockExecutor.executeMethod(any())).thenReturn(mockResponse);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(errorXml.getBytes()));
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(SC_NOT_FOUND);
        return mockExecutor;
    }

    @Test
    public void buildS3RequestUrlCrossSubtreeNeverProducesAbsolutePrefix() {
        String bucketRoot = "https://maven-central.storage.googleapis.com/";
        String browseUrl = "https://maven-central.storage.googleapis.com/org/junit/jupiter/junit-jupiter/";

        S3RepositoryBrowser browser = new S3RepositoryBrowser(method -> {
            throw new AssertionError("HTTP should not be called in this test");
        });
        ReflectionTestUtils.setField(browser, "rootUrl", bucketRoot);

        String s3Url = (String) ReflectionTestUtils.invokeMethod(browser, "buildS3RequestUrl", browseUrl);

        assertFalse(s3Url.contains("prefix=https://"),
                "prefix must be a relative key path, not an absolute URL — got: " + s3Url);
        assertFalse(s3Url.contains("prefix=http://"),
                "prefix must be a relative key path, not an absolute URL — got: " + s3Url);
        assertTrue(s3Url.startsWith(bucketRoot),
                "S3 list request must be rooted at the bucket root — got: " + s3Url);
    }

    private static class S3Handler extends Handler.Abstract {

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            String target = request.getHttpURI().getPath();
            String path = target;
            // compose the resource path based on the prefix
            Fields.Field prefixField = Request.extractQueryParameters(request).get("prefix");
            String prefix = prefixField != null ? prefixField.getValue() : null;
            if (StringUtils.isNotBlank(prefix)) {
                path += prefix;
            }
            InputStream resource = SimpleMockServer.class.getResourceAsStream(path);
            if (resource == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                // assume the root is the base http, so use the target path as the Key
                String key = StringUtils.removeStart(target, "/");
                String responseBody = "<Error>" +
                        "<Code>NoSuchKey</Code>" +
                        "<Message>The specified key does not exist.</Message>" +
                        "<Key>" + key + "</Key>" +
                        "<RequestId>E69C77E280506925</RequestId>" +
                        "<HostId>AfNsMbcTZ0kw77y06lcsMEb6oxENUr80Xa0yR2P3kRs9fajMBHr0i1NYNRtjd8mV</HostId>" +
                        "</Error>";
                resource = new StringInputStream(responseBody);
            }
            ByteBuffer byteBuffer = ByteBuffer.wrap(resource.readAllBytes());
            response.write(true, byteBuffer, callback);
            callback.succeeded();
            return true;
        }
    }

    private static final String testResourcesPath = "org/artifactory/repo/remote/browse/s3/";

}
