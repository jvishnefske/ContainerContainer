package org.artifactory.api.request;

import org.apache.commons.io.input.CountingInputStream;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.rest.exception.ExpiredRestException;
import org.artifactory.rest.exception.RestException;
import org.artifactory.rest.resource.ArtifactoryResourceInfo;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.artifactory.api.request.ArtifactoryHeaders.CURATION_ORIGIN;
import static org.artifactory.api.request.ArtifactoryHeaders.XRAY_ORIGIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Andrey Tashlitsky
 */
class JerseyArtifactoryResponseTest {

    @Test
    void testCallbackCalledAfterDownload() throws IOException {
        String test = "test";
        InputStream targetStream = new ByteArrayInputStream(test.getBytes());
        CountingInputStream cis = new CountingInputStream(targetStream);
        JerseyArtifactoryResponse jerseyArtifactoryResponse = new JerseyArtifactoryResponse();

        long[] a = new long[1];
        jerseyArtifactoryResponse.sendStream(cis, value -> a[0] = value);

        assertEquals(0, a[0]); // stream is not consumed

        Response built = jerseyArtifactoryResponse.build();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ((StreamingOutput) built.getEntity()).write(baos);

        assertEquals(test.length(), a[0]);  // stream is consumed
    }

    @Test
    void testSendErrorInternal() {
        JerseyArtifactoryResponse jerseyArtifactoryResponse = new JerseyArtifactoryResponse();
        jerseyArtifactoryResponse.setHeader(XRAY_ORIGIN, "true");
        jerseyArtifactoryResponse.setHeader(CURATION_ORIGIN, "true");
        jerseyArtifactoryResponse.setHeader("cool-header", "cool-value");
        RestException e = assertThrows(RestException.class,
                () -> jerseyArtifactoryResponse.sendErrorInternal(403, "cool-error")
        );
        assertEquals(403, e.getStatusCode());
        assertEquals("cool-error", e.getMessage());
        assertEquals("true", e.getHeaders().get(XRAY_ORIGIN));
        assertEquals("true", e.getHeaders().get(CURATION_ORIGIN));
        assertEquals("cool-value", e.getHeaders().get("cool-header"));
    }

    @Test
    void testSendErrorInternalWithResource() {
        JerseyArtifactoryResponse jerseyArtifactoryResponse = new JerseyArtifactoryResponse();
        ArtifactoryResourceInfo resourceInfoExpired = new ArtifactoryResourceInfo(false, true, false);
        RestException e = assertThrows(RestException.class,
                () -> jerseyArtifactoryResponse.sendErrorInternalWithResource(403, "cool-error", resourceInfoExpired)
        );
        assertInstanceOf(ExpiredRestException.class, e);
        ArtifactoryResourceInfo resourceInfoFound = new ArtifactoryResourceInfo(false, false, false);
        e = assertThrows(RestException.class,
                () -> jerseyArtifactoryResponse.sendErrorInternalWithResource(403, "cool-error", resourceInfoFound)
        );
        assertInstanceOf(RestException.class, e);
        assertFalse(e instanceof ExpiredRestException);
        e = assertThrows(RestException.class,
                () -> jerseyArtifactoryResponse.sendErrorInternalWithResource(403, "cool-error", null
                )
        );
        assertInstanceOf(RestException.class, e);
        assertFalse(e instanceof ExpiredRestException);
    }


    @Test
    void testSendErrorInternalNoHeaders() {
        JerseyArtifactoryResponse jerseyArtifactoryResponse = new JerseyArtifactoryResponse();
        RestException e = assertThrows(RestException.class,
                () -> jerseyArtifactoryResponse.sendErrorInternal(403, "cool-error")
        );
        assertEquals(403, e.getStatusCode());
        assertEquals("cool-error", e.getMessage());
        assertNull(e.getHeaders().get(XRAY_ORIGIN));
        assertNull(e.getHeaders().get(CURATION_ORIGIN));
        assertNull(e.getHeaders().get("cool-header"));
    }

    @Test
    void sendAuthorizationRequiredThrowsException() {
        JerseyArtifactoryResponse response = new JerseyArtifactoryResponse();
        assertThrows(AuthorizationRestException.class, () ->
                response.sendAuthorizationRequired("Unauthorized access", "TestRealm")
        );
    }
}
