package org.artifactory.servicetrust.federation;

import jakarta.annotation.Nonnull;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.security.access.GenericTokenSpec;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.servicetrust.ServiceTrustTokenData;
import org.artifactory.servicetrust.ServiceTrustUrls;
import org.artifactory.servicetrust.ServiceTrustUseCase;
import org.artifactory.servicetrust.TokenExtension;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.app.service.trust.dto.ExchangeTokenRequest;
import org.jfrog.common.JsonUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.INTERNAL_SCOPE_PREFIX;

@Slf4j
@NoArgsConstructor
public class UnidirectionalFederationTokenSpecService extends FederatedRepositoryTokenSpecService {

    public static final String PAIRING_TOKEN_UNI_FEDERATION_SCOPE_MISSING_ERROR =
            "Pairing token does not have unidirectional federation related scope";

    public static final String READ_UNIDIRECTIONAL_ALLOWED_SCOPE = "r";

    public static final UnidirectionalFederationTokenSpecService UNIDIRECTIONAL_FEDERATION_TOKEN_SPEC_SERVICE =
            new UnidirectionalFederationTokenSpecService();

    @Override
    public TokenSpec<GenericTokenSpec> createExchangeTokenSpec(ServiceId artifactoryServiceId, String useCasePath) {
        String subject = artifactoryServiceId.getFormattedName();
        String requiredScope = createRequiredInternalScope(useCasePath);

        return GenericTokenSpec.create(subject)
                .refreshable(false)
                .audience(List.of(subject))
                .scope(List.of(READ_UNIDIRECTIONAL_ALLOWED_SCOPE, requiredScope))
                .expiresIn(DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS)
                .extension(JsonUtils.getInstance().valueToString(TokenExtension.REVOCABLE_TOKEN));
    }

    @Override
    public TokenSpec<GenericTokenSpec> createMasterTokenSpec(@NonNull ServiceTrustTokenData tokenData,
            @NonNull String useCasePath, @NonNull ServiceTrustUrls urls) {
        // no admin scope since this is a unidirectional federation
        // whoever holds this master token may only read from the remote member
        String requiredScope = createRequiredInternalScope(useCasePath);
        List<String> scopes = List.of(requiredScope, tokenData.scope());
        TokenExtension newExtension = new TokenExtension(urls.base(), urls.exchange(), urls.pairing(), true);

        return GenericTokenSpec.create(tokenData.subject())
                .refreshable(true)
                .audience(Collections.singletonList(tokenData.subject()))
                .scope(scopes)
                .expiresIn(DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS)
                .extension(JsonUtils.getInstance().valueToString(newExtension));
    }

    @Override
    public ExchangeTokenRequest createMasterTokenExchangeRefreshRequest(String subject) {
        return new ExchangeTokenRequest(subject,
                ServiceTrustUseCase.FEDERATED_REPO_READ.getName() + "/" + READ_UNIDIRECTIONAL_ALLOWED_SCOPE);
    }

    @Override
    public ExchangeTokenRequest createExchangeTokenRequest(String subject, JwtAccessToken jwt) {
        String scopedUseCase = getUseCaseFromPairScope(jwt.getScope());
        return new ExchangeTokenRequest(subject, scopedUseCase);
    }

    @Override
    public ExchangeTokenRequest createScopedTokenExchangeRequest(String subject, String... scopes) {
        String target = Arrays.stream(scopes)
                .map(scp -> createRequiredInternalScope("federated-repo/" + scp + ":r"))
                .collect(Collectors.joining(" "));
        return new ExchangeTokenRequest(subject, target);
    }

    @Nonnull
    @Override
    public String createRequiredInternalScope(@Nonnull String useCasePath) {
        if (!ServiceTrustUseCase.FEDERATED_REPO_READ.matches(useCasePath)) {
            throw new IllegalArgumentException(TOKEN_SCOPE_DOESNT_MATCH_FEDERATED_REPO_LEGAL_PATTERNS);
        }

        if (useCasePath.startsWith(INTERNAL_SCOPE_PREFIX)) {
            return useCasePath;
        }

        return INTERNAL_SCOPE_PREFIX + useCasePath;
    }

    @NonNull
    @Override
    protected String getUseCasePathForScope(@NonNull String scope) {
        return getUseCaseFromPairScope(Collections.singletonList(scope));
    }

    @NonNull
    @Override
    protected String getUseCaseFromPairScope(@NonNull List<String> scopes) {
        assertScopesExist(scopes);
        return scopes
                .stream()
                .filter(s -> s.contains(ServiceTrustUseCase.FEDERATED_REPO_READ.getName()))
                .findFirst()
                .map(this::getSimpleScope)
                .orElseThrow(() -> new IllegalArgumentException(PAIRING_TOKEN_UNI_FEDERATION_SCOPE_MISSING_ERROR));
    }
}
