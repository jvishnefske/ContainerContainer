package org.artifactory.engine;

import org.apache.http.HttpHeaders;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.RequestClientType;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.traffic.context.TrafficContextThreadLocal;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.storage.binstore.common.BinaryElementRequestImpl;
import org.jfrog.storage.binstore.ifc.ManualRedirectType;
import org.jfrog.storage.binstore.ifc.SignedUrl;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.storage.binstore.ifc.model.BinaryElementRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Enumeration;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.request.RequestClientType.DEBIAN;
import static org.jfrog.storage.binstore.ifc.ManualRedirectType.DEFAULT;
import static org.jfrog.storage.binstore.ifc.RedirectType.S3;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ORIGIN_PACKAGE_TYPE;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ORIGIN_PROJECT_KEY;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ORIGIN_REPOSITORY_KEY;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ORIGIN_REPO_TYPE;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.REDIRECT_GROUP;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.SKIP_CONTENT_DISPOSITION;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.SUBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BinaryElementFactoryTest extends ArtifactoryHomeBoundJUnitTest {

    private BinaryElementFactory binaryElementFactory;
    private DownloadServiceImpl downloadService;
    @Mock
    private ArtifactoryBinaryService binaryService;
    @Mock
    private SecurityService securityService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private ArtifactoryRequest request;
    @Mock
    private InternalRequestContext requestContext;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(requestContext.getRequest()).thenReturn(request);
        downloadService = new DownloadServiceImpl();
        downloadService.binaryService = binaryService;
        when(centralConfigDescriptor.isUseOnlyTokenForIdentifyingXrayUser()).thenReturn(true);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        binaryElementFactory = new BinaryElementFactory(securityService, projectsService, centralConfigService);
    }

    @AfterEach
    public void unbind() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    private static Stream<Arguments> provideRequestData() {
        return Stream.of(
                Arguments.of("xray/1.0", "xray", "xray"),
                Arguments.of("xray/1.0", "test-user", null),
                Arguments.of("jfrog-cli", "xray", null),
                Arguments.of("xray/1.0", "token:jfrt@123", null),
                Arguments.of("jfrog-cli", "token:jfxr@123", null),
                Arguments.of("x-ray/1.0", "token:jfxr@123", null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideRequestData")
    void testGetXrayUserName(String userAgent, String principal, String expectedResult) {
        when(securityService.currentUsername()).thenReturn(principal);
        when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn(userAgent);
        String result = binaryElementFactory.getXrayUserName(requestContext, null);
        assertEquals(result, expectedResult);
    }

    private static Stream<Arguments> provideRequestDataForAccessTokenAuthentication() {
        return Stream.of(
                //agent, user, expectedResult
                Arguments.of("xray/1.0", "xray", null),
                Arguments.of("xray/1.0", "test-user", null),
                Arguments.of("jfrog-cli", "xray", null),
                Arguments.of("xray/1.0", "token:jfrt@123", null),
                Arguments.of("jfrog-cli", "jfxr@123", null),
                Arguments.of("xray/1.0", "jfxr@123", "jfxr@123"),
                Arguments.of("x-ray/1.0", "jfxidx@123", null),
                Arguments.of("xray/1.0", "jfxidx@123", "jfxidx@123"),
                Arguments.of("x-ray/1.0", "jfxpst@123", null),
                Arguments.of("xray/1.0", "jfxpst@123", "jfxpst@123"),
                Arguments.of("x-ray/1.0", "jfxana@123", null),
                Arguments.of("xray/1.0", "jfxana@123", "jfxana@123")
        );
    }

    @ParameterizedTest
    @MethodSource("provideRequestDataForAccessTokenAuthentication")
    void testGetXrayUserNameUsingAccessTokenAuthentication(String userAgent, String principal,
            String expectedResult) {
        AccessTokenAuthentication accessTokenAuthentication = mock(AccessTokenAuthentication.class);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
        when(jwtAccessToken.getSubject()).thenReturn(principal);
        when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn(userAgent);
        String result = binaryElementFactory.getXrayUserName(requestContext, accessTokenAuthentication);
        assertEquals(result, expectedResult);
    }

    private static Stream<Arguments> redirectGroupDataProvider() {
        return Stream.of(
                // regularHeaderValue, dockerHeaderValue, expectedGroup
                Arguments.of("USA", "USA"),
                Arguments.of(null, null));
    }

    @ParameterizedTest
    @MethodSource("redirectGroupDataProvider")
    void testGetRedirectGroupName(String headerValue, String expectedGroup) {
        Enumeration<String> enumeration = mock(Enumeration.class);
        when(enumeration.hasMoreElements()).thenReturn(headerValue != null);
        when(enumeration.nextElement()).thenReturn(headerValue);
        when(request.getHeaderValues(REDIRECT_GROUP.getHeaderName())).thenReturn(enumeration);
        String groupName = binaryElementFactory.getRedirectGroupName(requestContext);
        assertEquals(expectedGroup, groupName);
    }

    @Test
    void testGetHeaderMapForRedirect() {
        // setup
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getName()).thenReturn("repoName");
        when(repoPath.getPath()).thenReturn("repoPath");

        RepoResourceInfo resourceInfo = mock(RepoResourceInfo.class);
        when(resourceInfo.getRepoPath()).thenReturn(repoPath);

        // test
        Map<String, String> headers = binaryElementFactory
                .getHeaderMapForRedirect(resourceInfo, "user", RequestClientType.UNKNOWN,
                        null, DEFAULT, DEFAULT_PROJECT,
                        "local", "npm", S3);

        // verify
        assertEquals(headers.get(SKIP_CONTENT_DISPOSITION.getHeaderName()), "false");

        // test
        headers = binaryElementFactory
                .getHeaderMapForRedirect(resourceInfo, "user", DEBIAN,
                        null, DEFAULT, DEFAULT_PROJECT,
                        "local", "npm", S3);

        // verify
        assertEquals(headers.get(SKIP_CONTENT_DISPOSITION.getHeaderName()), "true");

        // test
        headers = binaryElementFactory
                .getHeaderMapForRedirect(resourceInfo, "user", RequestClientType.APACHE_IVY,
                        null, DEFAULT, DEFAULT_PROJECT,
                        "local", "npm", S3);

        // verify
        assertEquals(headers.get(SKIP_CONTENT_DISPOSITION.getHeaderName()), "false");
    }

    @Test
    void testGetHeaderMapForRedirectOriginInfo() {
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getName()).thenReturn("jfrt-generic-local");
        when(repoPath.getPath()).thenReturn("cool-path");
        RepoResourceInfo resourceInfo = mock(RepoResourceInfo.class);
        when(resourceInfo.getRepoPath()).thenReturn(repoPath);
        ArtifactoryRequest artifactoryRequest = mock(ArtifactoryRequest.class);
        when(artifactoryRequest.getRepoKey()).thenReturn("jfrt-generic-virtual");
        ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
        when(artifactoryContext.beanForType(ProjectsService.class)).thenReturn(projectsService);
        when(projectsService.getProjectKeyOfRepository("jfrt-generic-virtual")).thenReturn("jfrt");
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        RepoConfig repoConfigDockerLocal = mock(RepoConfig.class);
        when(repositoryService.repoConfigByKey("jfrt-generic-virtual")).thenReturn(repoConfigDockerLocal);
        when(repoConfigDockerLocal.getRepoType()).thenReturn(VIRTUAL);
        when(repoConfigDockerLocal.getPackageType()).thenReturn(Generic);

        try {
            ArtifactoryContextThreadBinder.bind(artifactoryContext);
            TrafficContextThreadLocal.bind("jfrt-generic-virtual");

            Map<String, String> headers =
                    binaryElementFactory.getHeaderMapForRedirect(resourceInfo, "user", DEBIAN, null, DEFAULT,
                            DEFAULT_PROJECT, "local", "npm", S3);
            assertEquals(headers.get(ORIGIN_REPOSITORY_KEY.getHeaderName()), "jfrt-generic-virtual");
            assertEquals(headers.get(ORIGIN_PROJECT_KEY.getHeaderName()), "jfrt");
            assertEquals(headers.get(ORIGIN_REPO_TYPE.getHeaderName()), "virtual");
            assertEquals(headers.get(ORIGIN_PACKAGE_TYPE.getHeaderName()), "generic");
        } finally {
            TrafficContextThreadLocal.unbind();
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void testGetSignUrl() {
        // setup
        RepoResource resource = mock(RepoResource.class);
        RepoResourceInfo repoResourceInfo = mock(RepoResourceInfo.class);
        when(repoResourceInfo.getSha1()).thenReturn("cf23df2207d99a74fbe169e3eba035e633b65d94");
        when(resource.getInfo()).thenReturn(repoResourceInfo);
        Map<String, String> headers = binaryElementFactory
                .getHeaderMapForRedirect(repoResourceInfo, "user", DEBIAN,
                        null, DEFAULT, DEFAULT_PROJECT,
                        "local", "npm", S3);
        BinaryElementRequest request =
                new BinaryElementRequestImpl(resource.getInfo().getSha1(), null, null, 0L, headers);
        when(binaryService.getSignedUrl(request)).thenReturn(new SignedUrl("http://signed.url", S3));

        // test
        SignedUrl url = downloadService
                .getSignedUrl(request);

        // verify
        assertEquals("http://signed.url", url.value());
    }

    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                    SERVICE_TYPE,           SUBJECT_HEADER_PRESENT
                    jfxr@alabala,           true
                    jfxidx@alabala,         true
                    jfxpst@alabala,         true
                    jfxana@alabala,         true
                    jfac@alabala,           false
                    jfrt@alabala,           false
                    wrong-subject,          false
                    ,                       false
                    """
    )
    @ParameterizedTest
    void xRayUserIdentificationByTokenOnly(String serviceType, boolean subjectHeaderPresent) {
        setStringSystemProperty(ConstantValues.isAddUserNameToSignUrlForRedirect, "true");

        when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn("GammaRay/3.82.10");

        when(securityService.currentUsername()).thenReturn("gammaRayUser");

        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        String subject = serviceType + "@alabala";
        when(jwtAccessToken.getSubject()).thenReturn(subject);

        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        when(authentication.getAccessToken()).thenReturn(jwtAccessToken);

        SecurityContext securityContext = mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);

        Repo responseRepo = mock(Repo.class);
        when(responseRepo.getRepoConfig()).thenReturn(repoConfig);

        RepoResourceInfo repoResourceInfo = mock(RepoResourceInfo.class);
        RepoResource resource = mock(RepoResource.class);
        when(resource.getInfo()).thenReturn(repoResourceInfo);

        BinaryElement binaryElement = binaryElementFactory.getBinaryElement(requestContext, responseRepo,
                resource, ManualRedirectType.S3, S3);

        assertEquals(subjectHeaderPresent,
                subject.equals(binaryElement.getHeaders().getOrDefault(SUBJECT.getHeaderName(), "")));
    }
}
