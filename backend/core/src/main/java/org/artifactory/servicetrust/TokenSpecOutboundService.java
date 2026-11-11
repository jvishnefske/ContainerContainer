package org.artifactory.servicetrust;

import org.apache.commons.lang3.NotImplementedException;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.app.service.trust.dto.ExchangeTokenRequest;
import org.jfrog.app.service.trust.dto.ExchangeTokenResponse;

import java.util.Collection;

/**
 * @author igoru
 */
public interface TokenSpecOutboundService {

    /**
     * Store master token together with exchange url, base url and other
     */
    void storeMasterTokenData(ExchangeTokenResponse response, TokenExtension extension);

    /**
     * Update master token to avoid expiration
     */
    void updateMasterTokenData(ExchangeTokenResponse response);

    ExchangeTokenRequest createExchangeTokenRequest(String subject, JwtAccessToken jwt);

    /**
     * Create exchange token to refresh master token
     */
    ExchangeTokenRequest createMasterTokenExchangeRefreshRequest(String subject);

    ExchangeTokenRequest createScopedTokenExchangeRequest(String subject, String... scopes);

    default Collection<ServiceTrustToken> getPairedInstances() {
        throw new NotImplementedException();
    }
}
