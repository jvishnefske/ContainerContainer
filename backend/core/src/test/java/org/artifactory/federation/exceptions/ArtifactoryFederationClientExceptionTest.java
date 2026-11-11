package org.artifactory.federation.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Tomer Slutsky
 */
class ArtifactoryFederationClientExceptionTest {

    @Test
    void testArtifactoryFederationClientException() {
        String message = "message";
        ArtifactoryFederationClientException exception = new ArtifactoryFederationClientException(message);
        assertEquals(message, exception.getMessage());
    }
}