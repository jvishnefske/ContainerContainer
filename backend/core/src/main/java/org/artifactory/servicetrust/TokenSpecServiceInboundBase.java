package org.artifactory.servicetrust;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.access.GenericTokenSpec;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.descriptor.authentication.AuthenticationToken;
import org.artifactory.util.CollectionUtils;
import org.jfrog.access.common.ServiceId;
import org.jfrog.common.JsonUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_PAIRING_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.INTERNAL_SCOPE_PREFIX;
import static org.artifactory.servicetrust.ServiceTrustService.INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_POSTFIX;
import static org.artifactory.servicetrust.ServiceTrustService.INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_PREFIX;
import static org.artifactory.servicetrust.ServiceTrustService.PAIRING_SCOPE_PATTERN;
import static org.jfrog.access.common.AccessAuthz.ADMIN;

/**
 * @author volodymyrm
 * @author bilali
 * @author Tomer Slutsky
 */
@Slf4j
public abstract class TokenSpecServiceInboundBase implements TokenSpecInboundService {
    protected static final String LIMITED_SCOPE = "scoped";

    public static final String PAIRING_TOKEN_SCOPES_NULL_OR_EMPTY_ERROR =
            "Pairing token scopes can not be null or empty";

    @Override
    public TokenSpec<GenericTokenSpec> createPairingTokenSpec(@NonNull ServiceTrustTokenData tokenData,
            @NonNull ServiceTrust trust,
            @NonNull ServiceTrustUrls urls) {
        String useCasePath = getUseCasePathForScope(tokenData.scope());
        String updatedScope = String.format(PAIRING_SCOPE_PATTERN, useCasePath);
        TokenSpec<GenericTokenSpec> spec = GenericTokenSpec.create(tokenData.subject());
        spec.audience(Collections.singletonList(tokenData.artifactoryServiceId().getFormattedName()));
        spec.expiresIn(DEFAULT_PAIRING_TOKEN_EXPIRATION_TIME_IN_SECS);
        spec.scope(Collections.singletonList(updatedScope));
        spec.refreshable(false);
        TokenExtension tokenExtension = new TokenExtension(urls.base(), urls.exchange(), urls.pairing(), true);
        String extension = JsonUtils.getInstance().valueToString(tokenExtension);
        spec.extension(extension);
        return spec;
    }

    @NonNull
    protected abstract String getUseCasePathForScope(@NonNull String scope);

    @NonNull
    protected abstract String getUseCaseFromPairScope(@NonNull List<String> scopes);

    @Override
    public TokenSpec<GenericTokenSpec> createMasterTokenSpec(@NonNull ServiceTrustTokenData tokenData,
            @NonNull String useCasePath, @NonNull ServiceTrustUrls urls) {
        TokenSpec<GenericTokenSpec> spec = GenericTokenSpec.create(tokenData.subject());
        spec.audience(Collections.singletonList(tokenData.subject()));
        spec.expiresIn(DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS);
        String adminScope = tokenData.subject() + ":" + ADMIN;
        String requiredScope = createRequiredInternalScope(useCasePath);
        spec.scope(Arrays.asList(adminScope, requiredScope, tokenData.scope()));
        spec.refreshable(true);
        TokenExtension newExtension = new TokenExtension(urls.base(), urls.exchange(), urls.pairing(), true);
        String extension = JsonUtils.getInstance().valueToString(newExtension);
        spec.extension(extension);
        return spec;
    }

    @Override
    @NonNull
    public String createRequiredInternalScope(@NonNull String useCasePath) {
        if (useCasePath.startsWith(INTERNAL_SCOPE_PREFIX)) {
            return useCasePath;
        }

        return INTERNAL_SCOPE_PREFIX + useCasePath;
    }

    public TokenSpec<GenericTokenSpec> createExchangeTokenSpec(ServiceId artifactoryServiceId, String useCasePath) {
        String subject = artifactoryServiceId.getFormattedName();
        TokenSpec<GenericTokenSpec> spec = GenericTokenSpec.create(subject);
        spec.audience(List.of(artifactoryServiceId.getFormattedName()));
        spec.expiresIn(DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS);
        String adminScope = artifactoryServiceId.getFormattedName() + ":" + ADMIN;
        String requiredScope = createRequiredInternalScope(useCasePath);
        List<String> scopeList = List.of(adminScope, requiredScope);
        spec.scope(scopeList);
        spec.refreshable(false);
        String extension = JsonUtils.getInstance().valueToString(TokenExtension.REVOCABLE_TOKEN);
        spec.extension(extension);
        return spec;
    }

    @NonNull
    protected String getSimpleScope(String fullScope) {
        return fullScope
                .replace(INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_PREFIX, "")
                .replace(INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_POSTFIX, "");
    }

    protected String encrypt(String toEncrypt) {
        return CryptoHelper.encryptIfNeeded(ContextHelper.get().getArtifactoryHome(), toEncrypt);
    }

    protected void assertScopesExist(List<String> scopes) {
        if (CollectionUtils.isNullOrEmpty(scopes)) {
            throw new IllegalArgumentException(PAIRING_TOKEN_SCOPES_NULL_OR_EMPTY_ERROR);
        }
    }

    protected ServiceTrustToken toServiceTrustToken(AuthenticationToken token) {
        return token == null ? null : ServiceTrustToken.builder()
                .accessToken(token.getAccessToken())
                .baseUrl(token.getBaseUrl())
                .tokenId(token.getTokenId())
                .tokenType(token.getTokenType())
                .exchangeUrl(token.getExchangeUrl())
                .expirationInMillis(token.getExpirationInMillis())
                .lastRefreshDateMillis(token.getLastRefreshDateMillis())
                .scope(token.getScope())
                .build();
    }
}
