package org.artifactory.servicetrust;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.metrics.FeatureAdoptionAction;
import org.artifactory.api.metrics.FeatureAdoptionFeature;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.TokenNotFoundException;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.config.InternalCentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.logging.throttle.LogThrottleMarker;
import org.artifactory.metrics.framework.adoption.FeatureAdoptionService;
import org.artifactory.metrics.framework.adoption.federated.FederatedRepoLabels;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.servicetrust.cold.ArtifactoryColdTokenSpecService;
import org.artifactory.servicetrust.distribution.DistributionTokenSpecService;
import org.artifactory.servicetrust.federation.FederatedRepositoryTokenSpecService;
import org.artifactory.servicetrust.federation.UnidirectionalFederationTokenSpecService;
import org.artifactory.spring.Reloadable;
import org.artifactory.util.HttpClientConfigurator;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.common.SubjectFQN;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.JwtAccessTokenImpl;
import org.jfrog.app.service.trust.dto.ExchangeTokenRequest;
import org.jfrog.app.service.trust.dto.ExchangeTokenResponse;
import org.jfrog.app.service.trust.error.ServiceTrustException;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.config.diff.DataDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import static org.artifactory.security.AuthenticationHelper.getAuthentication;

/**
 * @author igoru
 */
@Service
@Slf4j
@Reloadable(beanClass = ServiceTrustService.class,
        initAfter = InternalCentralConfigService.class,
        listenOn = CentralConfigKey.proxies)
public class ServiceTrustServiceImpl implements ServiceTrustService {

    private static final int SOCKET_TIMEOUT = 15000;
    private static final int CONNECTION_TIMEOUT = 1500;

    private static final String BEARER = "Bearer ";

    private static final String ERROR_EXCHANGING_TOKEN = "Error exchanging token";

    static final String ERROR_EXCHANGING_TOKEN_NETWORK_FAILURE =
            ERROR_EXCHANGING_TOKEN + " due to network failure";
    static final String ERROR_EXCHANGING_TOKEN_MEMBER_NOT_READY =
            ERROR_EXCHANGING_TOKEN + ": the remote member is not ready yet";
    private static final String ERROR_EXCHANGING_TOKEN_WITH_REASON =
            ERROR_EXCHANGING_TOKEN + ": unacceptable http status code from target server %s";
    private static final String ERROR_CUSTOM_URL =
            "WARNING: Custom url for a target server is not configured. It may cause pairing issues";
    private static final String ERROR_UNPAIRING_TOKEN =
            "Error unpairing token: unacceptable http status code from target server %s";

    private static final Set<ServiceTrustUseCase> USE_CASES = Set.of(
            ServiceTrustUseCase.ARTIFACTORY_COLD,
            ServiceTrustUseCase.ARTIFACTORY_COLD_WITH_NAMESPACE,
            ServiceTrustUseCase.DISTRIBUTION,
            ServiceTrustUseCase.FEDERATED_REPO,
            ServiceTrustUseCase.FEDERATED_REPO_READ
    );
    private static final String USE_CASE_NOT_FOUND = "Use case %s not found";

    private static final Map<ServiceTrustUseCase, TokenSpecOutboundService> SPEC_OUTBOUND_SERVICES;
    private static final Map<ServiceTrustUseCase, TokenSpecInboundService> SPEC_INBOUND_SERVICES;
    private static final Map<ServiceTrustUseCase, FeatureAdoptionFeature> FEATURE_ADOPTION_SERVICES;
    public static final String ERROR_PAIRING_TOKEN_EXPIRED = "Attempt to pair instances with an expired token";
    public static final String ERROR_PAIRING_INSTANCE_WITH_ITSELF = "Attempt to pair instance with itself";

    private final ReentrantLock httpClientLock = new ReentrantLock();
    private CloseableHttpClient httpClient = null;
    private CloseableHttpClient httpClientWithRetry = null;

    static {
        SPEC_OUTBOUND_SERVICES = Map.of(
                ServiceTrustUseCase.ARTIFACTORY_COLD,
                ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE,

                ServiceTrustUseCase.ARTIFACTORY_COLD_WITH_NAMESPACE,
                ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE,

                ServiceTrustUseCase.FEDERATED_REPO,
                FederatedRepositoryTokenSpecService.FEDERATED_REPO_TOKEN_SPEC_SERVICE,

                ServiceTrustUseCase.FEDERATED_REPO_READ,
                UnidirectionalFederationTokenSpecService.UNIDIRECTIONAL_FEDERATION_TOKEN_SPEC_SERVICE);

        SPEC_INBOUND_SERVICES = Map.of(
                ServiceTrustUseCase.ARTIFACTORY_COLD,
                ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE,

                ServiceTrustUseCase.ARTIFACTORY_COLD_WITH_NAMESPACE,
                ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE,

                // distribution only uses the inbound service since it is not using the artifactory spec service logic
                ServiceTrustUseCase.DISTRIBUTION,
                DistributionTokenSpecService.DISTRIBUTION_TOKEN_SPEC_SERVICE,

                ServiceTrustUseCase.FEDERATED_REPO,
                FederatedRepositoryTokenSpecService.FEDERATED_REPO_TOKEN_SPEC_SERVICE,

                ServiceTrustUseCase.FEDERATED_REPO_READ,
                UnidirectionalFederationTokenSpecService.UNIDIRECTIONAL_FEDERATION_TOKEN_SPEC_SERVICE);

        FEATURE_ADOPTION_SERVICES = Map.of(
                ServiceTrustUseCase.ARTIFACTORY_COLD,
                FeatureAdoptionFeature.COLD_STORAGE,
                ServiceTrustUseCase.FEDERATED_REPO,
                FeatureAdoptionFeature.FEDERATED_REPO
        );
    }

    private AccessService accessService;
    private SecurityService securityService;
    private CentralConfigService centralConfigService;
    private FeatureAdoptionService featureAdoptionService;

    @Autowired
    public void setCentralConfigService(CentralConfigService centralConfigService) {
        this.centralConfigService = centralConfigService;
    }

    @Autowired
    public void setAccessService(AccessService accessService) {
        this.accessService = accessService;
    }

    @Autowired
    public void setFeatureAdoptionService(FeatureAdoptionService featureAdoptionService) {
        this.featureAdoptionService = featureAdoptionService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public String getPairingToken(ServiceTrustUseCaseData trustData, ServiceTrustUrls urls) {
        TokenSpec pairingTokenSpec = createPairingTokenSpec(trustData, urls);
        CreatedTokenInfo tokenInfo = accessService.createToken(pairingTokenSpec);
        return tokenInfo.getTokenValue();
    }

    @Override
    public void pairToken(ServiceTrustUseCase useCase, String token) throws ServiceTrustException {
        JwtAccessToken jwt = parseTokenValue(token);
        assertAccessTokenNotExpired(jwt);
        ExchangeTokenRequest exchangeTokenRequest = createExchangeTokenRequest(jwt, useCase);
        TokenExtension extension = JsonUtils.getInstance().readValue(jwt.getExtension(), TokenExtension.class);
        ExchangeTokenResponse exchangeTokenResponse =
                executeTokenExchangeRequest(extension.getExchangeUrl(), token, exchangeTokenRequest);

        storeMasterTokenData(useCase, exchangeTokenResponse, extension);

        FeatureAdoptionFeature feature = getFeatureByUseCase(useCase);
        if (feature != null) {
            featureAdoptionService.trackAdoption(feature, FeatureAdoptionAction.REGISTERED, true,
                    FederatedRepoLabels.TOKEN);
        }
    }

    private ExchangeTokenRequest createExchangeTokenRequest(JwtAccessToken jwt, ServiceTrustUseCase useCase) {
        ServiceId artifactoryServiceId = accessService.getArtifactoryServiceId();
        assertPairingValid(jwt, artifactoryServiceId.getFormattedName());
        String subject = createTokenSubject(artifactoryServiceId);
        TokenSpecOutboundService specService = getOutboundSpecService(useCase);
        return specService.createExchangeTokenRequest(subject, jwt);
    }

    private void assertAccessTokenNotExpired(JwtAccessToken jwt) {
        if (jwt.getExpirationTime() != null && System.currentTimeMillis() > jwt.getExpirationTime()) {
            throw new BadRequestException(ERROR_PAIRING_TOKEN_EXPIRED);
        }
    }

    private void assertPairingValid(JwtAccessToken jwt, String serviceIdName) {
        jwt.getAudience().stream()
                .filter(aud -> aud.equalsIgnoreCase(serviceIdName))
                .findFirst()
                .ifPresent(found -> {
                    throw new BadRequestException(ERROR_PAIRING_INSTANCE_WITH_ITSELF);
                });
    }

    @Override
    public void refreshMasterToken(ServiceTrustUseCase serviceTrustUseCase, String exchangeUrl, String masterToken)
            throws IOException {
        ServiceId artifactoryServiceId = accessService.getArtifactoryServiceId();
        String subject = createTokenSubject(artifactoryServiceId);
        ExchangeTokenRequest exchangeTokenRequest =
                getOutboundSpecService(serviceTrustUseCase).createMasterTokenExchangeRefreshRequest(subject);
        ExchangeTokenResponse exchangeTokenResponse =
                executeTokenExchangeRequest(exchangeUrl, masterToken, exchangeTokenRequest);
        updateMasterTokenData(serviceTrustUseCase, exchangeTokenResponse);
    }

    @Override
    public ExchangeTokenResponse exchangeMasterToken(ServiceTrustUseCase serviceTrustUseCase,
            String exchangeUrl, String masterToken, String... scopes) throws IOException {
        ServiceId artifactoryServiceId = accessService.getArtifactoryServiceId();
        String subject = createTokenSubject(artifactoryServiceId);
        ExchangeTokenRequest exchangeTokenRequest =
                getOutboundSpecService(serviceTrustUseCase).createScopedTokenExchangeRequest(subject, scopes);
        return executeTokenExchangeRequest(exchangeUrl, masterToken, exchangeTokenRequest);
    }

    @Override
    public Collection<ServiceTrustToken> getPairingTokenList(String useCasePath, String baseUrl) {
        return getOutboundSpecService(findUseCase(useCasePath).get()).getPairedInstances();
    }

    @Override
    public void unpairToken(ServiceTrustUseCase serviceTrustUseCase, String useCasePath, String baseUrl,
            boolean force) {
        // Scenario: Admin or MC calls JPD-A
        TokenSpecInboundService inboundSpecService = getInboundSpecService(serviceTrustUseCase);
        Optional<String> authenticationToken = inboundSpecService.getAuthenticationToken(baseUrl);

        if (authenticationToken.isEmpty()) {
            log.warn("Master authentication token for base url {} is not found or has already been removed", baseUrl);
            // in the internal call to JPD-B the base url will never be found, we care less and simply return OK
            throw new ServiceTrustException("Pairing does not exist for instance");
        }
        String accessToken = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), authenticationToken.get());
        JwtAccessToken jwt = parseTokenValue(accessToken);
        TokenExtension extension = JsonUtils.getInstance().readValue(jwt.getExtension(), TokenExtension.class);
        String revokeTokenUrl = extension.getPairingUrl()
                .replace("/pairing", "/revoke/").concat(useCasePath);
        /*
         * Here order is important, we first check is any Archive Policy is enabled or running then we are checking the
         * force flag.
         * If the Archive Policy is enabled or running, we will not revoke the token even if the force flag is set.
         * */
        if (inboundSpecService.isForcedUnpairingAllowed() && force) {
            try {
                log.info("Revoking token for base url {} with force: {}", baseUrl, true);
                boolean revoked = executeRevokeTokenRequestWithRetries(revokeTokenUrl, accessToken);
                if (!revoked) {
                    log.info("Token for base url {} was force deleted without successful revocation.", baseUrl);
                }
            } catch (IOException | ServiceTrustException ignored) {
                //skip the exception, we will delete the token anyway
            }

        } else {
            try {
                executeRevokeTokenRequest(revokeTokenUrl, accessToken);
            } catch (IOException | ServiceTrustException e) {
                String message = "Failed calling revoke token endpoint";
                log.error(message + ": '{}'", revokeTokenUrl);
                throw new ServiceTrustException(message);
            }
        }

        inboundSpecService.deleteAuthenticationToken(baseUrl);
    }

    /**
     * Scenario: JPD-A -> JPD-B
     * Best effort to revoke the token (if created by this JPD -> revoke it)
     * But also make sure the revoked token has the relevant use case!
     * (avoid revoking other pairing types e.g. MC paired with this instance using scoped tokens...)
     */
    @Override
    public boolean revokeToken(String useCasePath) {
        JwtAccessToken authToken = getJwtAccessToken();
        if (authToken != null && authToken.hasScopeMatching(s -> s.contains(useCasePath))) {
            try {
                accessService.revokeTokenById(authToken.getTokenId());
                return true;
            } catch (TokenNotFoundException e) {
                String message = "Tried revoking token which does not exist or has already been revoked";
                log.warn(message);
                throw e;
            } catch (Exception e) {
                log.warn("Failed revoking token", e);
            }
        }
        return false;
    }

    @Nullable
    private JwtAccessToken getJwtAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // this can be (JPD-A -> JPD-B) or (MC -> JPD-A)
        if (authentication instanceof AccessTokenAuthentication accessToken) {
            return accessToken.getAccessToken();
        }
        return null;
    }

    @Override
    public ExchangeTokenResponse exchangePairingToken(ExchangeTokenRequest request) {
        AccessTokenAuthentication accessTokenAuthentication = (AccessTokenAuthentication) getAuthentication();
        JwtAccessToken authToken = accessTokenAuthentication.getAccessToken();
        List<String> scopes = authToken.getScope();
        String tokenValue = Optional.ofNullable(authToken.getTokenValue())
                .orElseThrow(() -> new ServiceTrustException("Token value is null"));
        // TODO: elis 10/10/2021 - according to the spec the pairing token should be revoked after exchange
        return pairing(request.target(), scopes, tokenValue);
    }

    @Override
    public Optional<ServiceTrustUseCase> findUseCase(String useCasePath) throws ServiceTrustException {
        return USE_CASES.stream()
                .filter(u -> u.matches(useCasePath))
                .map(Optional::of)
                .findFirst()
                .orElseThrow(() -> new ServiceTrustException(String.format(USE_CASE_NOT_FOUND, useCasePath)));
    }

    @VisibleForTesting
    void storeMasterTokenData(ServiceTrustUseCase useCase, ExchangeTokenResponse response, TokenExtension extension) {
        getOutboundSpecService(useCase).storeMasterTokenData(response, extension);
    }

    @VisibleForTesting
    private void updateMasterTokenData(ServiceTrustUseCase serviceTrustUseCase,
            ExchangeTokenResponse exchangeTokenResponse) {
        getOutboundSpecService(serviceTrustUseCase).updateMasterTokenData(exchangeTokenResponse);
    }

    @VisibleForTesting
    JwtAccessToken parseTokenValue(String token) {
        return JwtAccessTokenImpl.parseTokenValue(token);
    }

    @VisibleForTesting
    ExchangeTokenResponse createToken(TokenSpec tokenSpec) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            securityService.authenticateAsAdmin();
            CreatedTokenInfo masterTokenInfo = accessService.createToken(tokenSpec, true, true);
            String masterToken = masterTokenInfo.getTokenValue();
            JwtAccessToken jwt = parseTokenValue(masterToken);
            return new ExchangeTokenResponse(jwt.getTokenId(), masterToken, masterTokenInfo.getExpiresIn(),
                    masterTokenInfo.getScope());
        } finally {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    @VisibleForTesting
    CloseableHttpResponse executeMethod(HttpUriRequest httpMethod) throws IOException {
        return getClosableHttpClient().execute(httpMethod);
    }

    @VisibleForTesting
    CloseableHttpClient getClosableHttpClient() {
        httpClientLock.lock();
        try {
            if (httpClient == null) {
                initHttpClient();
            }
            return httpClient;
        } finally {
            httpClientLock.unlock();
        }
    }

    @VisibleForTesting
    CloseableHttpClient getClosableRetryHttpClient() {
        httpClientLock.lock();
        try {
            if (httpClientWithRetry == null) {
                initHttpClientWithRetry();
            }
            return httpClientWithRetry;
        } finally {
            httpClientLock.unlock();
        }
    }

    @VisibleForTesting
    ExchangeTokenResponse executeTokenExchangeRequest(String exchangeUrl, String token,
            ExchangeTokenRequest exchangeTokenRequest) throws ServiceTrustException {
        String requestBody = JsonUtils.getInstance().valueToString(exchangeTokenRequest, true);
        HttpEntity requestEntity = new StringEntity(requestBody, ContentType.APPLICATION_JSON);
        HttpPost postMethod = new HttpPost(exchangeUrl);
        postMethod.setEntity(requestEntity);
        postMethod.addHeader(HttpHeaders.AUTHORIZATION, BEARER + token);
        try (CloseableHttpResponse response = executeMethod(postMethod)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            if (statusCode != HttpStatus.SC_OK) {
                if (statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE) {
                    log.warn(ERROR_EXCHANGING_TOKEN_MEMBER_NOT_READY);
                    throw new ServiceTrustException(ERROR_EXCHANGING_TOKEN_MEMBER_NOT_READY);
                }
                logAndThrowExchangeError(entity, statusCode, exchangeUrl);
            }
            return JsonUtils.getInstance().readValue(entity.getContent(), ExchangeTokenResponse.class);
        } catch (IOException ioe) {
            log.error(LogThrottleMarker.getInstance(), ERROR_EXCHANGING_TOKEN_NETWORK_FAILURE);
            log.debug(ERROR_EXCHANGING_TOKEN_NETWORK_FAILURE, ioe);
            throw new ServiceTrustException(ERROR_EXCHANGING_TOKEN_NETWORK_FAILURE);
        }
    }

    @VisibleForTesting
    void executeRevokeTokenRequestWithHttpClient(String revokeTokenUrl, String token,
            CloseableHttpClient httpClient) throws IOException, ServiceTrustException {
        HttpDelete httpDelete = new HttpDelete(revokeTokenUrl);
        httpDelete.addHeader(HttpHeaders.AUTHORIZATION, BEARER + token);
        try (CloseableHttpResponse response = httpClient.execute(httpDelete)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                handleRevokeError(response.getEntity(), statusCode);
            }
        }
    }

    protected void executeRevokeTokenRequest(String revokeTokenUrl, String token) throws IOException {
        executeRevokeTokenRequestWithHttpClient(revokeTokenUrl, token, getClosableHttpClient());
    }

    protected boolean executeRevokeTokenRequestWithRetries(String revokeTokenUrl, String token)
            throws IOException, ServiceTrustException {
        executeRevokeTokenRequestWithHttpClient(revokeTokenUrl, token, getClosableRetryHttpClient());
        return true;
    }

    private void handleRevokeError(HttpEntity entity, int statusCode) throws IOException {
        String errorMessage = String.format(ERROR_UNPAIRING_TOKEN, statusCode);
        String detailedErrorMessage = String.join(". ", errorMessage,
                EntityUtils.toString(entity));
        log.warn(detailedErrorMessage);
        throw new ServiceTrustException(errorMessage);
    }

    private void logAndThrowExchangeError(HttpEntity entity, int statusCode, String exchangeUrl)
            throws ServiceTrustException {
        String errorMessage = ERROR_EXCHANGING_TOKEN_WITH_REASON.formatted(statusCode);
        if (exchangeUrl.contains("localhost")) {
            errorMessage = String.join(". ", errorMessage, ERROR_CUSTOM_URL);
        }
        boolean isWarning = Set.of(HttpStatus.SC_MOVED_TEMPORARILY,
                HttpStatus.SC_UNAUTHORIZED, HttpStatus.SC_FORBIDDEN,
                HttpStatus.SC_BAD_GATEWAY, HttpStatus.SC_SERVICE_UNAVAILABLE).contains(statusCode);
        tryLogDetailedExchangeError(entity, errorMessage, isWarning);

        throw new ServiceTrustException(errorMessage);
    }

    private static void tryLogDetailedExchangeError(HttpEntity entity, String errorMessage, boolean isWarning) {
        try {
            String detailedErrorMessage = String.join(". ", errorMessage,
                    EntityUtils.toString(entity));
            if (isWarning) {
                log.warn(LogThrottleMarker.getInstance(), detailedErrorMessage);
            } else {
                log.error(LogThrottleMarker.getInstance(), detailedErrorMessage);
            }
        } catch (IOException parsingException) {
            if (isWarning) {
                log.warn(errorMessage, parsingException);
            } else {
                log.error(errorMessage, parsingException);
            }
        }
    }

    private TokenSpecOutboundService getOutboundSpecService(ServiceTrustUseCase useCase) {
        TokenSpecOutboundService factory = SPEC_OUTBOUND_SERVICES.get(useCase);
        if (factory == null) {
            throw new ServiceTrustException(String.format("No outbound spec service for usecase %s", useCase));
        }
        return factory;
    }

    private FeatureAdoptionFeature getFeatureByUseCase(ServiceTrustUseCase useCase) {
        return FEATURE_ADOPTION_SERVICES.get(useCase);
    }

    protected TokenSpecInboundService getInboundSpecService(ServiceTrustUseCase useCase) {
        TokenSpecInboundService factory = SPEC_INBOUND_SERVICES.get(useCase);
        if (factory == null) {
            throw new ServiceTrustException(String.format("No inbound spec service for usecase %s", useCase));
        }
        return factory;
    }

    private TokenSpec createExchangeTokenSpec(String useCasePath) {
        ServiceTrustUseCase useCase = findUseCase(useCasePath).get();

        ServiceId artifactoryServiceId = accessService.getArtifactoryServiceId();
        TokenSpecInboundService specFactory = getInboundSpecService(useCase);
        return specFactory.createExchangeTokenSpec(artifactoryServiceId, useCasePath);
    }

    private TokenSpec createMasterTokenSpec(String target, String pairingScope) {
        ServiceTrustUseCase useCase = findUseCase(target).get();
        ServiceId artifactoryServiceId = accessService.getArtifactoryServiceId();
        JwtAccessToken token = ((AccessTokenAuthentication) getAuthentication()).getAccessToken();
        TokenExtension extension = JsonUtils.getInstance().readValue(token.getExtension(), TokenExtension.class);
        TokenSpecInboundService specFactory = getInboundSpecService(useCase);
        return specFactory.createMasterTokenSpec(
                new ServiceTrustTokenData(artifactoryServiceId, artifactoryServiceId.getFormattedName(), pairingScope),
                target,
                new ServiceTrustUrls(extension.getBaseUrl(), extension.getPairingUrl(), extension.getExchangeUrl()));
    }

    protected TokenSpec createPairingTokenSpec(ServiceTrustUseCaseData trustData, ServiceTrustUrls urls) {
        ServiceId serviceId = accessService.getArtifactoryServiceId();
        TokenSpecInboundService specFactory = getInboundSpecService(trustData.trustUseCase());
        return specFactory.createPairingTokenSpec(new ServiceTrustTokenData(serviceId, createTokenSubject(serviceId),
                String.format(PAIRING_SCOPE_PATTERN, trustData.path())), trustData.trust(), urls);
    }

    private String createTokenSubject(ServiceId artifactoryServiceId) {
        JwtAccessToken jwtAccessToken = getJwtAccessToken();
        return jwtAccessToken != null ? jwtAccessToken.getSubject() : createSubjectForUsername(artifactoryServiceId);
    }

    private String createSubjectForUsername(ServiceId artifactoryServiceId) {
        String username = UserInfo.ANONYMOUS;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            username = auth.getName();
        }

        SubjectFQN subjectFQN = new SubjectFQN(artifactoryServiceId, SubjectFQN.USERS_NAME_PART, username);
        return subjectFQN.getFormattedName();
    }

    private ExchangeTokenResponse exchange(String useCasePath) {
        TokenSpec exchangeTokenSpec = createExchangeTokenSpec(useCasePath);
        return createToken(exchangeTokenSpec);
    }

    private ExchangeTokenResponse createNewMaster(String target, String pairingScope, String tokenValue) {
        TokenSpec masterTokenSpec = createMasterTokenSpec(target, pairingScope);
        accessService.revokeToken(tokenValue);
        return createToken(masterTokenSpec);
    }

    private ExchangeTokenResponse pairing(String target, List<String> scopes, String tokenValue) {
        String pairingScope = findPairingScope(scopes);
        String useCasePath = getUseCasePathFromScope(pairingScope);
        assertPairingForSingleScopeValid(scopes, target, useCasePath);
        if (scopes.size() == 1 || isRefreshMaster(scopes, target, useCasePath)) {
            return createNewMaster(target, pairingScope, tokenValue);
        } else {
            return exchange(target);
        }
    }

    private boolean isRefreshMaster(List<String> scopes, String target, String useCasePath) {
        return scopes.size() > 1 && target.equals(useCasePath);
    }

    private void assertPairingForSingleScopeValid(List<String> scopes, String target, String useCasePath) {
        if (scopes.size() == 1 && !target.equals(useCasePath)) {
            throw new ServiceTrustException("Target and usecase doesnt match");
        }
    }

    private String getUseCasePathFromScope(String pairingScope) {
        return pairingScope.replace(INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_PREFIX, "")
                .replace(INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_POSTFIX, "");
    }

    private String findPairingScope(List<String> scopes) {
        return scopes.stream()
                .filter(s -> s.startsWith(INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_PREFIX))
                .findFirst()
                .orElseThrow(() -> new ServiceTrustException("Pairing scope not found"));
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        ProxyDescriptor newDefaultProxy = centralConfigService.getDescriptor().getDefaultProxy();
        if (oldDescriptor.getDefaultProxy() != null &&
                oldDescriptor.getDefaultProxy().diff(newDefaultProxy)) {
            return;
        }
        tearDownHttpClient();
    }

    @Override
    public void destroy() {
        tearDownHttpClient();
    }

    @VisibleForTesting
    void tearDownHttpClient() {
        httpClientLock.lock();
        try {
            if (httpClient != null) {
                httpClient.close();
            }
            if (httpClientWithRetry != null) {
                httpClientWithRetry.close();
            }
        } catch (IOException e) {
            log.error("Error while destroying ServiceTrustServiceImpl httpClient.", e);
        } finally {
            httpClient = null;
            httpClientWithRetry = null;
            httpClientLock.unlock();
        }
    }

    @VisibleForTesting
    void initHttpClient() {
        ProxyDescriptor proxy = centralConfigService.getDescriptor().getDefaultProxy();
        httpClient = new HttpClientConfigurator()
                .proxy(proxy)
                .socketTimeout(SOCKET_TIMEOUT)
                .connectionTimeout(CONNECTION_TIMEOUT)
                .noRetry()
                .build();
    }

    @VisibleForTesting
    void initHttpClientWithRetry() {
        ProxyDescriptor proxy = centralConfigService.getDescriptor().getDefaultProxy();
        httpClientWithRetry = new HttpClientConfigurator()
                .proxy(proxy)
                .socketTimeout(SOCKET_TIMEOUT)
                .connectionTimeout(CONNECTION_TIMEOUT)
                .retry(ConstantValues.serviceTrustHttpRequestMaxRetries.getInt())
                .build();
    }
}
