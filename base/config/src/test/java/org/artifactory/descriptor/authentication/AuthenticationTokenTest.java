package org.artifactory.descriptor.authentication;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author Oleksandr Legenkiy
 */
public class AuthenticationTokenTest {

    @Test // RTDEV-27499
    public void testOverridesEquals() {
        assertEquals(new AuthenticationToken(), new AuthenticationToken());
        assertEquals(createToken(), createToken());
    }

    private AuthenticationToken createToken() {
        return new AuthenticationToken("someToken", "someBaseUrl", "someExchangeUrl", 1L, 1L, "someTokenId",
                "someTokenType", "someScope");
    }
}
