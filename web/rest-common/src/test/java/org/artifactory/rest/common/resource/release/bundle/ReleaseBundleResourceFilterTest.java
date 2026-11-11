package org.artifactory.rest.common.resource.release.bundle;

import org.apache.http.HttpHeaders;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.release.bundle.ReleaseBundleActors;
import org.testng.annotations.Test;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ReleaseBundleResourceFilterTest {

    private ReleaseBundleResourceFilter filter = new ReleaseBundleResourceFilter();

    @Test
    public void testFilterRequest_noHeaders() throws IOException {
        // setup:
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();

        when(requestContext.getHeaders()).thenReturn(headers);

        // test
        filter.filter(requestContext);

        // verify
        assertEquals(ReleaseBundleActors.EXTERNAL_USER, CurrentActivity.ACTOR.get());
    }

    @Test
    public void testFilterRequest_noAgentHeaders() throws IOException {
        // setup:
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.put("some_header", List.of("value"));

        when(requestContext.getHeaders()).thenReturn(headers);

        // test
        filter.filter(requestContext);

        // verify
        assertEquals(ReleaseBundleActors.EXTERNAL_USER, CurrentActivity.ACTOR.get());
    }

    @Test
    public void testFilterRequest_frontEndAgent() throws IOException {
        // setup:
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.put(HttpHeaders.USER_AGENT, List.of("JFrog-Frontend"));

        when(requestContext.getHeaders()).thenReturn(headers);

        // test
        filter.filter(requestContext);

        // verify
        assertEquals(ReleaseBundleActors.EXTERNAL_USER, CurrentActivity.ACTOR.get());
    }

    @Test
    public void testFilterRequest_artifactoryUserAgent() throws IOException {
        // setup:
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.put(HttpHeaders.USER_AGENT, List.of("artifactory-client-java:2.16.0"));

        when(requestContext.getHeaders()).thenReturn(headers);

        // test
        filter.filter(requestContext);

        // verify
        assertEquals(ReleaseBundleActors.DISTRIBUTION_SERVICE, CurrentActivity.ACTOR.get());
    }

    @Test
    public void testFilterRequestOut() throws IOException {
        // setup:
        CurrentActivity.ACTOR.put(ReleaseBundleActors.DISTRIBUTION_SERVICE);
        assertEquals(ReleaseBundleActors.DISTRIBUTION_SERVICE, CurrentActivity.ACTOR.get());

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        ContainerResponseContext responseContext = mock(ContainerResponseContext.class);

        // test
        filter.filter(requestContext, responseContext);

        // verify
        assertNull(CurrentActivity.ACTOR.get());
    }

}