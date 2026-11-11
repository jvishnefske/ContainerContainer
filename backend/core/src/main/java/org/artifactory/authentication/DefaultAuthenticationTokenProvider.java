package org.artifactory.authentication;

import org.apache.commons.lang3.NotImplementedException;
import org.jfrog.storage.authentication.AuthenticationTokenProvider;
import org.springframework.stereotype.Service;

@Service
public class DefaultAuthenticationTokenProvider implements AuthenticationTokenProvider {

    @Override
    public String getToken(String remoteUrl, String key) {
        throw new NotImplementedException("Not supported");
    }

    @Override
    public void invalidateTokens(String remoteUrl) {
        throw new NotImplementedException("Not supported");
    }
}
