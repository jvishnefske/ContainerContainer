package org.artifactory.repo;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author Alexei Vainshtein
 */

public class HeartbeatLegacyRequestTest {

    @Test
    public void testLegacyRequest() {
        Long time = 1L;
        HeartbeatLegacyRequest request =
                HeartbeatLegacyRequest.builder().originRepoKey("origin").localKey("local").version(time).build();

        assertEquals("local", request.getLocalKey());
        assertEquals("origin", request.getOriginRepoKey());
        assertEquals(time, request.getVersion());
    }
}