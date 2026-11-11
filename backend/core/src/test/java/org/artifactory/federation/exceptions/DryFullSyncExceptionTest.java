package org.artifactory.federation.exceptions;

import org.artifactory.federation.DryFullSyncException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sivan Shafrir
 */
class DryFullSyncExceptionTest {

    @Test
    void testArtifactoryFederationClientException() {
        DryFullSyncException dryFullSyncException = new DryFullSyncException("error message");
        assertEquals("error message", dryFullSyncException.getMessage());
    }

    @Test
    void testArtifactoryFederationClientExceptionWithCause() {
        DryFullSyncException dryFullSyncException = new DryFullSyncException("error message",
                new IOException("some_error"));
        assertEquals("error message", dryFullSyncException.getMessage());
    }

}