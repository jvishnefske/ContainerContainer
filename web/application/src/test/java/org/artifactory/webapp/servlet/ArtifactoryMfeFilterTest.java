package org.artifactory.webapp.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.web.FeatureFlagService;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.resource.mfe.MfeName;
import org.artifactory.rest.resource.mfe.MfeProjectConfig;
import org.artifactory.rest.resource.mfe.MfeProjectRegistry;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.resource.mfe.server.MFEFileServer;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ArtifactoryMfeFilterTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void pathTraversal() throws IOException {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();

        String traversedPath = "/artifactory/webapp/../../something";
        String properPath = "/artifactory/webapp/js";
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn(traversedPath);
        assertTrue(artifactoryMfeFilter.assertPathTraversal(request));

        when(request.getRequestURI()).thenReturn(properPath);
        assertFalse(artifactoryMfeFilter.assertPathTraversal(request));
    }

    @Test
    void shouldSkipCurrentFilterTest() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        setupMockContext(artifactoryMfeFilter);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/webapp/should_be_skipped");
        assertTrue(artifactoryMfeFilter.shouldSkipCurrentFilter(request));
    }

    @Test
    void shouldNotSkipCurrentFilterForJsTest() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        setupMockContext(artifactoryMfeFilter);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/artifactory/webapp/js/app.umd.js");
        assertFalse(artifactoryMfeFilter.shouldSkipCurrentFilter(request));
    }

    @Test
    void shouldNotSkipCurrentFilterForImgTest() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        setupMockContext(artifactoryMfeFilter);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/artifactory/webapp/img/picture.jpeg");
        assertFalse(artifactoryMfeFilter.shouldSkipCurrentFilter(request));
    }

    @Test
    void shouldNotSkipCurrentFilterForRtuiJsTest() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        setupMockContext(artifactoryMfeFilter);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/artifactory/webapp/rtui/js/app.esm.js");
        assertFalse(artifactoryMfeFilter.shouldSkipCurrentFilter(request));
    }

    @Test
    void shouldNotSkipCurrentFilterForRtuiImgTest() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        setupMockContext(artifactoryMfeFilter);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/artifactory/webapp/rtui/img/picture.jpeg");
        assertFalse(artifactoryMfeFilter.shouldSkipCurrentFilter(request));
    }

    @Test
    void shouldNotSkipCurrentFilterForAssetsTest() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        setupMockContext(artifactoryMfeFilter);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/artifactory/webapp/assets/chunk.js");
        assertFalse(artifactoryMfeFilter.shouldSkipCurrentFilter(request));
    }

    @Test
    void shouldNotSkipCurrentFilterForRtuiAssetsTest() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        setupMockContext(artifactoryMfeFilter);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/artifactory/webapp/rtui/assets/index.js");
        assertFalse(artifactoryMfeFilter.shouldSkipCurrentFilter(request));
    }

    @Test
    void shouldNotSkipCurrentFilterForRtuiMigratedRoutesTest() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        setupMockContext(artifactoryMfeFilter);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/artifactory/webapp/rtui/migrated-routes.json");
        assertFalse(artifactoryMfeFilter.shouldSkipCurrentFilter(request));
    }

    @Test
    void shouldNotSkipCurrentFilterForRetentionMigratedRoutesTest() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        setupMockContext(artifactoryMfeFilter);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/artifactory/webapp/retention/migrated-routes.json");
        assertFalse(artifactoryMfeFilter.shouldSkipCurrentFilter(request));
    }

    @Test
    void shouldSkipCurrentFilterForMigratedRoutesWithSuffixTest() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        setupMockContext(artifactoryMfeFilter);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/artifactory/webapp/rtui/migrated-routes.json.backup");
        assertTrue(artifactoryMfeFilter.shouldSkipCurrentFilter(request));
    }

    @Test
    void doFilterTestWhenShouldExitWithPathTraversal() throws IOException, ServletException {
        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(response.getWriter()).thenReturn(mock(PrintWriter.class));
        when(request.getServletPath()).thenReturn("/webapp/js/../../app.js");
        when(request.getRequestURI()).thenReturn("/artifactory/webapp/js/../../app.js");

        try {
            artifactoryMfeFilter.doFilter(request, response, filterChain);
        } finally {
            verify(artifactoryMfeFilter).doFilter(request, response, filterChain);
            verify(artifactoryMfeFilter).assertPathTraversal(any());
            verify(artifactoryMfeFilter).sendNotFoundResponse(any());
            verifyNoMoreInteractions(artifactoryMfeFilter);
        }
    }

    @Test
    void doFilterTestWhenShouldBeSkipped() throws IOException, ServletException {
        // given
        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        setupMockContext(artifactoryMfeFilter);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(response.getWriter()).thenReturn(mock(PrintWriter.class));
        when(request.getServletPath()).thenReturn("/webapp/should_be_skipped");
        when(request.getRequestURI()).thenReturn("/artifactory/webapp/should_be_skipped");

        artifactoryMfeFilter.doFilter(request, response, filterChain);
        
        verify(artifactoryMfeFilter).doFilter(request, response, filterChain);
        verify(artifactoryMfeFilter).assertPathTraversal(any());
        verify(artifactoryMfeFilter).shouldSkipCurrentFilter(any());
    }

    @Test
    void doFilterTestWhenShouldNotServe() throws IOException, ServletException {

        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        setupMockContext(artifactoryMfeFilter);
        MFEFileServer mfeServer = mock(MFEFileServer.class);
        artifactoryMfeFilter.mfeFileServer = mfeServer;
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        doThrow(new NotFoundException("Failed to serve MFE file: /webapp/js/app.not.umd.js"))
                .when(mfeServer).serveFile(any(), anyString(), anyString());
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));
        when(request.getRequestURI()).thenReturn("/artifactory/webapp/js/app.not.umd.js");
        when(request.getServletPath()).thenReturn("/webapp/js/app.not.umd.js");

        artifactoryMfeFilter.doFilter(request, response, filterChain);
        
        verify(artifactoryMfeFilter).doFilter(request, response, filterChain);
        verify(artifactoryMfeFilter).assertPathTraversal(any());
        verify(artifactoryMfeFilter).shouldSkipCurrentFilter(any());
        verify(artifactoryMfeFilter).sendNotFoundResponse(any());
    }

    @Test
    void testGetManifestWithFeatureFlags() {
        String resourcePath = "/artifactory/webapp/manifest.js";
        String featureFlags = "{\"treeBrowserStreamEnabled\":true}";
        String manifest = "\"THIS_IS_A_PLACEHOLDER_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"";

        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        MFEFileServer mfeServer = mock(MFEFileServer.class);
        artifactoryMfeFilter.mfeFileServer = mfeServer;

        FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
        when(featureFlagService.getAllFeatureFlags()).thenReturn(featureFlags);
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(FeatureFlagService.class)).thenReturn(featureFlagService);
        when(mfeServer.readFileContent(anyString())).thenReturn(manifest);
        String result = artifactoryMfeFilter.getManifestWithFeatureFlags(resourcePath);
        assertEquals(featureFlags, result);
    }

    @Test
    void testManifestFileResponse() throws IOException, ServletException {
        String resourcePath = "/manifest.umd.js";
        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        setupMockContext(artifactoryMfeFilter);
        artifactoryMfeFilter.mfeFileServer = mock(MFEFileServer.class);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = spy(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);
        when(request.getServletPath()).thenReturn("/webapp" + resourcePath);
        when(request.getRequestURI()).thenReturn("/artifactory/webapp/js" + resourcePath);

        doReturn("{}").when(artifactoryMfeFilter).getManifestWithFeatureFlags(resourcePath);

        try (MockedStatic<ResourceUtils> mockedResourceUtils = mockStatic(ResourceUtils.class)) {
            mockedResourceUtils.when(() -> ResourceUtils.resourceExists(eq(resourcePath))).thenReturn(true);

            artifactoryMfeFilter.doFilter(request, response, filterChain);
        }
        verify(outputStream).write(any(byte[].class));
    }

    @Test
    void testGetStaticFileWithMigratedRoutesList_projectWithMigratedRoutesReturnsAsIs() {
        String staticFileContent = "This is a test file content with a placeholder: " +
                "\"PLACEHOLDER_FOR_LIST_OF_ROUTES_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"";
        String resourcePath = "/rtui/manifest.esm.js";

        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        artifactoryMfeFilter.mfeFileServer = mock(MFEFileServer.class);

        // Setup context and registry
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MfeProjectRegistry registry = mock(MfeProjectRegistry.class);
        
        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(MfeProjectRegistry.class)).thenReturn(registry);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "mfeProjectRegistry", registry);

        // Setup rtui project WITH migrated routes - should return as-is
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .hasMigratedRoutes(true)
                .controlledRolloutEnabled(ConstantValues.rtuiMfeControlledRolloutEnabled)
                .build();
        when(registry.findProjectForResource(resourcePath)).thenReturn(rtuiProject);

        String result = artifactoryMfeFilter.getStaticFileWithMigratedRoutesList(staticFileContent, resourcePath);
        // Should return unchanged because rtui has its own migrated routes
        assertEquals(staticFileContent, result);
    }

    @Test
    void testGetStaticFileWithMigratedRoutesList_projectWithoutMigratedRoutesAndNoOtherProjects() {
        String staticFileContent = "This is a test file content with a placeholder: " +
                "\"PLACEHOLDER_FOR_LIST_OF_ROUTES_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"";
        String resourcePath = "/manifest.js";  // Core project without migrated routes

        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        artifactoryMfeFilter.mfeFileServer = mock(MFEFileServer.class);

        // Setup context and registry
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MfeProjectRegistry registry = mock(MfeProjectRegistry.class);
        
        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(MfeProjectRegistry.class)).thenReturn(registry);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "mfeProjectRegistry", registry);

        // Setup core project WITHOUT migrated routes
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .hasMigratedRoutes(false)
                .build();
        when(registry.findProjectForResource(resourcePath)).thenReturn(coreProject);
        when(registry.getAllProjects()).thenReturn(java.util.Collections.singletonList(coreProject));

        String result = artifactoryMfeFilter.getStaticFileWithMigratedRoutesList(staticFileContent, resourcePath);

        // Should return unchanged because no other projects have migrated routes
        assertEquals(staticFileContent, result);
    }


    @Test
    void testGetStaticFileWithMigratedRoutesList_projectWithoutMigratedRoutesButFileNotFound() {
        String staticFileContent = "This is a test file content with a placeholder: " +
                "\"PLACEHOLDER_FOR_LIST_OF_ROUTES_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"";
        String resourcePath = "/manifest.js";  // Core project without migrated routes

        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        artifactoryMfeFilter.mfeFileServer = mock(MFEFileServer.class);

        // Setup context and registry
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MfeProjectRegistry registry = mock(MfeProjectRegistry.class);
        
        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(MfeProjectRegistry.class)).thenReturn(registry);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "mfeProjectRegistry", registry);

        // Setup core project WITHOUT migrated routes
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .hasMigratedRoutes(false)
                .build();
        
        // Setup rtui project WITH migrated routes but file not found
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .hasMigratedRoutes(true)
                .controlledRolloutEnabled(ConstantValues.rtuiMfeControlledRolloutEnabled)
                .build();
        
        when(registry.findProjectForResource(resourcePath)).thenReturn(coreProject);
        when(registry.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject, rtuiProject));

        when(artifactoryMfeFilter.mfeFileServer.readFileContent("/rtui/migrated-routes.json"))
                .thenThrow(new NotFoundException("File not found"));

        String result = artifactoryMfeFilter.getStaticFileWithMigratedRoutesList(staticFileContent, resourcePath);

        // Should return unchanged because file was not found
        assertEquals(staticFileContent, result);
    }


    @Test
    void testGetStaticFileWithMigratedRoutesList_placeholderNotFoundButReplacementAttempted() {
        String staticFileContent = "This is a test file content without any placeholder";
        String expectedContent = staticFileContent;
        String resourcePath = "/manifest.js";  // Core project without migrated routes

        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        artifactoryMfeFilter.mfeFileServer = mock(MFEFileServer.class);

        // Setup context and registry
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MfeProjectRegistry registry = mock(MfeProjectRegistry.class);
        
        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(MfeProjectRegistry.class)).thenReturn(registry);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "mfeProjectRegistry", registry);

        // Setup core project WITHOUT migrated routes
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .hasMigratedRoutes(false)
                .build();
        
        // Setup rtui project WITH migrated routes
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .hasMigratedRoutes(true)
                .controlledRolloutEnabled(ConstantValues.rtuiMfeControlledRolloutEnabled)
                .build();
        
        when(registry.findProjectForResource(resourcePath)).thenReturn(coreProject);
        when(registry.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject, rtuiProject));

        when(artifactoryMfeFilter.mfeFileServer.readFileContent("/rtui/migrated-routes.json"))
                .thenReturn("{\"routesList\": [\"route1\", \"route2\"]}");

        String result = artifactoryMfeFilter.getStaticFileWithMigratedRoutesList(staticFileContent, resourcePath);

        // Should return unchanged because placeholder not found (replace does nothing)
        assertEquals(expectedContent, result);
    }

    @Test
    void testGetStaticFileWithMigratedRoutesList_malformedJson() {
        String staticFileContent = "This is a test file content with a placeholder: " +
                "\"PLACEHOLDER_FOR_LIST_OF_ROUTES_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"";
        String resourcePath = "/manifest.js";  // Core project without migrated routes

        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        artifactoryMfeFilter.mfeFileServer = mock(MFEFileServer.class);

        // Setup context and registry
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MfeProjectRegistry registry = mock(MfeProjectRegistry.class);
        
        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(MfeProjectRegistry.class)).thenReturn(registry);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "mfeProjectRegistry", registry);

        // Setup core project WITHOUT migrated routes
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .hasMigratedRoutes(false)
                .build();
        
        // Setup rtui project WITH migrated routes but malformed JSON
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .hasMigratedRoutes(true)
                .controlledRolloutEnabled(ConstantValues.rtuiMfeControlledRolloutEnabled)
                .build();
        
        when(registry.findProjectForResource(resourcePath)).thenReturn(coreProject);
        when(registry.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject, rtuiProject));

        when(artifactoryMfeFilter.mfeFileServer.readFileContent("/rtui/migrated-routes.json"))
                .thenReturn("{ routesList: [route1, route2] }");

        String result = artifactoryMfeFilter.getStaticFileWithMigratedRoutesList(staticFileContent, resourcePath);

        assertEquals(staticFileContent, result);  // Fallback to original content
    }

    @Test
    void testGetStaticFileWithMigratedRoutesList_projectWithoutMigratedRoutesCollectsFromAll() {
        String staticFileContent = "This is a test file content with a placeholder: " +
                "\"PLACEHOLDER_FOR_LIST_OF_ROUTES_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"";
        String expectedContent = "This is a test file content with a placeholder: " + 
                "[\"route1\",\"route2\"]";
        String resourcePath = "/manifest.js";  // Core project without migrated routes

        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        artifactoryMfeFilter.mfeFileServer = mock(MFEFileServer.class);

        // Setup context and registry
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MfeProjectRegistry registry = mock(MfeProjectRegistry.class);
        
        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(MfeProjectRegistry.class)).thenReturn(registry);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "mfeProjectRegistry", registry);

        // Setup core project WITHOUT migrated routes
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .hasMigratedRoutes(false)
                .build();
        
        // Setup rtui project WITH migrated routes
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .hasMigratedRoutes(true)
                .controlledRolloutEnabled(ConstantValues.rtuiMfeControlledRolloutEnabled)
                .build();

        when(registry.findProjectForResource(resourcePath)).thenReturn(coreProject);
        when(registry.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject, rtuiProject));

        // Mock reading from rtui project with migrated routes
        when(artifactoryMfeFilter.mfeFileServer.readFileContent("/rtui/migrated-routes.json"))
                .thenReturn("{\"routesList\": [\"route1\", \"route2\"]}");

        String result = artifactoryMfeFilter.getStaticFileWithMigratedRoutesList(staticFileContent, resourcePath);
        
        // Should contain routes from rtui project
        assertEquals(expectedContent, result);
    }

    @Test
    void testGetStaticFileWithMigratedRoutesList_rolloutEnabled_excludesNothingFromRolloutPhase() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.rtuiMfeControlledRolloutEnabled,
                Boolean.TRUE.toString());

        String staticFileContent = "content: " +
                "\"PLACEHOLDER_FOR_LIST_OF_ROUTES_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"";
        String expectedContent = "content: " + "[\"/route1\",\"/route2\"]";

        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        artifactoryMfeFilter.mfeFileServer = mock(MFEFileServer.class);

        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MfeProjectRegistry registry = mock(MfeProjectRegistry.class);

        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(MfeProjectRegistry.class)).thenReturn(registry);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "mfeProjectRegistry", registry);

        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .hasMigratedRoutes(false)
                .build();
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .hasMigratedRoutes(true)
                .controlledRolloutEnabled(ConstantValues.rtuiMfeControlledRolloutEnabled)
                .build();

        when(registry.findProjectForResource("/manifest.js")).thenReturn(coreProject);
        when(registry.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject, rtuiProject));

        when(artifactoryMfeFilter.mfeFileServer.readFileContent("/rtui/migrated-routes.json"))
                .thenReturn(
                        "{\"routesList\": [\"/route1\", \"/route2\"], \"rolloutPhaseList\": [\"/route1\"]}");

        String result = artifactoryMfeFilter.getStaticFileWithMigratedRoutesList(staticFileContent, "/manifest.js");
        assertEquals(expectedContent, result);
    }

    @Test
    void testGetStaticFileWithMigratedRoutesList_rolloutDisabled_excludesRoutesInRolloutPhase() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.rtuiMfeControlledRolloutEnabled,
                Boolean.FALSE.toString());

        String staticFileContent = "content: " +
                "\"PLACEHOLDER_FOR_LIST_OF_ROUTES_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"";
        String expectedContent = "content: " + "[\"/route2\"]";

        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        artifactoryMfeFilter.mfeFileServer = mock(MFEFileServer.class);

        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MfeProjectRegistry registry = mock(MfeProjectRegistry.class);

        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(MfeProjectRegistry.class)).thenReturn(registry);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "mfeProjectRegistry", registry);

        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .hasMigratedRoutes(false)
                .build();
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .hasMigratedRoutes(true)
                .controlledRolloutEnabled(ConstantValues.rtuiMfeControlledRolloutEnabled)
                .build();

        when(registry.findProjectForResource("/manifest.js")).thenReturn(coreProject);
        when(registry.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject, rtuiProject));

        when(artifactoryMfeFilter.mfeFileServer.readFileContent("/rtui/migrated-routes.json"))
                .thenReturn(
                        "{\"routesList\": [\"/route1\", \"/route2\"], \"rolloutPhaseList\": [\"/route1\"]}");

        String result = artifactoryMfeFilter.getStaticFileWithMigratedRoutesList(staticFileContent, "/manifest.js");
        assertEquals(expectedContent, result);
    }

    @Test
    void testGetStaticFileWithMigratedRoutesList_rolloutDisabled_emptyRolloutPhase_unloadsAllRoutes() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.rtuiMfeControlledRolloutEnabled,
                Boolean.FALSE.toString());

        String staticFileContent = "content: " +
                "\"PLACEHOLDER_FOR_LIST_OF_ROUTES_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"";
        String expectedContent = "content: " + "[\"/route1\",\"/route2\"]";

        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        artifactoryMfeFilter.mfeFileServer = mock(MFEFileServer.class);

        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MfeProjectRegistry registry = mock(MfeProjectRegistry.class);

        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(MfeProjectRegistry.class)).thenReturn(registry);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "mfeProjectRegistry", registry);

        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .hasMigratedRoutes(false)
                .build();
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .hasMigratedRoutes(true)
                .controlledRolloutEnabled(ConstantValues.rtuiMfeControlledRolloutEnabled)
                .build();

        when(registry.findProjectForResource("/manifest.js")).thenReturn(coreProject);
        when(registry.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject, rtuiProject));

        when(artifactoryMfeFilter.mfeFileServer.readFileContent("/rtui/migrated-routes.json")).thenReturn(
                "{\"routesList\": [\"/route1\", \"/route2\"], \"rolloutPhaseList\": []}");

        String result = artifactoryMfeFilter.getStaticFileWithMigratedRoutesList(staticFileContent, "/manifest.js");
        assertEquals(expectedContent, result);
    }

    @Test
    void substituteControlledRolloutFlagPlaceholder_writesTrueForResolvedMfe() {
        getOrCreateArtifactoryHomeStub()
                .setProperty(ConstantValues.rtuiMfeControlledRolloutEnabled, "true");

        String resourcePath = "/artifactory/webapp/rtui/manifest.esm.js";

        ArtifactoryMfeFilter filter = newFilterWithRegistry(registry -> {
            MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                    .mfeName(MfeName.RTUI)
                    .basePath("/rtui")
                    .hasMigratedRoutes(true)
                    .controlledRolloutEnabled(ConstantValues.rtuiMfeControlledRolloutEnabled)
                    .build();
            when(registry.findProjectForResource(resourcePath)).thenReturn(rtuiProject);
        });

        String content = "{\"rollout\":" +
                "\"PLACEHOLDER_FOR_CONTROLLED_ROLLOUT_FLAG_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"" +
                "}";

        String outgoing = filter.substituteControlledRolloutFlagPlaceholder(content, resourcePath);

        assertEquals("{\"rollout\":true}", outgoing);
        assertFalse(outgoing.contains("PLACEHOLDER_FOR_CONTROLLED_ROLLOUT_FLAG_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE"));
    }

    @Test
    void substituteControlledRolloutFlagPlaceholder_writesFalseForResolvedMfe() {
        getOrCreateArtifactoryHomeStub()
                .setProperty(ConstantValues.rtuiMfeControlledRolloutEnabled, "false");

        ArtifactoryMfeFilter filter = newFilterWithRegistry(registry -> {
            MfeProjectConfig coreProject = MfeProjectConfig.builder()
                    .mfeName(MfeName.CORE)
                    .basePath("")
                    .hasMigratedRoutes(false)
                    .build();
            MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                    .mfeName(MfeName.RTUI)
                    .basePath("/rtui")
                    .hasMigratedRoutes(true)
                    .controlledRolloutEnabled(ConstantValues.rtuiMfeControlledRolloutEnabled)
                    .build();
            when(registry.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject, rtuiProject));
        });

        String content = "{\"rollout\":" +
                "\"PLACEHOLDER_FOR_CONTROLLED_ROLLOUT_FLAG_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"" +
                "}";

        String outgoing = filter.substituteControlledRolloutFlagPlaceholder(content, "/rtui/manifest.esm.js");

        assertEquals("{\"rollout\":false}", outgoing);
    }

    @Test
    void substituteControlledRolloutFlagPlaceholder_defaultsToFalseWhenNoProjectResolved() {
        ArtifactoryMfeFilter filter = newFilterWithRegistry(registry ->
                when(registry.getAllProjects()).thenReturn(java.util.Collections.emptyList()));

        String content = "{\"rollout\":" +
                "\"PLACEHOLDER_FOR_CONTROLLED_ROLLOUT_FLAG_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"" +
                "}";

        String outgoing = filter.substituteControlledRolloutFlagPlaceholder(content, "/unknown/manifest.esm.js");

        assertEquals("{\"rollout\":false}", outgoing);
    }

    private ArtifactoryMfeFilter newFilterWithRegistry(java.util.function.Consumer<MfeProjectRegistry> stubber) {
        ArtifactoryMfeFilter filter = new ArtifactoryMfeFilter();
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MfeProjectRegistry registry = mock(MfeProjectRegistry.class);

        ReflectionTestUtils.setField(filter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(MfeProjectRegistry.class)).thenReturn(registry);
        ReflectionTestUtils.setField(filter, "mfeProjectRegistry", registry);

        stubber.accept(registry);
        return filter;
    }

    @Test
    void testGetManifestWithFeatureFlags_fileNotFound() {
        String resourcePath = "/artifactory/webapp/manifest.js";
        
        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        MFEFileServer mfeServer = mock(MFEFileServer.class);
        artifactoryMfeFilter.mfeFileServer = mfeServer;
        
        FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
        when(featureFlagService.getAllFeatureFlags()).thenReturn("{}");
        
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(FeatureFlagService.class)).thenReturn(featureFlagService);
        
        when(mfeServer.readFileContent(anyString())).thenThrow(new NotFoundException("File not found"));
        
        try {
            artifactoryMfeFilter.getManifestWithFeatureFlags(resourcePath);
        } catch (NotFoundException e) {
            assertEquals("File not found", e.getMessage());
        }
    }
    
    @Test
    void testGetManifestWithFeatureFlags_emptyFile() {
        String resourcePath = "/artifactory/webapp/manifest.js";
        String featureFlags = "{\"treeBrowserStreamEnabled\":true}";
        
        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        MFEFileServer mfeServer = mock(MFEFileServer.class);
        artifactoryMfeFilter.mfeFileServer = mfeServer;
        
        FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
        when(featureFlagService.getAllFeatureFlags()).thenReturn(featureFlags);
        
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(FeatureFlagService.class)).thenReturn(featureFlagService);
        
        when(mfeServer.readFileContent(anyString())).thenReturn("");
        
        String result = artifactoryMfeFilter.getManifestWithFeatureFlags(resourcePath);
        
        assertEquals("", result);
    }
    
    @Test
    void testGetManifestWithFeatureFlags_nothingToReplace() {
        String resourcePath = "/artifactory/webapp/manifest.js";
        String featureFlags = "{\"treeBrowserStreamEnabled\":true}";
        String manifestContent = "var config = { apiUrl: '/api' }; // No placeholder here";
        
        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        MFEFileServer mfeServer = mock(MFEFileServer.class);
        artifactoryMfeFilter.mfeFileServer = mfeServer;
        
        FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
        when(featureFlagService.getAllFeatureFlags()).thenReturn(featureFlags);
        
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ReflectionTestUtils.setField(artifactoryMfeFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(FeatureFlagService.class)).thenReturn(featureFlagService);
        
        when(mfeServer.readFileContent(anyString())).thenReturn(manifestContent);
        
        String result = artifactoryMfeFilter.getManifestWithFeatureFlags(resourcePath);
        
        assertEquals(manifestContent, result);
    }

    @Test
    void doFilterTestWhenIOExceptionOnGetOutputStream() throws IOException, ServletException {
        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        setupMockContext(artifactoryMfeFilter);
        MFEFileServer mfeServer = mock(MFEFileServer.class);
        artifactoryMfeFilter.mfeFileServer = mfeServer;
        
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        
        when(request.getServletPath()).thenReturn("/webapp/js/app.umd.js");
        when(request.getRequestURI()).thenReturn("/artifactory/webapp/js/app.umd.js");
        
        when(response.getOutputStream()).thenThrow(new IOException("Cannot get output stream"));
        
        doReturn("test content").when(artifactoryMfeFilter).getManifestWithFeatureFlags(anyString());
        doReturn("test content").when(artifactoryMfeFilter).getStaticFileWithMigratedRoutesList(anyString(),
                anyString());
        
        try {
            artifactoryMfeFilter.doFilter(request, response, filterChain);
        } catch (IOException e) {
            assertEquals("Cannot get output stream", e.getMessage());
        }
    }
    
    @Test
    void doFilterTestWhenMfeServerThrowsException() throws IOException, ServletException {
        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        setupMockContext(artifactoryMfeFilter);
        MFEFileServer mfeServer = mock(MFEFileServer.class);
        artifactoryMfeFilter.mfeFileServer = mfeServer;
        
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        
        when(request.getServletPath()).thenReturn("/webapp/css/styles.css");
        when(request.getRequestURI()).thenReturn("/artifactory/webapp/css/styles.css");
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));
        
        doThrow(new RuntimeException("Unexpected server error")).when(mfeServer)
                .serveFile(any(), anyString(), anyString());
        
        try {
            artifactoryMfeFilter.doFilter(request, response, filterChain);
        } catch (RuntimeException e) {
            assertEquals("Unexpected server error", e.getMessage());
        }
    }
    
    @Test
    void doFilterTestWhenResponseWriterIsNull() throws IOException, ServletException {
        ArtifactoryMfeFilter artifactoryMfeFilter = spy(new ArtifactoryMfeFilter());
        
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        
        when(request.getServletPath()).thenReturn("/webapp/js/../../app.js");
        when(request.getRequestURI()).thenReturn("/artifactory/webapp/js/../../app.js");
        
        when(response.getWriter()).thenReturn(null);
        
        try {
            artifactoryMfeFilter.doFilter(request, response, filterChain);
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException || e instanceof IOException);
        }
    }


    public static Stream<Arguments> getContentTypeTestProvider() {
        return Stream.of(
                Arguments.of("app.umd.js", "js", "application/javascript"),
                Arguments.of("index.html", "html", "text/html"),
                Arguments.of("index", null, null),
                Arguments.of("index.", ".", null),
                // Additional negative test cases
                Arguments.of("", "", null),
                Arguments.of("file", "", null),
                Arguments.of(".hiddenfile", "", null),
                Arguments.of("file.unknown", "unknown", null),
                Arguments.of("file.UNKNOWN", "UNKNOWN", null),
                Arguments.of("file..js", ".js", "text/javascript"),
                Arguments.of("file.js.backup", "backup", null),
                Arguments.of("file with spaces.js", "js", "application/javascript"),
                Arguments.of("file@#$%.js", "js", "application/javascript"),
                Arguments.of("very.long.filename.with.many.dots.js", "js", "application/javascript"),
                Arguments.of("noextension", null, null)
        );
    }

    @ParameterizedTest
    @MethodSource("getContentTypeTestProvider")
    void testGetContentType(String filename, String fileExtension, String expectedContentType) {
        String result = ArtifactoryMfeFilter.getContentType(fileExtension, filename);
        assertEquals(expectedContentType, result);
    }
    
    @Test
    void pathTraversalWithNullRequest() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        
        try {
            artifactoryMfeFilter.assertPathTraversal(null);
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException || e instanceof IOException);
        }
    }
    
    @Test
    void pathTraversalWithEmptyPath() throws IOException {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        
        when(request.getRequestURI()).thenReturn("");
        boolean result = artifactoryMfeFilter.assertPathTraversal(request);
        assertFalse(result);
    }
    
    @Test
    void pathTraversalWithRootPath() throws IOException {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        
        when(request.getRequestURI()).thenReturn("/");
        boolean result = artifactoryMfeFilter.assertPathTraversal(request);
        assertFalse(result); 
    }
    
    @Test
    void shouldSkipCurrentFilterWithNullRequest() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        
        try {
            artifactoryMfeFilter.shouldSkipCurrentFilter(null);
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException);
        }
    }
    
    @Test
    void shouldSkipCurrentFilterWithEmptyURI() {
        ArtifactoryMfeFilter artifactoryMfeFilter = new ArtifactoryMfeFilter();
        setupMockContext(artifactoryMfeFilter);
        HttpServletRequest request = mock(HttpServletRequest.class);
        
        when(request.getRequestURI()).thenReturn("");
        boolean result = artifactoryMfeFilter.shouldSkipCurrentFilter(request);
        assertTrue(result);
    }
    
    /**
     * Helper method to setup mock ArtifactoryContext and MfeProjectRegistry for tests.
     */
    private void setupMockContext(ArtifactoryMfeFilter filter) {
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MfeProjectRegistry registry = mock(MfeProjectRegistry.class);
        
        ReflectionTestUtils.setField(filter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(MfeProjectRegistry.class)).thenReturn(registry);
        ReflectionTestUtils.setField(filter, "mfeProjectRegistry", registry);
        
        // Mock basic project configs for the registry
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .hasMigratedRoutes(false)
                .build();
        
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .hasMigratedRoutes(true)
                .controlledRolloutEnabled(ConstantValues.rtuiMfeControlledRolloutEnabled)
                .build();
        
        MfeProjectConfig retentionProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RETENTION)
                .basePath("/retention")
                .hasMigratedRoutes(true)
                .controlledRolloutEnabled(ConstantValues.retentionMfeControlledRolloutEnabled)
                .build();
        
        when(registry.findProjectForResource(any())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            if (path != null && path.startsWith("/rtui")) {
                return rtuiProject;
            }
            if (path != null && path.startsWith("/retention")) {
                return retentionProject;
            }
            return coreProject;
        });
        
        when(registry.getAllProjects()).thenReturn(
                java.util.Arrays.asList(coreProject, rtuiProject, retentionProject));
    }
}