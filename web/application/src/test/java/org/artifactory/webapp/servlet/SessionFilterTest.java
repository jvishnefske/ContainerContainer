package org.artifactory.webapp.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.PrintWriter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SessionFilterTest {

    private HttpServletRequest servletRequest;
    private HttpServletResponse servletResponse;
    private FilterChain mockChain;
    private Filter filter;

    @BeforeMethod
    public void setUp() throws ServletException {
        servletRequest = Mockito.mock(HttpServletRequest.class);
        servletResponse = Mockito.mock(HttpServletResponse.class);
        mockChain = Mockito.mock(FilterChain.class);
        filter = new SessionFilter();
    }

    @Test
    public void testDoFilterReadinessRequestWhenDelegateIsNull() throws IOException, ServletException {
        when(servletRequest.getRequestURI()).thenReturn("/artifactory/api/v1/system/readiness");
        when(servletResponse.getWriter()).thenReturn(Mockito.mock(PrintWriter.class));

        // call doFilter, in this case initLater will not be called and delegate will be null
        filter.doFilter(servletRequest, servletResponse, mockChain);

        // Assert we're sending 503 for readiness endpoint
        verify(servletResponse).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(servletResponse).setContentType("application/json");
        verify(servletResponse).getWriter();
    }

    @Test
    public void testDoFilterPreStopRequestWhenDelegateIsNull() throws IOException, ServletException {
        when(servletRequest.getRequestURI()).thenReturn("/artifactory/api/v1/system/preStop");
        when(servletResponse.getWriter()).thenReturn(Mockito.mock(PrintWriter.class));

        filter.doFilter(servletRequest, servletResponse, mockChain);

        verify(servletResponse).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(servletResponse).setContentType("application/json");
        verify(servletResponse).getWriter();
    }

    @Test(expectedExceptions = ServletException.class)
    public void testDoFilterWhenDelegateIsNull() throws IOException, ServletException {
        when(servletRequest.getRequestURI()).thenReturn("/artifactory/api/v1/test");
        when(servletResponse.getWriter()).thenReturn(Mockito.mock(PrintWriter.class));

        // call doFilter, in this case initLater will not be called and delegate will be null
        filter.doFilter(servletRequest, servletResponse, mockChain);
    }
}
