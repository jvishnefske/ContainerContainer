package org.artifactory.request;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.message.BasicHeader;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.request.ArtifactoryResponseBase;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.RealRepoDescriptor;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.RepoPath;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.MutableRepoResourceInfo;
import org.artifactory.resource.RemoteRepoResource;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.ZipEntryResource;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.traffic.TrafficService;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.config.ConfigurationManagerImpl;
import org.jfrog.config.ifc.ConfigurationManager;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.web.util.UriUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import static org.apache.http.HttpHeaders.CONTENT_ENCODING;
import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Shay Bagants
 */
@Test
public class RequestResponseHelperTest {

    private static final String TEST_REPO_KEY = "repoKey";
    private static final String TEST_ENTRY_PATH = "test/abc.zip";
    private static final String TEST_SHA_ONE = "sha1";
    private static final String TEST_SHA_TWO = "sha256";
    private static final String TEST_MD_FIVE = "md5";
    private static final String TEST_ZIP_ENTRY_NAME = "testZipEntryName.sql";
    private static final Long TEST_SIZE = 1L;

    @Mock
    private TrafficService trafficService;
    @Mock
    private SecurityService securityService;
    @Spy
    private ArtifactoryResponseBase response;
    @Mock
    private ZipEntryResource res;
    @Mock
    private FileResource fileResource;
    @Mock
    private ResourceStreamHandle handle;
    @Mock
    private InternalRequestContext requestContext;
    @Mock
    private Request request;
    @Mock
    private RepoPath repoPath;
    @Mock
    private InputStream inputStream;
    @Mock
    private RepoResourceInfo repoResourceInfo;
    @Mock
    RepoResource repoResource;
    @Mock
    private MutableFileInfo mutableFileInfo;
    @Mock
    private ArtifactoryApplicationContext applicationContext;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RealRepoDescriptor repoDescriptor;

    @Captor
    private ArgumentCaptor<String> captor;

    private RequestResponseHelper requestResponseHelper;

    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryHome home = new ArtifactoryHomeStub(getClass().getName());
        ConfigurationManager configurationManager =
                ConfigurationManagerImpl.create(new ArtifactoryConfigurationAdapter(home));
        configurationManager.initDefaultFiles();
        home.initArtifactorySystemProperties();
        home.initMimeTypes();
        ArtifactoryHome.bind(home);
        ArtifactoryContextThreadBinder.bind(applicationContext);
        requestResponseHelper = new RequestResponseHelper(trafficService, securityService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testZipDownloadEntryName() throws IOException {
        when(requestContext.getRequest()).thenReturn(request);
        when(request.getHeader(HttpHeaders.RANGE)).thenReturn(HttpHeaders.RANGE);
        when(request.getHeader(HttpHeaders.IF_RANGE)).thenReturn(HttpHeaders.IF_RANGE);
        when(res.getEntryPath()).thenReturn(TEST_ENTRY_PATH);
        when(res.getResponseRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn(TEST_REPO_KEY);
        when(repoPath.getPath()).thenReturn(TEST_ENTRY_PATH);
        when(handle.getInputStream()).thenReturn(inputStream);
        when(handle.getSize()).thenReturn(TEST_SIZE);
        when(res.getInfo()).thenReturn(repoResourceInfo);
        when(repoResourceInfo.getSha1()).thenReturn(TEST_SHA_ONE);
        when(repoResourceInfo.getSha2()).thenReturn(TEST_SHA_TWO);
        when(repoResourceInfo.getMd5()).thenReturn(TEST_MD_FIVE);
        when(applicationContext.getRepositoryService()).thenReturn(repositoryService);
        when(applicationContext.getRepositoryService()).thenReturn(repositoryService);
        when(res.getZipEntryName()).thenReturn(TEST_ZIP_ENTRY_NAME);
        when(res.getRepoPath()).thenReturn(repoPath);
        requestResponseHelper.sendBodyResponse(response, res, handle, requestContext, true);
        verify(response).setFilename(captor.capture());
        assertEquals(captor.getValue(), TEST_ZIP_ENTRY_NAME);
    }

    @Test
    public void testZipDownloadNotZipEntryButFileExpectFileName() throws IOException {
        when(requestContext.getRequest()).thenReturn(request);
        when(request.getHeader(HttpHeaders.RANGE)).thenReturn(HttpHeaders.RANGE);
        when(request.getHeader(HttpHeaders.IF_RANGE)).thenReturn(HttpHeaders.IF_RANGE);
        when(fileResource.getResponseRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn(TEST_REPO_KEY);
        when(repoPath.getPath()).thenReturn(TEST_ENTRY_PATH);
        when(handle.getInputStream()).thenReturn(inputStream);
        when(handle.getSize()).thenReturn(TEST_SIZE);
        when(fileResource.getInfo()).thenReturn(mutableFileInfo);
        when(mutableFileInfo.getSha1()).thenReturn(TEST_SHA_ONE);
        when(mutableFileInfo.getSha2()).thenReturn(TEST_SHA_TWO);
        when(mutableFileInfo.getMd5()).thenReturn(TEST_MD_FIVE);
        when(applicationContext.getRepositoryService()).thenReturn(repositoryService);
        when(applicationContext.getRepositoryService()).thenReturn(repositoryService);
        when(fileResource.getRepoPath()).thenReturn(repoPath);
        when(repoDescriptor.isArchiveBrowsingEnabled()).thenReturn(true);
        when(repositoryService.repoDescriptorByKey(TEST_REPO_KEY)).thenReturn(repoDescriptor);
        when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn("Mozilla gecko");
        requestResponseHelper.sendBodyResponse(response, fileResource, handle, requestContext, true);
        verify(res, never()).getZipEntryName();
        verify(response).setFilename(captor.capture());
        assertNotEquals(captor.getValue(), TEST_ZIP_ENTRY_NAME);
        //RTFACT-23853
        String contentSecurityPolicy = ConstantValues.contentBrowsingContentSecurityPolicy.getString();
        verify(response).setHeader("Content-Security-Policy", contentSecurityPolicy);
    }


    @Test(dataProvider = "provideUserNames", description = "RTFACT-20177")
    public void testGetUserAgentIfXrayUser(String userName, String agent) {
        Request request = mock(Request.class);
        when(request.getHeader(USER_AGENT)).thenReturn(agent);
        String result = RequestResponseHelper.getUserAgentIfXrayUser(request, userName, null);
        assertEquals(result, agent);
    }

    @Test(dataProvider = "provideUserNamesForXrayToken", description = "RTFACT-20177")
    public void testGetUserAgentIfXrayUserUsingAuthenticationToken(String subject, String agent) {
        AccessTokenAuthentication accessTokenAuthentication = Mockito.mock(AccessTokenAuthentication.class);
        JwtAccessToken jwtAccessToken = Mockito.mock(JwtAccessToken.class);
        when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
        when(jwtAccessToken.getSubject()).thenReturn(subject);
        Request request = mock(Request.class);
        when(request.getHeader(USER_AGENT)).thenReturn(agent);
        String result = RequestResponseHelper.getUserAgentIfXrayUser(request, "", accessTokenAuthentication);
        assertEquals(result, agent);
    }

    @Test(dataProvider = "extractRequestClientTypeProvider")
    public void testExtractRequestClientType(String userAgent, RequestClientType expectedType) {
        // setup
        Enumeration<String> enumeration = mock(Enumeration.class);
        Iterator<String> iterator = List.of(userAgent).iterator();
        when(enumeration.asIterator()).thenReturn(iterator);

        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getHeaderValues(USER_AGENT)).thenReturn(enumeration);

        // test
        RequestClientType requestClientType = RequestResponseHelper.extractRequestClientType(request);

        // verify
        assertEquals(requestClientType, expectedType);
    }

    @Test
    public void testExtractRequestClientTypeNoUserAgent() {
        // setup
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getHeaderValues(USER_AGENT)).thenReturn(null);

        // test
        RequestClientType requestClientType = RequestResponseHelper.extractRequestClientType(request);

        // verify
        assertEquals(requestClientType, RequestClientType.UNKNOWN);
    }

    @Test(dataProvider = "clientSupportsM3SnapshotVersionsProvider")
    public void testClientSupportsM3SnapshotVersions(String userAgent, boolean expectedSupport) {
        // setup
        Enumeration<String> enumeration = mock(Enumeration.class);
        Iterator<String> iterator = List.of(userAgent).iterator();
        when(enumeration.asIterator()).thenReturn(iterator);

        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getHeaderValues(USER_AGENT)).thenReturn(enumeration);
        when(request.getHeader(USER_AGENT)).thenReturn(userAgent);

        // test
        boolean supports = RequestResponseHelper.clientSupportsM3SnapshotVersions(request);

        // verify
        assertEquals(supports, expectedSupport);
    }

    @Test
    public void testClientSupportsM3SnapshotVersionsNoUserAgent() {
        // setup
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getHeaderValues(USER_AGENT)).thenReturn(null);
        when(request.getHeader(USER_AGENT)).thenReturn(null);

        // test
        boolean supports = RequestResponseHelper.clientSupportsM3SnapshotVersions(request);

        // verify
        assertTrue(supports);
    }

    @Test
    public void testSendBodyResponseContentLengthShouldReturnHandleSize() throws IOException {
        when(requestContext.getRequest()).thenReturn(request);
        when(res.getResponseRepoPath()).thenReturn(repoPath);
        when(res.getSize()).thenReturn(1000L);
        when(handle.getSize()).thenReturn(TEST_SIZE);
        when(res.getInfo()).thenReturn(repoResourceInfo);
        when(applicationContext.getRepositoryService()).thenReturn(repositoryService);
        when(res.getRepoPath()).thenReturn(repoPath);
        requestResponseHelper.sendBodyResponse(response, res, handle, requestContext, true);
        assertEquals(response.getContentLength(), TEST_SIZE.longValue());
    }

    @Test
    public void testSendBodyResponseContentLengthShouldReturnResourceSize() throws IOException {
        long expectedSize = 1000L;
        when(requestContext.getRequest()).thenReturn(request);
        when(res.getResponseRepoPath()).thenReturn(repoPath);
        when(res.getSize()).thenReturn(expectedSize);
        when(handle.getSize()).thenReturn(0L);
        when(res.getInfo()).thenReturn(repoResourceInfo);
        when(applicationContext.getRepositoryService()).thenReturn(repositoryService);
        when(res.getRepoPath()).thenReturn(repoPath);
        when(repoResourceInfo.getName()).thenReturn("test");
        when(res.getInfo()).thenReturn(repoResourceInfo);
        requestResponseHelper.sendBodyResponse(response, res, handle, requestContext, true);
        assertEquals(response.getContentLength(), expectedSize);
    }

    @Test
    public void testSendBodyResponseShouldNotReturnResourceSize() throws IOException {
        long expectedSize = -1;
        RemoteRepoResource remoteRepoResource = mock(RemoteRepoResource.class);
        when(requestContext.getRequest()).thenReturn(request);
        when(remoteRepoResource.getResponseRepoPath()).thenReturn(repoPath);
        when(remoteRepoResource.getSize()).thenReturn(1000L);
        when(handle.getSize()).thenReturn(0L);
        MutableRepoResourceInfo mutableRepoResourceInfo = mock(MutableRepoResourceInfo.class);
        when(remoteRepoResource.getInfo()).thenReturn(mutableRepoResourceInfo);
        when(applicationContext.getRepositoryService()).thenReturn(repositoryService);
        when(remoteRepoResource.getRepoPath()).thenReturn(repoPath);
        Header[] headers = {new BasicHeader(CONTENT_ENCODING, "gzip")};
        when(remoteRepoResource.getAllHeaders()).thenReturn(headers);
        requestResponseHelper.sendBodyResponse(response, remoteRepoResource, handle, requestContext, true);
        assertEquals(response.getContentLength(), expectedSize);
    }

    @Test(dataProvider = "provideNonAsciiFileNames")
    public void testSendHeadResponseContainingNonAsciiCharactersWhenContentBrowsingDisabled(String fileName) {
        setupSendHead();
        when(repositoryService.repoDescriptorByKey("testRepoKey")).thenReturn(repoDescriptor);
        when(repoDescriptor.isArchiveBrowsingEnabled()).thenReturn(false);
        when(repoResourceInfo.getName()).thenReturn(fileName);

        requestResponseHelper.sendHeadResponse(response, repoResource);

        final String encodedFilename = UriUtils.encode(fileName, StandardCharsets.UTF_8)
                                                 .replace("+", "%20");
        verify(response).setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
    }

    @Test(dataProvider = "provideNonAsciiFileNames")
    public void testSendHeadResponseContainingNonAsciiCharactersWhenContentBrowsingEnabled(String fileName) {
        setupSendHead();
        when(repositoryService.repoDescriptorByKey("testRepoKey")).thenReturn(repoDescriptor);
        when(repoDescriptor.isArchiveBrowsingEnabled()).thenReturn(true);
        when(repoResourceInfo.getName()).thenReturn(fileName);

        requestResponseHelper.sendHeadResponse(response, repoResource);

        final String encodedFilename = UriUtils.encode(fileName, StandardCharsets.UTF_8)
                                                 .replace("+", "%20");
        verify(response).setHeader("Content-Disposition", "inline; filename*=UTF-8''" + encodedFilename);
    }

    @Test
    public void whenOriginRemotePathPresent() {
        setupSendHead();

        when(repoResource.getOriginRemotePath()).thenReturn("originRemotePath");

        requestResponseHelper.sendHeadResponse(response, repoResource);

        verify(response).setHeader("X-Artifactory-Origin-Remote-Path", "originRemotePath");
    }

    @Test
    public void whenOriginRemotePathNotPresent() {
        setupSendHead();

        when(repoResource.getOriginRemotePath()).thenReturn(null);

        requestResponseHelper.sendHeadResponse(response, repoResource);

        verify(response, times(0)).setHeader(eq("X-Artifactory-Origin-Remote-Path"), any());
    }

    private void setupSendHead() {
        when(repoPath.getRepoKey()).thenReturn("testRepoKey");
        when(repoResource.getRepoPath()).thenReturn(repoPath);
        when(repoResource.getResponseRepoPath()).thenReturn(repoPath);
        when(repoResourceInfo.getName()).thenReturn("fileName");
        when(repoResource.getInfo()).thenReturn(repoResourceInfo);
        when(applicationContext.getRepositoryService()).thenReturn(repositoryService);
    }

    @DataProvider
    public static Object[][] clientSupportsM3SnapshotVersionsProvider() {
        return new Object[][] {
                //header,  type
                {"Debian APT-HTTP/1.3 (2.0.2ubuntu0.1)", true},
                {"Apache Ivy/", false},
                {"Wharf Ivy/", false},
                {"anything", true},
                {"", true},
        };
    }

    @DataProvider
    public static Object[][] extractRequestClientTypeProvider() {
        return new Object[][] {
                //header,  type
                {"Debian APT-HTTP/1.3 (2.0.2ubuntu0.1)", RequestClientType.DEBIAN},
                {"Apache Ivy/", RequestClientType.APACHE_IVY},
                {"Wharf Ivy/", RequestClientType.WHARF_IVY},
                {"anything", RequestClientType.UNKNOWN},
                {"", RequestClientType.UNKNOWN},
        };
    }

    @DataProvider
    public static Object[][] provideUserNames() {
        return new Object[][] {
                //user,  agent
                {"xray", "xray-agent"},
                {"token:jfrt@123", ""},
                {"token:xyz@123", ""},
                {"token:abc", ""},
                {"xday", ""},
                {"admin", ""},
                {"system", ""},
                {"me", ""}
        };
    }

    @DataProvider
    public static Object[][] provideUserNamesForXrayToken() {
        return new Object[][] {
                //user,  agent
                {"jfxr@123", "xray-token"},
                {"xray", ""},
                {"jfrt@123", ""},
                {"token:xyz@123", ""},
                {"abc", ""},
                {"xday", ""},
                {"admin", ""},
                {"system", ""},
                {"me", ""},
                {"jfxana@123", "xray-token"},
                {"jfxpst@123", "xray-token"},
                {"jfxidx@123", "xray-token"},
        };
    }

    @DataProvider
    public static Object[][] provideNonAsciiFileNames() {
        return new Object[][] {
                {"测试.jpg"},
                {"אאא"},
        };
    }
}