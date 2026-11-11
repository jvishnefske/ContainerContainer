package org.artifactory.webapp.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.common.ConstantValues;
import org.artifactory.request.audit.HttpHeadersAuditService;
import org.artifactory.request.guard.InboundRequestGuardService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpHeadersAuditFilterTest extends ArtifactoryHomeBoundJUnitTest {

    private FilterConfig filterConfig;
    private ServletContext servletContext;
    private ArtifactoryContext artifactoryContext;
    private HttpHeadersAuditService auditService;
    private InboundRequestGuardService guardService;
    private FilterChain filterChain;
    private HttpServletRequest httpRequest;
    private HttpServletResponse httpResponse;

    private MockedStatic<RequestUtils> requestUtils;

    private HttpHeadersAuditFilter filter;

    @BeforeEach
    void setUp() {
        filterConfig = mock(FilterConfig.class);
        servletContext = mock(ServletContext.class);
        artifactoryContext = mock(ArtifactoryContext.class);
        auditService = mock(HttpHeadersAuditService.class);
        guardService = mock(InboundRequestGuardService.class);
        filterChain = mock(FilterChain.class);
        httpRequest = mock(HttpServletRequest.class);
        httpResponse = mock(HttpServletResponse.class);

        requestUtils = mockStatic(RequestUtils.class);
        filter = new HttpHeadersAuditFilter();
    }

    @AfterEach
    void tearDown() {
        requestUtils.close();
    }

    @Test
    void initLater_shouldResolveAuditAndGuardServicesFromContext() throws ServletException {
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        requestUtils.when(() -> RequestUtils.getArtifactoryContext(servletContext)).thenReturn(artifactoryContext);
        when(artifactoryContext.beanForType(HttpHeadersAuditService.class)).thenReturn(auditService);
        when(artifactoryContext.beanForType(InboundRequestGuardService.class)).thenReturn(guardService);

        filter.initLater(filterConfig);

        verify(artifactoryContext).beanForType(HttpHeadersAuditService.class);
        verify(artifactoryContext).beanForType(InboundRequestGuardService.class);
    }

    @Test
    void doFilter_shouldRecordHeadersAndDelegateToChain_whenFlagEnabled() throws IOException, ServletException {
        homeStub.setProperty(ConstantValues.httpHeadersAuditEnabled, "true");
        initializeFilter();

        filter.doFilter(httpRequest, httpResponse, filterChain);

        verify(auditService).recordHeaderNames(httpRequest);
        verify(filterChain).doFilter(httpRequest, httpResponse);
    }

    @Test
    void doFilter_shouldSkipAuditButStillCallChain_whenFlagDisabled() throws IOException, ServletException {
        homeStub.setProperty(ConstantValues.httpHeadersAuditEnabled, "false");
        initializeFilter();

        filter.doFilter(httpRequest, httpResponse, filterChain);

        verify(auditService, never()).recordHeaderNames(any());
        verify(filterChain).doFilter(httpRequest, httpResponse);
    }

    @Test
    void doFilter_shouldSkipAudit_whenRequestIsNotHttp() throws IOException, ServletException {
        homeStub.setProperty(ConstantValues.httpHeadersAuditEnabled, "true");
        initializeFilter();

        ServletRequest nonHttpRequest = mock(ServletRequest.class);
        ServletResponse nonHttpResponse = mock(ServletResponse.class);

        filter.doFilter(nonHttpRequest, nonHttpResponse, filterChain);

        verify(auditService, never()).recordHeaderNames(any());
        verify(filterChain).doFilter(nonHttpRequest, nonHttpResponse);
    }

    @Test
    void doFilter_shouldSwallowAuditExceptionAndContinueChain() throws IOException, ServletException {
        homeStub.setProperty(ConstantValues.httpHeadersAuditEnabled, "true");
        initializeFilter();
        doThrow(new RuntimeException("boom")).when(auditService).recordHeaderNames(httpRequest);

        filter.doFilter(httpRequest, httpResponse, filterChain);

        verify(auditService).recordHeaderNames(httpRequest);
        verify(filterChain).doFilter(httpRequest, httpResponse);
    }

    @Test
    void doFilter_shouldReturn403AndSkipChain_whenGuardBlocks() throws IOException, ServletException {
        homeStub.setProperty(ConstantValues.httpHeadersAuditEnabled, "true");
        initializeFilter();
        when(guardService.shouldBlock(httpRequest)).thenReturn(true);

        filter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse).sendError(eq(HttpServletResponse.SC_FORBIDDEN),
                eq(HttpHeadersAuditFilter.EULA_BLOCK_MESSAGE));
        verify(auditService, never()).recordHeaderNames(any());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_shouldContinueChain_whenGuardExceptionThrown() throws IOException, ServletException {
        homeStub.setProperty(ConstantValues.httpHeadersAuditEnabled, "true");
        initializeFilter();
        when(guardService.shouldBlock(httpRequest)).thenThrow(new RuntimeException("guard exploded"));

        filter.doFilter(httpRequest, httpResponse, filterChain);

        verify(auditService).recordHeaderNames(httpRequest);
        verify(filterChain).doFilter(httpRequest, httpResponse);
        verify(httpResponse, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN), any());
    }

    private void initializeFilter() {
        try {
            when(filterConfig.getServletContext()).thenReturn(servletContext);
            requestUtils.when(() -> RequestUtils.getArtifactoryContext(servletContext)).thenReturn(artifactoryContext);
            when(artifactoryContext.beanForType(HttpHeadersAuditService.class)).thenReturn(auditService);
            when(artifactoryContext.beanForType(InboundRequestGuardService.class)).thenReturn(guardService);
            filter.initLater(filterConfig);
        } catch (ServletException e) {
            throw new RuntimeException("Failed to initialize HttpHeadersAuditFilter", e);
        }
    }
}
