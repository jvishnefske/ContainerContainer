package org.artifactory.webapp.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.AccessClientHttpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtifactoryFilterTest {

    ArtifactoryFilter artifactoryFilter = spy(new ArtifactoryFilter());

    @Mock
    ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
    @Mock
    ArtifactoryHome artifactoryHome;
    @Mock
    FilterChain filterChain = mock(FilterChain.class);

    @BeforeEach
    void setup() {
        when(artifactoryContext.getArtifactoryHome()).thenReturn(artifactoryHome);
        doReturn(false).when(artifactoryFilter).redirectIfNeeded(any(), any(), any());
    }

    @Test
    void testErrorCodeIsNotReplacedWhenBadRequestStatusSetByAccess() throws ServletException, IOException {
        // Setup
        HttpServletRequest request = createHttpRequestMock();
        HttpServletResponse response = createHttpResponseMock();

        doThrow(new AccessClientHttpException(HttpStatus.SC_BAD_REQUEST,
                "Failed on executing request. Response: " + "Bad Request")).
                when(filterChain).doFilter(any(), any());

        artifactoryFilter.doFilter(request, response, filterChain);

        // Verify the results
        verify(response, times(0)).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        verify(response, times(1)).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testReplaceInitialErrorStatusWhenItIsNotSendByAccess() throws ServletException, IOException {
        // Setup
        HttpServletRequest request = createHttpRequestMock();
        HttpServletResponse response = createHttpResponseMock();

        doThrow(new IOException("Failed to request")).
                when(filterChain).doFilter(any(), any());

        artifactoryFilter.doFilter(request, response, filterChain);

        // Verify the results
        verify(response, times(1)).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    void testInitialExceptionIsNotChangedWhenResponseIsNotHttp() throws ServletException, IOException {
        // Setup
        HttpServletRequest request = createHttpRequestMock();
        ServletResponse response = mock(ServletResponse.class);

        doThrow(new IOException("Any exception")).
                when(filterChain).doFilter(any(), any());

        IOException ex = assertThrows(IOException.class, () -> artifactoryFilter.doFilter(request,
                response, filterChain));
        assertTrue(ex.getMessage().contains("Any exception"));
    }

    @ParameterizedTest
    @MethodSource("unauthorizedException")
    void testProcessExceptionForHttpServletResponseBadCredentialsException(final RuntimeException exception)
            throws IOException {
        HttpServletRequest request = createHttpRequestMock();
        HttpServletResponse response = createHttpResponseMock();
        String logMessage = "Auth failure";

        artifactoryFilter.processExceptionForHttpServletResponse(response, request, exception, logMessage);

        verify(response).setStatus(HttpStatus.SC_UNAUTHORIZED);
        verify(response).getWriter();
    }

    private static Stream<Arguments> unauthorizedException() {
        return Stream.of(
                Arguments.of(new BadCredentialsException("Invalid credentials")),
                Arguments.of(new AccessClientException("exception"))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidParameterScenarios")
    void testProcessExceptionForHttpServletResponseMalformedParameters(
            String name, Exception exception, int expectedStatus) throws IOException {
        HttpServletRequest request = createHttpRequestMock();
        HttpServletResponse response = createHttpResponseMock();

        artifactoryFilter.processExceptionForHttpServletResponse(response, request, exception, "ignored");

        verify(response).setStatus(expectedStatus);
        verify(response, times(0)).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        verify(response).getWriter();
    }

    private static Stream<Arguments> invalidParameterScenarios() {
        return Stream.of(
                // bare InvalidParameterException with no explicit errorCode → default to 400
                Arguments.of("bare-no-code-defaults-to-400",
                        new InvalidParameterException("malformed"), HttpStatus.SC_BAD_REQUEST),
                // explicit Tomcat-supplied error code (e.g. 413 from postTooLarge) must be preserved
                Arguments.of("explicit-tomcat-error-code-preserved",
                        new InvalidParameterException("too big", HttpStatus.SC_REQUEST_TOO_LONG),
                        HttpStatus.SC_REQUEST_TOO_LONG),
                // typical filter-chain propagation: ServletException wraps the parse failure
                Arguments.of("wrapped-in-servlet-exception",
                        new ServletException("oops", new InvalidParameterException("malformed query")),
                        HttpStatus.SC_BAD_REQUEST),
                // nested two levels deep (RuntimeException → ServletException → InvalidParameterException)
                Arguments.of("nested-two-levels-deep",
                        new RuntimeException("outer",
                                new ServletException("middle", new InvalidParameterException("malformed"))),
                        HttpStatus.SC_BAD_REQUEST)
        );
    }

    @Test
    void testProcessExceptionForHttpServletResponseNonParameterIllegalStateStaysAs500() throws IOException {
        HttpServletRequest request = createHttpRequestMock();
        HttpServletResponse response = createHttpResponseMock();
        // plain IllegalStateException (parent of InvalidParameterException) must NOT be misclassified as 400
        IllegalStateException unrelated = new IllegalStateException("unrelated state error");

        artifactoryFilter.processExceptionForHttpServletResponse(response, request, unrelated, "ignored");

        verify(response).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    void testProcessExceptionForHttpServletResponseSelfReferencingCauseDoesNotLoop() throws IOException {
        HttpServletRequest request = createHttpRequestMock();
        HttpServletResponse response = createHttpResponseMock();
        // pathological self-referencing cause must not cause an infinite loop in findInvalidParameterCause
        RuntimeException loop = new RuntimeException("loop") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        artifactoryFilter.processExceptionForHttpServletResponse(response, request, loop, "ignored");

        verify(response).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    void testProcessExceptionForHttpServletResponseWhenPreStopRequest() throws IOException {
        final HttpServletRequest request = createHttpRequestMock();
        when(request.getRequestURI()).thenReturn("/artifactory/api/v1/system/preStop");
        when(request.getMethod()).thenReturn("GET");
        final HttpServletResponse response = createHttpResponseMock();
        final RuntimeException exception = new RuntimeException("Any error during shutdown");
        final String logMessage = "Caught exception in GET, /artifactory/api/v1/system/preStop";

        artifactoryFilter.processExceptionForHttpServletResponse(response, request, exception, logMessage);

        verify(response).setStatus(HttpStatus.SC_SERVICE_UNAVAILABLE);
        verify(response).getWriter();
    }

    private HttpServletResponse createHttpResponseMock() throws IOException {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.isCommitted()).thenReturn(false);
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        PrintWriter writer = Mockito.mock(PrintWriter.class);
        when(response.getWriter()).thenReturn(writer);
        return response;
    }

    private HttpServletRequest createHttpRequestMock() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getContextPath()).thenReturn("/artifactory");
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(artifactoryContext);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        artifactoryFilter.init(filterConfig);
        when(request.getServletContext()).thenReturn(servletContext);
        when(request.getRequestURI()).thenReturn("/artifactory/api/system/ping");
        return request;
    }
}
