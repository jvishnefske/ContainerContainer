package org.artifactory.sso;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.artifactory.addon.oauth.OAuthHandler;
import org.artifactory.addon.oauth.OAuthLoginUrl;
import org.artifactory.addon.oauth.SsoLoginModel;
import org.artifactory.security.props.auth.model.OauthModel;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Default impl for the {@link OAuthHandler}.
 * The main impl is in the Artifactory pro module.
 */
@Service
public class OAuthHandlerDefaultImpl implements OAuthHandler {

    @Override
    public URI handleLoginResponse(HttpServletRequest request, HttpServletResponse response, boolean isPlatformMode) {
        return null;
    }

    @Override
    public List<OAuthLoginUrl> getActiveProviders(HttpServletRequest request, HttpServletResponse response,
            boolean isPlatformMode) throws JsonProcessingException {
        return Collections.emptyList();
    }

    @Override
    public Object handleLogin(String method, String name, String path, HttpServletRequest request,
            SsoLoginModel ssoLoginModel) {
        return null;
    }

    @Override
    public Optional<String> getNpmLoginHandler() {
        return Optional.empty();
    }

    @Override
    public OauthModel getCreateToken(String providerName, String basicAuth) {
        return null;
    }
}
