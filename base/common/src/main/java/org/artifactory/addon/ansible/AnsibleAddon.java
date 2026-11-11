package org.artifactory.addon.ansible;

import jakarta.annotation.Nullable;
import org.artifactory.common.addon.Addon;
import org.artifactory.security.props.auth.model.OAuth2TokenModel;

public interface AnsibleAddon extends Addon {

    /**
     * Fetches an OAuth2 access token via SSO (refresh token grant) for the given Ansible
     * repository. Handles JWT parsing, request construction, and HTTP execution using the
     * repository's configured HTTP client (proxy, auth, connection settings).
     *
     * @param authUrl       The SSO token endpoint URL
     * @param refreshToken  The OAuth2 refresh token (JWT containing the 'azp' claim)
     * @param repoKey       The repository key (used to resolve the AnsibleHttpRepo)
     * @return The parsed token response, or null if the request failed
     */
    @Nullable
    OAuth2TokenModel fetchSsoToken(String authUrl, String refreshToken, String repoKey);
}
