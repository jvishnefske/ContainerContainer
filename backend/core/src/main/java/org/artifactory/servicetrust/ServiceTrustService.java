package org.artifactory.servicetrust;

import org.artifactory.spring.ReloadableBean;
import org.jfrog.app.service.trust.dto.ExchangeTokenRequest;
import org.jfrog.app.service.trust.dto.ExchangeTokenResponse;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author igoru
 */
public interface ServiceTrustService extends ReloadableBean {

    long DEFAULT_PAIRING_TOKEN_EXPIRATION_TIME_IN_SECS = TimeUnit.MINUTES.toSeconds(5);
    long DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS = TimeUnit.DAYS.toSeconds(365);
    long DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS = TimeUnit.HOURS.toSeconds(1);

    String INTERNAL_SCOPE_PREFIX = "internal:";

    String INTERNAL_APPLIED_PERMISSIONS_SERVICE_PREFIX = INTERNAL_SCOPE_PREFIX + "applied-permissions/service/";
    String INTERNAL_APPLIED_PERMISSIONS_SERVICE_POSTFIX = ":*";

    String INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_PREFIX = INTERNAL_SCOPE_PREFIX + "service-trust/pairing/";
    String INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_POSTFIX = ":x";

    String PAIRING_SCOPE_PATTERN =
            INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_PREFIX + "%s" + INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_POSTFIX;

    String APPLIED_PERMISSIONS_SERVICE_PATTERN =
            INTERNAL_APPLIED_PERMISSIONS_SERVICE_PREFIX + "%s" + INTERNAL_APPLIED_PERMISSIONS_SERVICE_POSTFIX;

    /**
     * Creates a short-lived pairing token for Artifactory instances requesting to bind.
     *
     * @param trustData a record of type {@link ServiceTrustUseCaseData}.
     * @return registration token value in JWT format.
     */
    String getPairingToken(ServiceTrustUseCaseData trustData, ServiceTrustUrls urls);

    /**
     * Pairs a pre-issued JWT binding token with the issuing remote Artifactory.
     * @param useCase a {@link ServiceTrustUseCase} object.
     * @param token a pairing token issued by the remote instance to be paired to.
     */
    void pairToken(ServiceTrustUseCase useCase, String token) throws IOException;

    /**
     * Exchanges pairing token for a long lived master token.
     *
     * @param request exchange request of type {@link ExchangeTokenRequest}
     * @return master token in JWT format.
     */
    ExchangeTokenResponse exchangePairingToken(ExchangeTokenRequest request);

    /**
     * Finds use case in the list of registered use cases
     *
     * @param useCasePath use case value
     * @return if case such use case is known returns optional with use case value, empty optional otherwise
     */
    Optional<ServiceTrustUseCase> findUseCase(String useCasePath);

    /**
     * Refreshes a master token
     *
     * @param exchangeUrl exchange url value
     * @param masterToken active master token
     */
    void refreshMasterToken(ServiceTrustUseCase serviceTrustUseCase, String exchangeUrl, String masterToken)
            throws IOException;

    /**
     * Exchange a master token to a scoped token
     *
     * @param serviceTrustUseCase - use case
     * @param exchangeUrl         - exchange url
     * @param masterToken         - master token
     * @return scoped token info
     * @throws IOException
     */
    ExchangeTokenResponse exchangeMasterToken(ServiceTrustUseCase serviceTrustUseCase, String exchangeUrl,
            String masterToken, String... scopes) throws IOException;

    Collection<ServiceTrustToken> getPairingTokenList(String useCasePath, String baseUrl);

    void unpairToken(ServiceTrustUseCase serviceTrustUseCase, String useCasePath, String baseUrl, boolean force);

    boolean revokeToken(String useCasePath);
}
