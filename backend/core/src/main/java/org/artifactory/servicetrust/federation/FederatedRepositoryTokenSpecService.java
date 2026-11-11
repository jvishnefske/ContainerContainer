package org.artifactory.servicetrust.federation;

import jakarta.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.federated.FederatedRepoAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.access.GenericTokenSpec;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.authentication.AuthenticationToken;
import org.artifactory.servicetrust.ServiceTrustToken;
import org.artifactory.servicetrust.ServiceTrustUseCase;
import org.artifactory.servicetrust.TokenExtension;
import org.artifactory.servicetrust.TokenSpecOutboundService;
import org.artifactory.servicetrust.TokenSpecServiceInboundBase;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.JwtAccessTokenImpl;
import org.jfrog.app.service.trust.dto.ExchangeTokenRequest;
import org.jfrog.app.service.trust.dto.ExchangeTokenResponse;
import org.jfrog.common.JsonUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.INTERNAL_SCOPE_PREFIX;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class FederatedRepositoryTokenSpecService extends TokenSpecServiceInboundBase
        implements TokenSpecOutboundService {
    public static final String FEDERATED_BINARY_PROVIDER_USE_CASE = "$federated-binary-provider$";

    public static final FederatedRepositoryTokenSpecService FEDERATED_REPO_TOKEN_SPEC_SERVICE =
            new FederatedRepositoryTokenSpecService();
    public static final String PAIRING_TOKEN_FEDERATION_SCOPE_MISSING_ERROR =
            "Pairing token does not have federation related scope";
    public static final String TOKEN_SCOPE_DOESNT_MATCH_FEDERATED_REPO_LEGAL_PATTERNS =
            "Token internal scope doesn't match federated-repo legal patterns";

    @NonNull
    @Override
    protected String getUseCasePathForScope(@NonNull String scope) {
        return getUseCaseFromPairScope(Collections.singletonList(scope));
    }

    /**
     * Extract a use case with name 'federated-repo/*' from scopes
     */
    @Override
    @NonNull
    protected String getUseCaseFromPairScope(@NonNull List<String> scopes) {
        assertScopesExist(scopes);
        return scopes
                .stream()
                .filter(s -> s.contains(ServiceTrustUseCase.FEDERATED_REPO.getName()))
                .findFirst()
                .map(this::getSimpleScope)
                .orElseThrow(() -> new IllegalArgumentException(PAIRING_TOKEN_FEDERATION_SCOPE_MISSING_ERROR));

    }

    @Nonnull
    @Override
    public String createRequiredInternalScope(@Nonnull String useCasePath) {
        if (!ServiceTrustUseCase.FEDERATED_REPO.matches(useCasePath)) {
            throw new IllegalArgumentException(TOKEN_SCOPE_DOESNT_MATCH_FEDERATED_REPO_LEGAL_PATTERNS);
        }

        if (useCasePath.startsWith(INTERNAL_SCOPE_PREFIX)) {return useCasePath;}

        if (useCasePath.equals("artifact:*:r")) {
            return useCasePath;
        }

        return INTERNAL_SCOPE_PREFIX + useCasePath;
    }

    @Override
    public ExchangeTokenRequest createExchangeTokenRequest(String subject, JwtAccessToken jwt) {
        String scopedUseCase = getUseCaseFromPairScope(jwt.getScope());
        return new ExchangeTokenRequest(subject, scopedUseCase);
    }

    @Override
    public ExchangeTokenRequest createMasterTokenExchangeRefreshRequest(String subject) {
        return new ExchangeTokenRequest(subject, ServiceTrustUseCase.FEDERATED_REPO.getName() + "/*");
    }

    @Override
    public void storeMasterTokenData(ExchangeTokenResponse response, TokenExtension extension) {
        // TODO[ts]: Encrypt every string
        getFederatedRepoAddon().saveAuthenticationMasterToken(response.getTokenId(), response.getTokenType(),
                response.getScope(), extension.getBaseUrl(), extension.getExchangeUrl(),
                response.getAccessToken(), response.getExpiresIn());
    }

    @Override
    public void updateMasterTokenData(ExchangeTokenResponse response) {

        JwtAccessToken jwtAccessToken = JwtAccessTokenImpl.parseTokenValue(response.getAccessToken());
        TokenExtension tokenExtension =
                JsonUtils.getInstance().readValue(jwtAccessToken.getExtension(), TokenExtension.class);

        log.info("Updating refreshed master token of remote member: {}", tokenExtension.getBaseUrl());
        getFederatedRepoAddon().saveAuthenticationMasterToken(
                response.getTokenId(),
                response.getTokenType(),
                response.getScope(),
                tokenExtension.getBaseUrl(),
                tokenExtension.getExchangeUrl(),
                response.getAccessToken(),
                response.getExpiresIn());
    }

    @Override
    public ExchangeTokenRequest createScopedTokenExchangeRequest(String subject, String... scopes) {
        String target = Arrays.stream(scopes)
                .map(scp -> scp.contains("full-sync") || scp.contains("statistic") ? "artifact:*:r"
                        : createRequiredInternalScope("federated-repo/" + scp + ":x"))
                .collect(Collectors.joining(" "));
        return new ExchangeTokenRequest(subject, target);
    }

    @Override
    public Collection<ServiceTrustToken> getPairedInstances() {
        return getFederatedRepoAddon().getPairAuthenticationTokens().stream().map(this::toServiceTrustToken).toList();
    }

    @Override
    public TokenSpec<GenericTokenSpec> createExchangeTokenSpec(ServiceId artifactoryServiceId, String useCasePath) {
        String subject = artifactoryServiceId.getFormattedName();
        boolean limitScope = ConstantValues.federatedExchangeTokenLimitScopeEnabled.getBoolean();

        // This is a special token handling cases where binary-provider needs access to remote federation member.
        // Note: the use of $...$ is to avoid using exact repository name: 'federated-binary-provider'
        List<String> scopes;
        if (useCasePath.contains(FEDERATED_BINARY_PROVIDER_USE_CASE)) {
            scopes = limitScope ? List.of("r") : List.of("r", "w", "d", "m");
        } else {
            scopes = List.of(createRequiredInternalScope(useCasePath));
        }

        long expirationHours = ConstantValues.federatedExchangeTokenExpirationTimeHours.getLong();
        if (expirationHours <= 0) {
            expirationHours = DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS;
            log.debug("Exchange token expiration hours must be greater than zero, " +
                    "setting to default value: {}", expirationHours);
        }

        return GenericTokenSpec.create(subject)
                .refreshable(false)
                .audience(List.of(subject))
                .scope(scopes)
                .expiresIn(expirationHours)
                .extension(JsonUtils.getInstance().valueToString(TokenExtension.REVOCABLE_TOKEN));
    }

    @Override
    public void deleteAuthenticationToken(String baseUrl) {
        getFederatedRepoAddon().deleteAuthenticationToken(baseUrl);
    }

    @Override
    public Optional<String> getAuthenticationToken(String baseUrl) {
        return getFederatedRepoAddon().getAuthenticationToken(baseUrl).map(AuthenticationToken::getAccessToken);
    }

    @Override
    public boolean isForcedUnpairingAllowed() {
        return true;
    }

    private FederatedRepoAddon getFederatedRepoAddon() {
        return ContextHelper.get().beanForType(AddonsManager.class).addonByType(FederatedRepoAddon.class);
    }
}
