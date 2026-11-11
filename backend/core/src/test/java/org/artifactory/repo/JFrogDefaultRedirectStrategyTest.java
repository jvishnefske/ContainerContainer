package org.artifactory.repo;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHeader;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.client.http.JFrogDefaultRedirectStrategy;
import org.jfrog.client.util.DenylistValidator;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class JFrogDefaultRedirectStrategyTest extends ArtifactoryHomeBoundTest {

    private static final int REDIRECT_PERMANENTLY = 308;

    @Test
    void testRedirectedPermanently() throws ProtocolException {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        RequestLine requestLine = mock(RequestLine.class);
        DenylistValidator denylistValidator = mock(DenylistValidator.class);
        when(statusLine.getStatusCode()).thenReturn(REDIRECT_PERMANENTLY);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(request.getRequestLine()).thenReturn(requestLine);
        when(requestLine.getMethod()).thenReturn("GET");
        when(denylistValidator.isBlocked(any())).thenReturn(false);
        JFrogDefaultRedirectStrategy jFrogDefaultRedirectStrategy = new JFrogDefaultRedirectStrategy(denylistValidator);
        assertTrue(jFrogDefaultRedirectStrategy.isRedirected(request, response, null));
    }

    @Test
    void testRedirectWithDenyList() throws ProtocolException {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        RequestLine requestLine = mock(RequestLine.class);
        DenylistValidator denylistValidator = mock(DenylistValidator.class);
        when(statusLine.getStatusCode()).thenReturn(REDIRECT_PERMANENTLY);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(request.getRequestLine()).thenReturn(requestLine);
        when(requestLine.getMethod()).thenReturn("GET");
        when(response.getFirstHeader(HttpHeaders.LOCATION)).thenReturn(
                new BasicHeader(HttpHeaders.LOCATION, "http://localhost:8082"));
        when(denylistValidator.isBlocked(any())).thenReturn(true);
        JFrogDefaultRedirectStrategy jFrogDefaultRedirectStrategy = new JFrogDefaultRedirectStrategy(denylistValidator);
        assertFalse(jFrogDefaultRedirectStrategy.isRedirected(request, response, null));
    }
}