package org.artifactory.webapp.servlet;

import com.jfrog.build.acl.bridge.BuildFacade;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.HttpStatus;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.addon.npm.NpmAuditRequestWrapper;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.FolderExpectedException;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.DownloadService;
import org.artifactory.api.request.UploadService;
import org.artifactory.api.rest.constant.ArtifactRestConstants;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.webdav.WebdavService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.exception.LoadHealerException;
import org.artifactory.exception.SQLIntegrityException;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.download.RateLimiterException;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throttling.lh.config.LoadHealerActivity;
import org.artifactory.throttling.lh.model.RunningActivityHandler;
import org.artifactory.throttling.lh.service.LoadHealerService;
import org.jfrog.client.util.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.testng.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.respondWith404ForNonExistentRepo;
import static org.artifactory.throttling.lh.config.LoadHealerActivity.LIMIT_ACTIVITY;
import static org.artifactory.throttling.lh.config.LoadHealerActivity.PARALLEL_DOWNLOAD_UPLOAD_PER_PACKAGE_TYPE;
import static org.artifactory.throttling.lh.config.LoadHealerActivity.PARALLEL_UPLOAD_PRE_REPO_KEY;
import static org.artifactory.webapp.servlet.RequestUtils.RepoRequestAnalysis.NOT_A_REPO_REQUEST;
import static org.artifactory.webapp.servlet.RequestUtils.RepoRequestAnalysis.REPO_REQUEST_REPO_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Michael Akushsky
 */
public class RepoFilterTest extends ArtifactoryHomeBoundJUnitTest {

    @BeforeEach
    public void setup() {
        ArtifactoryHome.bind(getOrCreateArtifactoryHomeStub());
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        ArtifactoryHome.unbind();
        MDC.clear();
    }

    @ParameterizedTest
    @ValueSource(classes = {SQLIntegrityException.class, FileExpectedException.class, FolderExpectedException.class,
            WorkerStopException.class, UnifiedEventException.class})
    void testFilterWhenExceptionOccursRespondConflict(Class<? extends RuntimeException> exceptionClass)
            throws IOException, RepoRejectException, ServletException {
        RepoFilter repoFilterSpy = spy(RepoFilter.class);

        ArtifactoryContext artifactoryContextMock = mock(ArtifactoryContext.class, RETURNS_DEEP_STUBS);
        UploadService uploadServiceMock = mock(UploadService.class);
        BuildFacade buildFacadeMock = mock(BuildFacade.class);
        SecurityService securityServiceMock = mock(SecurityService.class);
        LoadHealerService loadHealerService = mock(LoadHealerService.class);
        RepositoryService repoServiceMock = mock(RepositoryService.class);

        try (
                MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class);
                MockedStatic<RequestUtils> requestUtilsMock = mockStatic(RequestUtils.class);
                MockedStatic<ArtifactoryHome> artifactoryHomeMock = mockStatic(ArtifactoryHome.class);
                MockedStatic<PathUtils> pathUtilsMock = mockStatic(PathUtils.class)
        ) {

            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContextMock);
            when(artifactoryContextMock.beanForType(UploadService.class)).thenReturn(uploadServiceMock);
            when(artifactoryContextMock.beanForType(BuildFacade.class)).thenReturn(buildFacadeMock);
            when(artifactoryContextMock.beanForType(SecurityService.class)).thenReturn(securityServiceMock);
            when(artifactoryContextMock.beanForType(LoadHealerService.class)).thenReturn(loadHealerService);
            when(artifactoryContextMock.getRepositoryService()).thenReturn(repoServiceMock);

            // RTFACT-22667 situation preparing
            doThrow(exceptionClass).when(uploadServiceMock)
                    .upload(any(ArtifactoryRequest.class), any(ArtifactoryResponse.class));

            // Construct different fake objects
            requestUtilsMock.when(() -> RequestUtils.isRepoRequest(any(), anyBoolean())).thenReturn(true);
            requestUtilsMock.when(() -> RequestUtils.getServletPathFromRequest(any())).thenReturn("/");

            ArtifactoryHome artifactoryHomeObjectMock = mock(ArtifactoryHome.class, RETURNS_DEEP_STUBS);
            artifactoryHomeMock.when(ArtifactoryHome::get).thenReturn(artifactoryHomeObjectMock);

            pathUtilsMock.when(() -> PathUtils.getFirstPathElement(anyString())).thenReturn("/");
            pathUtilsMock.when(() -> PathUtils.splitZipResourcePathIfExist(anyString(), anyBoolean()))
                    .thenReturn(new String[] {"_", "_"});

            // Construct fake http request and response objects
            HttpServletRequest servletRequestMock = mock(HttpServletRequest.class);
            when(servletRequestMock.getAttribute(anyString())).thenReturn("FAKE");
            when(servletRequestMock.getMethod()).thenReturn("put");
            when(servletRequestMock.getRequestURI()).thenReturn("FAKE");
            HttpServletResponse servletResponseMock = mock(HttpServletResponse.class, RETURNS_DEEP_STUBS);
            FilterChain filterChainMock = mock(FilterChain.class);

            requestUtilsMock.when(() -> RequestUtils.analyzeRepoRequest(servletRequestMock)).thenReturn(
                    RequestUtils.RepoRequestAnalysis.REPO_REQUEST_REPO_EXISTS);

            // Wrap them to artifactory ones
            ArtifactoryResponse artifactoryResponse = new HttpArtifactoryResponse(servletResponseMock);
            ArtifactoryResponse artifactoryResponseSpy = spy(artifactoryResponse);
            doReturn(artifactoryResponseSpy).when(repoFilterSpy).createArtifactoryResponse(any());

            // And the rest - fake input for filter
            FilterConfig filterConfigMock = mock(FilterConfig.class);
            ServletContext servletContextMock = mock(ServletContext.class);
            when(filterConfigMock.getServletContext()).thenReturn(servletContextMock);
            when(filterConfigMock.getFilterName()).thenReturn("FAKE");
            when(servletContextMock.getAttribute(anyString())).thenReturn(null);

            // Now let's execute
            repoFilterSpy.init(filterConfigMock);
            repoFilterSpy.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

            // And verify HttpStatus
            verify(artifactoryResponseSpy).sendError(eq(HttpStatus.SC_CONFLICT), any(), any());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "PUT,/artifactory-build-info/.evidence/A/B/C.json,false,buildinfo,upload, false",
            "PUT,/test-npm-local/sample/sample-package-1.2.0.tgz,false,npm,upload, false",
            "PUT,api/test-npm-local/sample/sample-package-1.2.0.tgz,false,npm,upload, true",
            "GET,/test-npm-local/sample/sample-package-1.2.0.tgz,false,npm,download, false",
            "GET,/test-npm-local/sample/sample-package-1.2.0.tgz:properties,false,npm,meta, false",
            "GET,/test-npm-local/sample/sample-package-1.2.0.tgz,true,npm,props, false",
            "GET,/test-npm-local/list/,false,npm,list, false",
            "POST,/npm/v1/security/audits,false,npm,audit, false",
            "TRACE,/test-npm-local/,false,npm,webdav, false",
    })
    void checkActivityDetails(String method, String uri, boolean isProps, String packageType, String expectedGroup,
            boolean useLoadHealer) throws ServletException, IOException {

        RepositoryService repoServiceMock = mock(RepositoryService.class);
        LoadHealerService loadHealerService = mock(LoadHealerService.class);
        when(repoServiceMock.getAllRepoKeys()).thenReturn(Set.of("test-npm-local", "artifactory-build-info"));

        LocalRepoConfig repoConfigNpm = LocalRepoConfig.create("test-npm-local", RepoType.Npm);
        LocalRepoConfig repoConfigBuildInfo = LocalRepoConfig.create("artifactory-build-info", RepoType.BuildInfo);
        when(repoServiceMock.repoConfigByKey("test-npm-local")).thenReturn(repoConfigNpm);
        when(repoServiceMock.repoConfigByKey("artifactory-build-info")).thenReturn(repoConfigBuildInfo);
        when(repoServiceMock.repoExists("test-npm-local")).thenReturn(true);
        when(repoServiceMock.repoExists("artifactory-build-info")).thenReturn(true);

        BuildFacade buildFacade = mock(BuildFacade.class);
        ArtifactoryContext artifactoryContextMock = mock(ArtifactoryContext.class, RETURNS_DEEP_STUBS);
        when(buildFacade.getAllBuildInfoRepos()).thenReturn(Set.of("artifactory-build-info"));
        when(artifactoryContextMock.getRepositoryService()).thenReturn(repoServiceMock);
        when(artifactoryContextMock.beanForType(BuildFacade.class)).thenReturn(buildFacade);
        when(artifactoryContextMock.beanForType(DownloadService.class)).thenReturn(mock(DownloadService.class));
        when(artifactoryContextMock.beanForType(UploadService.class)).thenReturn(mock(UploadService.class));
        when(artifactoryContextMock.beanForType(WebdavService.class)).thenReturn(mock(WebdavService.class));
        when(artifactoryContextMock.beanForType(SecurityService.class)).thenReturn(mock(SecurityService.class));
        when(artifactoryContextMock.beanForType(LoadHealerService.class)).thenReturn(loadHealerService);

        try (
                MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class);
                MockedStatic<ArtifactoryHome> artifactoryHomeMock = mockStatic(ArtifactoryHome.class)
        ) {

            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContextMock);

            ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class, RETURNS_DEEP_STUBS);
            artifactoryHomeMock.when(ArtifactoryHome::get).thenReturn(artifactoryHome);

            HttpServletRequest requestMock = mock(HttpServletRequest.class, RETURNS_DEEP_STUBS);
            when(requestMock.getMethod()).thenReturn(method);
            when(requestMock.getRequestURI()).thenReturn(uri);
            when(requestMock.getParameter(ArtifactRestConstants.PROPERTIES_PARAM)).thenReturn(isProps ? "true" : null);

            HttpServletResponse responseMock = mock(HttpServletResponse.class, RETURNS_DEEP_STUBS);

            // Filter configuration
            FilterConfig filterConfigMock = mock(FilterConfig.class);
            ServletContext servletContextMock = mock(ServletContext.class);
            when(filterConfigMock.getServletContext()).thenReturn(servletContextMock);
            when(filterConfigMock.getFilterName()).thenReturn("activityTest");
            when(servletContextMock.getAttribute(anyString())).thenReturn(null);

            // Prepare assertions
            FilterChain chain = (ServletRequest req, ServletResponse resp) -> {};

            // Now let's execute
            RepoFilter repoFilter = new RepoFilter();
            repoFilter.init(filterConfigMock);

            if (useLoadHealer && uri.contains("api/load")) {
                doThrow(new LoadHealerException("upload blocked")).when(loadHealerService).assertAllows(any());
                Throwable error = assertThrows(LoadHealerException.class,
                        () -> repoFilter.doFilter(requestMock, responseMock, chain));
                Assert.assertEquals(error.getMessage(), "upload blocked");
            } else {
                repoFilter.doFilter(requestMock, responseMock, chain);
                // Check expectations
                if (!useLoadHealer) {
                    assertEquals(expectedGroup, CurrentActivity.GROUP.get());
                    assertEquals(packageType, CurrentActivity.PACKAGE.get());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("loadHealerTestCases")
    void loadHealerBlock(String user, String resource, LoadHealerActivity activity, String apiUrl, String method,
            boolean repoRequest)
            throws ServletException, IOException {
        RepoFilter repoFilterSpy = spy(RepoFilter.class);

        ArtifactoryContext artifactoryContextMock = mock(ArtifactoryContext.class, RETURNS_DEEP_STUBS);
        UploadService uploadServiceMock = mock(UploadService.class);
        BuildFacade buildFacadeMock = mock(BuildFacade.class);
        SecurityService securityServiceMock = mock(SecurityService.class);
        LoadHealerService loadHealerService = mock(LoadHealerService.class);
        RepositoryService repoServiceMock = mock(RepositoryService.class);

        try (
                MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class);
                MockedStatic<RequestUtils> requestUtilsMock = mockStatic(RequestUtils.class);
                MockedStatic<ArtifactoryHome> artifactoryHomeMock = mockStatic(ArtifactoryHome.class);
                MockedStatic<PathUtils> pathUtilsMock = mockStatic(PathUtils.class)
        ) {

            CurrentActivity.PACKAGE.put("generic");
            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContextMock);
            when(artifactoryContextMock.beanForType(UploadService.class)).thenReturn(uploadServiceMock);
            when(artifactoryContextMock.beanForType(BuildFacade.class)).thenReturn(buildFacadeMock);
            when(artifactoryContextMock.beanForType(SecurityService.class)).thenReturn(securityServiceMock);
            when(artifactoryContextMock.beanForType(LoadHealerService.class)).thenReturn(loadHealerService);
            when(artifactoryContextMock.getRepositoryService()).thenReturn(repoServiceMock);

            when(securityServiceMock.currentUsername()).thenReturn(user);
            RunningActivityHandler runningActivityHandler = mock(RunningActivityHandler.class);
            when(loadHealerService.startActivity(activity, resource)).thenReturn(runningActivityHandler);
            doThrow(new LoadHealerException("Action blocked")).when(loadHealerService)
                    .assertAllows(runningActivityHandler);

            // Construct different fake objects
            requestUtilsMock.when(() -> RequestUtils.isRepoRequest(any(), anyBoolean())).thenReturn(true);
            requestUtilsMock.when(() -> RequestUtils.getServletPathFromRequest(any())).thenReturn(apiUrl);

            ArtifactoryHome artifactoryHomeObjectMock = mock(ArtifactoryHome.class, RETURNS_DEEP_STUBS);
            artifactoryHomeMock.when(ArtifactoryHome::get).thenReturn(artifactoryHomeObjectMock);

            pathUtilsMock.when(() -> PathUtils.getFirstPathElement(anyString())).thenReturn("repo-key");
            pathUtilsMock.when(() -> PathUtils.splitZipResourcePathIfExist(anyString(), anyBoolean()))
                    .thenReturn(new String[] {"_", "_"});

            // Construct fake http request and response objects
            HttpServletRequest servletRequestMock = mock(HttpServletRequest.class);
            when(servletRequestMock.getAttribute(anyString())).thenReturn("FAKE");
            when(servletRequestMock.getMethod()).thenReturn(method);
            when(servletRequestMock.getRequestURI()).thenReturn("FAKE");
            HttpServletResponse servletResponseMock = mock(HttpServletResponse.class, RETURNS_DEEP_STUBS);
            FilterChain filterChainMock = mock(FilterChain.class);

            if (repoRequest) {
                requestUtilsMock.when(() -> RequestUtils.analyzeRepoRequest(servletRequestMock)).thenReturn(
                        RequestUtils.RepoRequestAnalysis.REPO_REQUEST_REPO_EXISTS);
            }

            // Wrap them to artifactory ones
            ArtifactoryResponse artifactoryResponse = new HttpArtifactoryResponse(servletResponseMock);
            ArtifactoryResponse artifactoryResponseSpy = spy(artifactoryResponse);
            doReturn(artifactoryResponseSpy).when(repoFilterSpy).createArtifactoryResponse(any());

            // And the rest - fake input for filter
            FilterConfig filterConfigMock = mock(FilterConfig.class);
            ServletContext servletContextMock = mock(ServletContext.class);
            when(filterConfigMock.getServletContext()).thenReturn(servletContextMock);
            when(filterConfigMock.getFilterName()).thenReturn("FAKE");
            when(servletContextMock.getAttribute(anyString())).thenReturn(null);

            // Now let's execute
            repoFilterSpy.init(filterConfigMock);
            try {
                repoFilterSpy.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
                verify(artifactoryResponseSpy).sendError(eq(HttpStatus.SC_TOO_MANY_REQUESTS), any(), any());
            } catch (LoadHealerException e) {
                assertEquals("Action blocked", e.getMessage());
            }
        }
    }

    @Test
    void rateLimiterException_shouldReturn429_whenDownloadRequestIsRateLimited()
            throws ServletException, IOException {
        RepoFilter repoFilterSpy = spy(RepoFilter.class);

        ArtifactoryContext artifactoryContextMock = mock(ArtifactoryContext.class, RETURNS_DEEP_STUBS);
        DownloadService downloadServiceMock = mock(DownloadService.class);
        BuildFacade buildFacadeMock = mock(BuildFacade.class);
        SecurityService securityServiceMock = mock(SecurityService.class);
        LoadHealerService loadHealerService = mock(LoadHealerService.class);
        RepositoryService repoServiceMock = mock(RepositoryService.class);

        try (
                MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class);
                MockedStatic<RequestUtils> requestUtilsMock = mockStatic(RequestUtils.class);
                MockedStatic<ArtifactoryHome> artifactoryHomeMock = mockStatic(ArtifactoryHome.class);
                MockedStatic<PathUtils> pathUtilsMock = mockStatic(PathUtils.class)
        ) {

            CurrentActivity.PACKAGE.put("generic");
            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContextMock);
            when(artifactoryContextMock.beanForType(DownloadService.class)).thenReturn(downloadServiceMock);
            when(artifactoryContextMock.beanForType(BuildFacade.class)).thenReturn(buildFacadeMock);
            when(artifactoryContextMock.beanForType(SecurityService.class)).thenReturn(securityServiceMock);
            when(artifactoryContextMock.beanForType(LoadHealerService.class)).thenReturn(loadHealerService);
            when(artifactoryContextMock.getRepositoryService()).thenReturn(repoServiceMock);

            when(securityServiceMock.currentUsername()).thenReturn("test-user");
            RunningActivityHandler runningActivityHandler = mock(RunningActivityHandler.class);
            when(loadHealerService.startActivity(LIMIT_ACTIVITY, "download")).thenReturn(runningActivityHandler);
            when(loadHealerService.startActivity(PARALLEL_DOWNLOAD_UPLOAD_PER_PACKAGE_TYPE, "generic"))
                    .thenReturn(runningActivityHandler);

            doThrow(new RateLimiterException("Request rate limiter time exceeded")).when(downloadServiceMock)
                    .process(any(ArtifactoryRequest.class), any(ArtifactoryResponse.class));

            // Construct different fake objects
            requestUtilsMock.when(() -> RequestUtils.isRepoRequest(any(), anyBoolean())).thenReturn(true);
            requestUtilsMock.when(() -> RequestUtils.getServletPathFromRequest(any())).thenReturn("/repo-key/path");

            ArtifactoryHome artifactoryHomeObjectMock = mock(ArtifactoryHome.class, RETURNS_DEEP_STUBS);
            artifactoryHomeMock.when(ArtifactoryHome::get).thenReturn(artifactoryHomeObjectMock);

            pathUtilsMock.when(() -> PathUtils.getFirstPathElement(anyString())).thenReturn("repo-key");
            pathUtilsMock.when(() -> PathUtils.splitZipResourcePathIfExist(anyString(), anyBoolean()))
                    .thenReturn(new String[] {"_", "_"});

            // Construct fake http request and response objects
            HttpServletRequest servletRequestMock = mock(HttpServletRequest.class);
            when(servletRequestMock.getAttribute(anyString())).thenReturn("FAKE");
            when(servletRequestMock.getMethod()).thenReturn("GET");
            when(servletRequestMock.getRequestURI()).thenReturn("/repo-key/path");
            HttpServletResponse servletResponseMock = mock(HttpServletResponse.class, RETURNS_DEEP_STUBS);
            FilterChain filterChainMock = mock(FilterChain.class);

            requestUtilsMock.when(() -> RequestUtils.analyzeRepoRequest(servletRequestMock)).thenReturn(
                    RequestUtils.RepoRequestAnalysis.REPO_REQUEST_REPO_EXISTS);

            // Wrap them to artifactory ones
            ArtifactoryResponse artifactoryResponse = new HttpArtifactoryResponse(servletResponseMock);
            ArtifactoryResponse artifactoryResponseSpy = spy(artifactoryResponse);
            doReturn(artifactoryResponseSpy).when(repoFilterSpy).createArtifactoryResponse(any());

            // And the rest - fake input for filter
            FilterConfig filterConfigMock = mock(FilterConfig.class);
            ServletContext servletContextMock = mock(ServletContext.class);
            when(filterConfigMock.getServletContext()).thenReturn(servletContextMock);
            when(filterConfigMock.getFilterName()).thenReturn("FAKE");
            when(servletContextMock.getAttribute(anyString())).thenReturn(null);

            // Now let's execute
            repoFilterSpy.init(filterConfigMock);
            repoFilterSpy.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

            // Verify that 429 status code is returned
            verify(artifactoryResponseSpy).sendError(eq(HttpStatus.SC_TOO_MANY_REQUESTS), any(), any());
        }
    }

    public static Stream<Arguments> repoNotFoundCasesProvider() {
        return Stream.of(Arguments.of(true, HttpStatus.SC_NOT_FOUND));
    }

    @ParameterizedTest
    @MethodSource("repoNotFoundCasesProvider")
    void testRepoRequest_RepoNotFound_Return404(boolean return404flagEnabled, int expectedStatus)
            throws ServletException, IOException {
        RepoFilter repoFilterSpy = spy(RepoFilter.class);

        ArtifactoryContext artifactoryContextMock = mock(ArtifactoryContext.class, RETURNS_DEEP_STUBS);
        SecurityService securityServiceMock = mock(SecurityService.class);
        RepositoryService repoServiceMock = mock(RepositoryService.class);
        when(repoServiceMock.repoExists(anyString())).thenReturn(false);
        when(artifactoryContextMock.getRepositoryService()).thenReturn(repoServiceMock);
        when(artifactoryContextMock.beanForType(SecurityService.class)).thenReturn(securityServiceMock);
        LoadHealerService loadHealerService = mock(LoadHealerService.class);
        when(artifactoryContextMock.beanForType(LoadHealerService.class)).thenReturn(loadHealerService);

        try (
                MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class);
                MockedStatic<RequestUtils> requestUtilsMock = mockStatic(RequestUtils.class)
        ) {
            setStringSystemProperty(respondWith404ForNonExistentRepo, String.valueOf(return404flagEnabled));

            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContextMock);

            String path = "/artifactory/load/non-existing-repo";

            HttpServletRequest servletRequestMock = mock(HttpServletRequest.class, RETURNS_DEEP_STUBS);
            when(servletRequestMock.getMethod()).thenReturn("put");
            when(servletRequestMock.getContextPath()).thenReturn("/artifactory");
            when(servletRequestMock.getRequestURI()).thenReturn(path);

            HttpServletResponse servletResponseMock = mock(HttpServletResponse.class, RETURNS_DEEP_STUBS);
            FilterChain filterChainMock = mock(FilterChain.class);

            requestUtilsMock.when(() -> RequestUtils.getServletPathFromRequest(servletRequestMock)).thenReturn(path);
            requestUtilsMock.when(() -> RequestUtils.isRepoRequest(any(), anyBoolean())).thenReturn(true);
            requestUtilsMock.when(() -> RequestUtils.analyzeRepoRequest(servletRequestMock)).thenReturn(
                    RequestUtils.RepoRequestAnalysis.REPO_REQUEST_REPO_NOT_EXISTS);

            // And the rest - fake input for filter
            FilterConfig filterConfigMock = mock(FilterConfig.class);
            ServletContext servletContextMock = mock(ServletContext.class);
            when(filterConfigMock.getServletContext()).thenReturn(servletContextMock);
            when(filterConfigMock.getFilterName()).thenReturn("FAKE");
            when(servletContextMock.getAttribute(anyString())).thenReturn(null);

            // Now let's execute
            repoFilterSpy.init(filterConfigMock);
            repoFilterSpy.doFilter(servletRequestMock, servletResponseMock, filterChainMock);

            // And verify HttpStatus
            verify(servletResponseMock).sendError(expectedStatus);
        } finally {
            setStringSystemProperty(respondWith404ForNonExistentRepo, respondWith404ForNonExistentRepo.getDefValue());
        }
    }

    @Test
    void analyzeRepoRequest_nullServletPath_returnsNotARepoRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        assertEquals(NOT_A_REPO_REQUEST, RepoFilter.analyzeRepoRequest(request, null));
    }

    @Test
    void analyzeRepoRequest_nonNullServletPath_delegatesToRequestUtils() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        try (var requestUtilsMock = mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.analyzeRepoRequest(request))
                    .thenReturn(REPO_REQUEST_REPO_EXISTS);
            assertEquals(REPO_REQUEST_REPO_EXISTS, RepoFilter.analyzeRepoRequest(request, "/some/path"));
        }
    }

    @Test
    void wrapRequestIfNeeded_gitLfsRequestWraps() {
        RepoFilter filter = new RepoFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getServletPath()).thenReturn("/api/lfs");
        when(req.getPathInfo()).thenReturn("");
        when(req.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api/lfs"));
        HttpServletRequest wrapped = filter.wrapRequestIfNeeded(req);
        assertTrue(wrapped instanceof RepoFilter.GitLfsMalformedRequestWrapper);
    }

    @Test
    void wrapRequestIfNeeded_dockerRequestWraps() {
        RepoFilter filter = new RepoFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getServletPath()).thenReturn("/api/docker");
        when(req.getPathInfo()).thenReturn("");
        when(req.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api/docker"));
        HttpServletRequest wrapped = filter.wrapRequestIfNeeded(req);
        assertInstanceOf(RepoFilter.DockerMalformedRequestWrapper.class, wrapped);
    }

    @Test
    void isGitLfsRequest_trueForLfsPath() {
        RepoFilter filter = new RepoFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getServletPath()).thenReturn("/api/lfs");
        when(req.getPathInfo()).thenReturn("");
        when(req.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api/lfs"));
        assertTrue(filter.isGitLfsRequest(req));
    }

    @Test
    void getApiRequestPath_dockerAndOci() {
        RepoFilter filter = new RepoFilter();
        HttpServletRequest reqDocker = mock(HttpServletRequest.class);
        when(reqDocker.getServletPath()).thenReturn("/api/docker");
        when(reqDocker.getPathInfo()).thenReturn("");
        when(reqDocker.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api/docker"));
        assertEquals("/api/docker", filter.getApiRequestPath(reqDocker));

        HttpServletRequest reqOci = mock(HttpServletRequest.class);
        when(reqOci.getServletPath()).thenReturn("/api/oci");
        when(reqOci.getPathInfo()).thenReturn("");
        when(reqOci.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api/oci"));
        assertEquals("/api/oci", filter.getApiRequestPath(reqOci));
    }

    @Test
    void isListBrowsingPath_trueAndFalse() {
        assertTrue(RepoFilter.isListBrowsingPath("/list/"));
        assertFalse(RepoFilter.isListBrowsingPath("/notlist/"));
    }

    @Test
    void isEvidenceServletPath_trueAndFalse() {
        RepoFilter filter = new RepoFilter();
        assertTrue(filter.isEvidenceServletPath("/artifactory-build-info/.evidence/A/B/C.json"));
        assertFalse(filter.isEvidenceServletPath("/test-npm-local/sample/sample-package.tgz"));
    }

    @Test
    void resolveDownloadAction_metaAndProps() {
        RepoFilter filter = new RepoFilter();

        ArtifactoryRequest req = mock(ArtifactoryRequest.class);
        ArtifactoryResponse resp = mock(ArtifactoryResponse.class);
        when(req.isMetadata()).thenReturn(true);
        assertEquals("meta", filter.resolveDownloadAction(req, resp));
        when(req.isMetadata()).thenReturn(false);
        when(resp.isPropertiesQuery()).thenReturn(true);
        assertEquals("props", filter.resolveDownloadAction(req, resp));
        when(resp.isPropertiesQuery()).thenReturn(false);
        assertEquals("download", filter.resolveDownloadAction(req, resp));
    }

    @Test
    void resolveDownloadAction_forPackageMetadataRequest() {
        // Arrange
        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.getRepoType("test-repo")).thenReturn(RepoType.Generic);

        CacheExpiry cacheExpiry = mock(CacheExpiry.class);
        when(cacheExpiry.getMetadataArtifactType(RepoType.Generic, "test-repo", "some/path"))
                .thenReturn("repo_index");

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.getRepositoryService()).thenReturn(repoService);
        when(context.beanForType(CacheExpiry.class)).thenReturn(cacheExpiry);

        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoKey()).thenReturn("test-repo");
        when(request.getPath()).thenReturn("some/path");
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);

        RepoFilter filter = new RepoFilter();

        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get).thenReturn(context);

            // Act
            String actual = filter.resolveDownloadAction(request, response);

            // Assert
            assertEquals("repo_index", actual);
        }
    }

    @Test
    void resolveDownloadActionFromPackageType_shouldReturnNull_forSystemRepositories() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);

        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get).thenReturn(context);

            RepoFilter filter = new RepoFilter();
            for (SystemRepos.SystemRepoConfig config : SystemRepos.SystemRepoConfig.values()) {
                ArtifactoryRequest request = mock(ArtifactoryRequest.class);
                when(request.getRepoKey()).thenReturn(config.getRepoKey());
                when(request.getPath()).thenReturn("file.info");

                String actual = filter.resolveDownloadActionFromPackageType(request);
                assertNull(actual, "Expected null action for system repository: " + config.getRepoKey());

                // filter supposed to return immediately without calling repository service
                verify(context, Mockito.never()).getRepositoryService();
            }
        }
    }

    @Test
    void doWebDavMethod_unsupportedMethodSetsStatus() throws IOException {
        RepoFilter filter = spy(RepoFilter.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        ArtifactoryRequest artReq = mock(ArtifactoryRequest.class);
        ArtifactoryResponse artResp = mock(ArtifactoryResponse.class);
        WebdavService webdavService = mock(WebdavService.class);
        doReturn(webdavService).when(filter).getWebdavService();
        when(webdavService.handleRequest(any(), any(), any())).thenReturn(false);
        when(webdavService.supportedMethods()).thenReturn(Set.of("GET", "PUT"));
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        filter.doWebDavMethod(req, resp, "DELETE", artReq, artResp);
        verify(resp).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(resp).setHeader(eq("Allow"), anyString());
    }

    @Test
    void checkNoUrlInPath_forbiddenUrlTriggersError() throws IOException {
        RepoFilter filter = new RepoFilter();
        ArtifactoryRequest artReq = mock(ArtifactoryRequest.class);
        ArtifactoryResponse artResp = mock(ArtifactoryResponse.class);
        when(artReq.getPath()).thenReturn("http://forbidden.com");
        assertTrue(filter.checkNoUrlInPath(artReq, artResp));
        verify(artResp).sendError(eq(HttpStatus.SC_BAD_REQUEST), contains("Forbidden Url in path"), any());
    }

    @Test
    void wrapRequestIfNeeded_regularRequestNoWrap() {
        RepoFilter filter = new RepoFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getServletPath()).thenReturn("/api/regular");
        when(req.getPathInfo()).thenReturn("");
        when(req.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api/regular"));
        HttpServletRequest wrapped = filter.wrapRequestIfNeeded(req);
        assertEquals(req, wrapped);
    }

    @Test
    void gitLfsWrapper_setsContentType() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        RepoFilter.GitLfsMalformedRequestWrapper wrapper = new RepoFilter.GitLfsMalformedRequestWrapper(req);
        when(req.getHeaders("Content-Type")).thenReturn(Collections.enumeration(List.of()));

        Enumeration<String> headers = wrapper.getHeaders("Content-Type");
        assertTrue(headers.hasMoreElements());
        assertEquals("application/vnd.git-lfs+json", headers.nextElement());
    }

    @Test
    void dockerWrapper_setsContentTypeWhenMissing() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn("");
        RepoFilter.DockerMalformedRequestWrapper wrapper = new RepoFilter.DockerMalformedRequestWrapper(req);

        Enumeration<String> headers = wrapper.getHeaders("Content-Type");
        assertTrue(headers.hasMoreElements());
        assertEquals("application/octet-stream", headers.nextElement());
    }

    @Test
    void checkNoUrlInPath_validPathReturnsTrue() throws IOException {
        RepoFilter filter = new RepoFilter();
        ArtifactoryRequest artReq = mock(ArtifactoryRequest.class);
        ArtifactoryResponse artResp = mock(ArtifactoryResponse.class);
        when(artReq.getPath()).thenReturn("valid/path");
        assertFalse(filter.checkNoUrlInPath(artReq, artResp));
    }

    @Test
    void getApiRequestPath_nonApiPathReturnsNull() {
        RepoFilter filter = new RepoFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getServletPath()).thenReturn("/artifactory/repo");
        when(req.getPathInfo()).thenReturn("");
        when(req.getRequestURL()).thenReturn(new StringBuffer("http://localhost/artifactory/repo"));
        assertNull(filter.getApiRequestPath(req));
    }

    @Test
    void createArtifactoryRequest_setsPackageType() throws Exception {
        RepoFilter filter = new RepoFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/artifactory/test-repo");
        when(req.getContextPath()).thenReturn("");
        when(req.getServletPath()).thenReturn("/test-repo");

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        RepositoryService repoService = mock(RepositoryService.class);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);

        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get).thenReturn(context);
            when(context.getRepositoryService()).thenReturn(repoService);
            when(repoService.repoConfigByKey(anyString())).thenReturn(repoConfig);
            when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);

            ArtifactoryRequest artReq = filter.createArtifactoryRequest(req);
            assertNotNull(artReq);
        }
    }

    @Test
    void doRepoListing_executesListingFlow() throws ServletException, IOException {
        RepoFilter filter = spy(RepoFilter.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        RequestDispatcher requestDispatcher = mock(RequestDispatcher.class);

        String servletPath = "/list/repoKey/some/path/";
        when(req.getRequestURI()).thenReturn(servletPath);
        when(req.getServletPath()).thenReturn(servletPath);
        when(req.getMethod()).thenReturn("GET");
        when(req.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);

        SecurityService securityService = mock(SecurityService.class);
        when(securityService.currentUsername()).thenReturn("user");

        DownloadService downloadService = mock(DownloadService.class);

        LoadHealerService loadHealerService = mock(LoadHealerService.class);

        RepositoryService repositoryService = mock(RepositoryService.class);
        when(repositoryService.repoExists("repoKey")).thenReturn(true);

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(DownloadService.class)).thenReturn(downloadService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(LoadHealerService.class)).thenReturn(loadHealerService);
        when(context.getRepositoryService()).thenReturn(repositoryService);

        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get).thenReturn(context);

            filter.doFilter(req, resp, chain);

            verify(requestDispatcher).forward(req, resp);
        }
    }

    @Test
    void doBuildUploadRedirect_redirectsToBuildEndpoint() throws ServletException, IOException {
        RepoFilter filter = spy(RepoFilter.class);
        HttpServletRequest req = mock(HttpServletRequest.class, RETURNS_DEEP_STUBS);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        RequestDispatcher requestDispatcher = mock(RequestDispatcher.class);

        String servletPath = "/artifactory-build-info/path/to/artifact.txt";
        when(req.getRequestURI()).thenReturn(servletPath);
        when(req.getServletPath()).thenReturn(servletPath);
        when(req.getMethod()).thenReturn(HttpMethod.PUT.name());
        when(req.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);

        FilterChain chain = mock(FilterChain.class);

        BuildFacade buildFacade = mock(BuildFacade.class);
        when(buildFacade.getAllBuildInfoRepos()).thenReturn(Set.of("artifactory-build-info"));

        LoadHealerService loadHealerService = mock(LoadHealerService.class);

        SecurityService securityService = mock(SecurityService.class);
        when(securityService.currentUsername()).thenReturn("user");

        RepositoryService repositoryService = mock(RepositoryService.class);
        when(repositoryService.repoExists(anyString())).thenReturn(true);

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(LoadHealerService.class)).thenReturn(loadHealerService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(context.beanForType(BuildFacade.class)).thenReturn(buildFacade);

        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(BuildFacade.class)).thenReturn(buildFacade);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);

            filter.doFilter(req, resp, chain);

            verify(requestDispatcher).forward(req, resp);
        }
    }

    @Test
    void execute_regularRequestCallsFilterChain() throws ServletException, IOException {
        RepoFilter filter = spy(RepoFilter.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getServletPath()).thenReturn("/artifactory/api/regular");
        when(req.getMethod()).thenReturn("GET");
        when(req.getRequestURL()).thenReturn(new StringBuffer("http://localhost/artifactory/api/regular"));

        try (MockedStatic<RequestUtils> requestUtils = mockStatic(RequestUtils.class)) {
            requestUtils.when(() -> RequestUtils.analyzeRepoRequest(any())).thenReturn(NOT_A_REPO_REQUEST);

            filter.doFilter(req, resp, chain);

            verify(chain).doFilter(eq(req), eq(resp));
        }
    }

    @Test
    void doNpmAuditRequest_wrapsRequestAndCallsChain() throws ServletException, IOException {
        RepoFilter filter = spy(RepoFilter.class);
        HttpServletRequest req = mock(HttpServletRequest.class, RETURNS_DEEP_STUBS);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        String servletPath = "/artifactory/api/npm/v1/security/audits";
        when(req.getRequestURI()).thenReturn(servletPath);
        when(req.getServletPath()).thenReturn(servletPath);
        when(req.getMethod()).thenReturn("POST");

        SecurityService securityService = mock(SecurityService.class);
        when(securityService.currentUsername()).thenReturn("user");

        RepositoryService repositoryService = mock(RepositoryService.class);
        when(repositoryService.repoExists(anyString())).thenReturn(false);

        LoadHealerService loadHealerService = mock(LoadHealerService.class);

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(LoadHealerService.class)).thenReturn(loadHealerService);
        when(context.getRepositoryService()).thenReturn(repositoryService);

        try (MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
            contextHelperMock.when(ContextHelper::get).thenReturn(context);

            filter.doFilter(req, resp, chain);

            verify(chain).doFilter(any(NpmAuditRequestWrapper.class), eq(resp));
        }
    }

    private static Stream<Arguments> loadHealerTestCases() {
        return Stream.of(
                Arguments.of("nonBlockedUser", "repo-key", PARALLEL_UPLOAD_PRE_REPO_KEY, "repo-key", "put", true),
                Arguments.of("nonBlockedUser", "generic", PARALLEL_DOWNLOAD_UPLOAD_PER_PACKAGE_TYPE, "generic", "put",
                        true),
                Arguments.of("nonBlockedUser", "upload", LIMIT_ACTIVITY, "upload", "put", true),
                Arguments.of("nonBlockedUser", "generic", PARALLEL_DOWNLOAD_UPLOAD_PER_PACKAGE_TYPE, "generic", "get",
                        true),
                Arguments.of("nonBlockedUser", "download", LIMIT_ACTIVITY, "download", "get", true)
        );
    }

    @ParameterizedTest
    @MethodSource("shouldDirectoryRequestTreatAsDownloadTestCases")
    void testShouldDirectoryRequestTreatAsDownload(boolean flagEnabled, String queryString, RepoDetailsType repoType,
            RepoType packageType, boolean propagateQueryParams, boolean expected) {
        RepoFilter repoFilter = new RepoFilter();
        HttpServletRequest servletRequestMock = mock(HttpServletRequest.class);
        ArtifactoryRequest artifactoryRequestMock = mock(ArtifactoryRequest.class);
        ArtifactoryContext contextMock = mock(ArtifactoryContext.class);
        RepositoryService repoServiceMock = mock(RepositoryService.class);
        RepoConfig repoConfigMock = mock(RepoConfig.class);
        RemoteRepositoryRepoTypeConfig typeConfigMock = mock(RemoteRepositoryRepoTypeConfig.class);

        try (MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
            contextHelperMock.when(ContextHelper::get).thenReturn(contextMock);
            when(contextMock.getRepositoryService()).thenReturn(repoServiceMock);
            when(artifactoryRequestMock.getRepoKey()).thenReturn("test-repo");
            when(repoServiceMock.getRepoConfigByKey("test-repo")).thenReturn(repoConfigMock);
            when(repoConfigMock.getRepoType()).thenReturn(repoType);
            when(repoConfigMock.getPackageType()).thenReturn(packageType);
            when(repoConfigMock.getRepoTypeConfig()).thenReturn(typeConfigMock);
            when(typeConfigMock.isPropagateQueryParams()).thenReturn(propagateQueryParams);
            when(servletRequestMock.getQueryString()).thenReturn(queryString);

            setStringSystemProperty(ConstantValues.directoryRequestQueryParamsEnabled,
                    String.valueOf(flagEnabled));

            boolean result = repoFilter.shouldDirectoryRequestTreatAsDownload(servletRequestMock,
                    artifactoryRequestMock);

            assertEquals(expected, result);
        }
    }

    private static Stream<Arguments> shouldDirectoryRequestTreatAsDownloadTestCases() {
        return Stream.of(
                Arguments.of(false, "mode=json", RepoDetailsType.REMOTE, RepoType.Generic, true, false),
                Arguments.of(true, null, RepoDetailsType.REMOTE, RepoType.Generic, true, false),
                Arguments.of(true, "mode=json", RepoDetailsType.LOCAL, RepoType.Generic, true, false),
                Arguments.of(true, "mode=json", RepoDetailsType.REMOTE, RepoType.Maven, true, false),
                Arguments.of(true, "mode=json", RepoDetailsType.REMOTE, RepoType.Generic, false, false),
                Arguments.of(true, "mode=json", RepoDetailsType.REMOTE, RepoType.Generic, true, true)
        );
    }

    @Test
    void collectWhitelistedHeaders_returnsOnlyPresentWhitelistedHeadersInOrder() {
        RepoFilter filter = new RepoFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(ArtifactoryHeaders.ORIGINAL_CLIENT_URI)).thenReturn("/orig/client/uri");
        when(req.getHeader(ArtifactoryHeaders.JFROG_OVERRIDE_BASE_URL)).thenReturn(null);
        when(req.getHeader(ArtifactoryHeaders.X_FORWARDED_HOST)).thenReturn("example.com");
        when(req.getHeader(ArtifactoryHeaders.X_FORWARDED_PROTO)).thenReturn("   ");
        // A non-whitelisted header must never leak into the trace output
        when(req.getHeader("Authorization")).thenReturn("Basic c2VjcmV0");

        Map<String, String> headers = filter.collectWhitelistedHeaders(req);

        // Only present, non-blank, whitelisted headers are kept, in whitelist order
        assertEquals(2, headers.size());
        assertEquals("/orig/client/uri", headers.get(ArtifactoryHeaders.ORIGINAL_CLIENT_URI));
        assertEquals("example.com", headers.get(ArtifactoryHeaders.X_FORWARDED_HOST));
        assertFalse(headers.containsKey(ArtifactoryHeaders.JFROG_OVERRIDE_BASE_URL));
        assertFalse(headers.containsKey(ArtifactoryHeaders.X_FORWARDED_PROTO));
        assertFalse(headers.containsKey("Authorization"));
        assertEquals(List.of(ArtifactoryHeaders.ORIGINAL_CLIENT_URI, ArtifactoryHeaders.X_FORWARDED_HOST),
                new ArrayList<>(headers.keySet()));
    }

    @Test
    void collectWhitelistedHeaders_excludesEmptyStringHeaderValue() {
        RepoFilter filter = new RepoFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        // Some servlet containers return "" rather than null for an absent header; it must still be excluded
        when(req.getHeader(ArtifactoryHeaders.JFROG_OVERRIDE_BASE_URL)).thenReturn("");
        when(req.getHeader(ArtifactoryHeaders.X_FORWARDED_HOST)).thenReturn("example.com");

        Map<String, String> headers = filter.collectWhitelistedHeaders(req);

        assertFalse(headers.containsKey(ArtifactoryHeaders.JFROG_OVERRIDE_BASE_URL));
        assertEquals("example.com", headers.get(ArtifactoryHeaders.X_FORWARDED_HOST));
        assertEquals(1, headers.size());
    }

    @Test
    void collectWhitelistedHeaders_returnsEmptyMapWhenNonePresent() {
        RepoFilter filter = new RepoFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);

        Map<String, String> headers = filter.collectWhitelistedHeaders(req);

        assertTrue(headers.isEmpty());
    }

    @Test
    void newTraceLoggingResponse_wrapsResponseWithWhitelistedHeaders() {
        RepoFilter filter = new RepoFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(ArtifactoryHeaders.X_FORWARDED_HOST)).thenReturn("example.com");
        ArtifactoryResponse delegate = mock(ArtifactoryResponse.class);

        ArtifactoryResponse wrapped = filter.newTraceLoggingResponse(req, delegate);

        assertInstanceOf(TraceLoggingResponse.class, wrapped);
    }
}

