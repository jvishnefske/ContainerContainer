package org.artifactory.webapp.servlet.authentication;

import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationManager;


class ArtifactoryAccessTokenAuthenticationFilterTest {

    @Test
    void getType() {
        AuthenticationManager authenticationManager = Mockito.mock(AuthenticationManager.class);
        ArtifactoryAccessTokenAuthenticationFilter accessTokenFilter =
                new ArtifactoryAccessTokenAuthenticationFilter(authenticationManager);
        Assertions.assertEquals(ArtifactoryAuthenticationType.ACCESS_TOKEN, accessTokenFilter.getType());
    }
}