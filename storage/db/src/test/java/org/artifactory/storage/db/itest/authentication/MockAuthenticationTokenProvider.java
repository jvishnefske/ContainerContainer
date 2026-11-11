package org.artifactory.storage.db.itest.authentication;

import org.jfrog.storage.authentication.AuthenticationTokenProvider;
import org.springframework.stereotype.Component;

@Component
public class MockAuthenticationTokenProvider implements AuthenticationTokenProvider {
    @Override
    public String getToken(String remoteUrl, String key) {
        return null;
    }

    @Override
    public void invalidateTokens(String remoteUrl) {
    }
}
