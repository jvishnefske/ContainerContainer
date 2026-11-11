package org.artifactory.webapp.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.security.SecurityService;
import org.artifactory.exception.LoadHealerException;
import org.artifactory.throttling.lh.service.LoadHealerRunnable;
import org.artifactory.throttling.lh.service.LoadHealerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
class LoadHealerFilterTest {

    @Mock
    private LoadHealerService loadHealerService;

    @Mock
    private SecurityService securityService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private LoadHealerFilter filter;

    @BeforeEach
    void setUp() throws ServletException {
        filter = new LoadHealerFilter();

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(LoadHealerService.class)).thenReturn(loadHealerService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);

        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);

        try (MockedStatic<RequestUtils> requestUtils = mockStatic(RequestUtils.class)) {
            requestUtils.when(() -> RequestUtils.getArtifactoryContext(servletContext)).thenReturn(context);
            filter.initLater(filterConfig);
        }
    }

    @Test
    void doFilterShouldSkipWhenLoadHealerDisabled() throws IOException, ServletException {
        // Arrange
        when(loadHealerService.isEnabled()).thenReturn(false);

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(loadHealerService, never()).executeWithActivity(any(), anyString(), any());
    }

    @Test
    void doFilterShouldExecuteWithUserActivityWhenEnabled() throws IOException, ServletException {
        // Arrange
        when(loadHealerService.isEnabled()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn("testUser");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        try (MockedStatic<RequestUtils> requestUtils = mockStatic(RequestUtils.class)) {
            requestUtils.when(() -> RequestUtils.getServletPathFromRequest(request)).thenReturn("/ui/home");

            doAnswer(invocation -> {
                invocation.getArgument(2, LoadHealerRunnable.class).run();
                return null;
            }).when(loadHealerService).executeWithActivity(any(), eq("testUser"), any());

            // Act
            filter.doFilter(request, response, (req, resp) -> chainCalled.set(true));

            // Assert
            assertTrue(chainCalled.get());
        }
    }

    @Test
    void doFilterShouldExecuteNestedActivitiesForApiPath() throws IOException, ServletException {
        // Arrange
        when(loadHealerService.isEnabled()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn("testUser");
        AtomicInteger activityCount = new AtomicInteger(0);

        try (MockedStatic<RequestUtils> requestUtils = mockStatic(RequestUtils.class)) {
            requestUtils.when(() -> RequestUtils.getServletPathFromRequest(request))
                    .thenReturn("/api/docker/v2/repo");

            doAnswer(invocation -> {
                activityCount.incrementAndGet();
                invocation.getArgument(2, LoadHealerRunnable.class).run();
                return null;
            }).when(loadHealerService).executeWithActivity(any(), anyString(), any());

            // Act
            filter.doFilter(request, response, filterChain);

            // Assert - should have 3 nested activities: user, module, endpoint
            assertEquals(3, activityCount.get());
        }
    }

    @Test
    void doFilterShouldExecuteTwoActivitiesWhenNoEndpoint() throws IOException, ServletException {
        // Arrange
        when(loadHealerService.isEnabled()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn("testUser");
        AtomicInteger activityCount = new AtomicInteger(0);

        try (MockedStatic<RequestUtils> requestUtils = mockStatic(RequestUtils.class)) {
            // Path with only module, no endpoint (e.g., /api/docker)
            requestUtils.when(() -> RequestUtils.getServletPathFromRequest(request)).thenReturn("/api/docker");

            doAnswer(invocation -> {
                activityCount.incrementAndGet();
                invocation.getArgument(2, LoadHealerRunnable.class).run();
                return null;
            }).when(loadHealerService).executeWithActivity(any(), anyString(), any());

            // Act
            filter.doFilter(request, response, filterChain);

            // Assert - should have 2 activities: user, module (no endpoint)
            assertEquals(2, activityCount.get());
        }
    }

    @Test
    void doFilterShouldPropagateLoadHealerException() throws IOException, ServletException {
        // Arrange
        when(loadHealerService.isEnabled()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn("blockedUser");

        try (MockedStatic<RequestUtils> requestUtils = mockStatic(RequestUtils.class)) {
            requestUtils.when(() -> RequestUtils.getServletPathFromRequest(request)).thenReturn("/api/test");

            doThrow(new LoadHealerException("User blocked")).when(loadHealerService)
                    .executeWithActivity(any(), eq("blockedUser"), any());

            // Act & Assert
            LoadHealerException thrown = assertThrows(LoadHealerException.class,
                    () -> filter.doFilter(request, response, filterChain));
            assertEquals("User blocked", thrown.getMessage());
        }
    }

    @Test
    void doFilterShouldPropagateServletException() throws IOException, ServletException {
        // Arrange
        when(loadHealerService.isEnabled()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn("testUser");

        try (MockedStatic<RequestUtils> requestUtils = mockStatic(RequestUtils.class)) {
            requestUtils.when(() -> RequestUtils.getServletPathFromRequest(request)).thenReturn("/ui/home");

            doAnswer(invocation -> {
                invocation.getArgument(2, LoadHealerRunnable.class).run();
                return null;
            }).when(loadHealerService).executeWithActivity(any(), anyString(), any());

            // Act & Assert
            ServletException thrown = assertThrows(ServletException.class,
                    () -> filter.doFilter(request, response, (req, resp) -> {
                        throw new ServletException("Servlet error");
                    }));
            assertEquals("Servlet error", thrown.getMessage());
        }
    }

    @Test
    void doFilterShouldPropagateIOException() throws IOException, ServletException {
        // Arrange
        when(loadHealerService.isEnabled()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn("testUser");

        try (MockedStatic<RequestUtils> requestUtils = mockStatic(RequestUtils.class)) {
            requestUtils.when(() -> RequestUtils.getServletPathFromRequest(request)).thenReturn("/ui/home");

            doAnswer(invocation -> {
                invocation.getArgument(2, LoadHealerRunnable.class).run();
                return null;
            }).when(loadHealerService).executeWithActivity(any(), anyString(), any());
            IOException thrown = assertThrows(IOException.class,
                    () -> filter.doFilter(request, response, (req, resp) -> {
                        throw new IOException("IO error");
                    }));
            assertEquals("IO error", thrown.getMessage());
        }
    }

    @Test
    void extractModuleShouldReturnCorrectModule() throws IOException, ServletException {
        // Arrange
        when(loadHealerService.isEnabled()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn("testUser");
        String[] capturedModule = new String[1];

        try (MockedStatic<RequestUtils> requestUtils = mockStatic(RequestUtils.class)) {
            requestUtils.when(() -> RequestUtils.getServletPathFromRequest(request))
                    .thenReturn("/api/docker/v2/repo");

            doAnswer(invocation -> {
                String resource = invocation.getArgument(1);
                // Capture the module resource (second call) - only first /api/ match
                if (resource.startsWith("/api/") && capturedModule[0] == null) {
                    capturedModule[0] = resource;
                }
                invocation.getArgument(2, LoadHealerRunnable.class).run();
                return null;
            }).when(loadHealerService).executeWithActivity(any(), anyString(), any());

            // Act
            filter.doFilter(request, response, filterChain);

            // Assert - module is /api/{pathParts[2]} = /api/docker
            assertEquals("/api/docker", capturedModule[0]);
        }
    }

    @Test
    void extractEndPointShouldReturnCorrectEndPoint() throws IOException, ServletException {
        // Arrange
        when(loadHealerService.isEnabled()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn("testUser");
        String[] capturedEndpoint = new String[1];

        try (MockedStatic<RequestUtils> requestUtils = mockStatic(RequestUtils.class)) {
            requestUtils.when(() -> RequestUtils.getServletPathFromRequest(request))
                    .thenReturn("/api/docker/v2/repo");

            doAnswer(invocation -> {
                String resource = invocation.getArgument(1);
                // Capture the endpoint resource (third call - contains /api/x/y format)
                if (resource.contains("/api/") && resource.split("/").length > 3) {
                    capturedEndpoint[0] = resource;
                }
                invocation.getArgument(2, LoadHealerRunnable.class).run();
                return null;
            }).when(loadHealerService).executeWithActivity(any(), anyString(), any());

            // Act
            filter.doFilter(request, response, filterChain);

            // Assert
            assertEquals("/api/docker/v2", capturedEndpoint[0]);
        }
    }
}

