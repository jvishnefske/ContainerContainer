package org.artifactory.servicetrust.distribution;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.security.access.GenericTokenSpec;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.servicetrust.ServiceTrustTokenData;
import org.artifactory.servicetrust.ServiceTrustUrls;
import org.artifactory.servicetrust.ServiceTrustUseCase;
import org.artifactory.servicetrust.TokenExtension;
import org.artifactory.servicetrust.TokenSpecServiceInboundBase;
import org.artifactory.util.bundles.BundleConfigurationUtil;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.access.common.ScopeContainer;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.common.v2.Action;
import org.jfrog.common.JsonUtils;

import java.util.Collections;
import java.util.List;

import static org.artifactory.servicetrust.ServiceTrustService.APPLIED_PERMISSIONS_SERVICE_PATTERN;
import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DistributionTokenSpecService extends TokenSpecServiceInboundBase {

    public static final String DISTRIBUTION_GENERIC_TOKEN_PATTERN = "^distribution$";
    public static final String DISTRIBUTE_TOKEN_PATTERN = "^distribute/.*";
    public static final String DISTRIBUTION_SPECIFIC_SCOPE_TOKEN_PATTERN = "^distribution/.*";

    public static final DistributionTokenSpecService DISTRIBUTION_TOKEN_SPEC_SERVICE =
            new DistributionTokenSpecService();

    public static final PermissionScope INTRANSIT_SCOPE_TOKEN = PermissionScope.create(
            PermissionScopeResourceType.INTERNAL, "_intransit", "admin", Action.EXECUTE);

    public static final PermissionScope RELEASE_BUNDLES_V2_JFDS_SCOPE_TOKEN =
            PermissionScope.create(PermissionScopeResourceType.INTERNAL,
                    "*" + BundleConfigurationUtil.DEFAULT_RBV2_DISTRIBUTION_REPO, "admin",
                    Action.EXECUTE);

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
                .filter(scope -> scope.contains(ServiceTrustUseCase.DISTRIBUTION.getName()))
                .findFirst()
                .map(this::getSimpleScope)
                .orElseThrow(() -> new IllegalArgumentException("Pair's token scope is missing"));
    }

    @Override
    @NonNull
    public TokenSpec<GenericTokenSpec> createMasterTokenSpec(@NonNull ServiceTrustTokenData tokenData,
            @NonNull String useCasePath, @NonNull ServiceTrustUrls urls) {
        TokenSpec<GenericTokenSpec> spec = GenericTokenSpec.create(tokenData.subject());
        spec.audience(Collections.singletonList(tokenData.subject()));
        spec.expiresIn(DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS);
        String appliedPermissionsScope = String.format(APPLIED_PERMISSIONS_SERVICE_PATTERN,
                ServiceTrustUseCase.DISTRIBUTION.getName());
        spec.scope(List.of(appliedPermissionsScope, tokenData.scope()));
        spec.refreshable(true);
        TokenExtension newExtension = new TokenExtension(urls.base(), urls.exchange(), urls.pairing(), true);
        String extension = JsonUtils.getInstance().valueToString(newExtension);
        spec.extension(extension);
        log.debug("Creating master token spec: {}", spec);
        return spec;
    }

    @Override
    @NonNull
    public TokenSpec<GenericTokenSpec> createExchangeTokenSpec(
            @NonNull ServiceId artifactoryServiceId,
            @NonNull String useCasePath) {
        String subject = artifactoryServiceId.getFormattedName();
        TokenSpec<GenericTokenSpec> spec = GenericTokenSpec.create(subject);
        spec.scope(useCasePathToScopes(useCasePath));
        spec.audience(List.of(artifactoryServiceId.getFormattedName()));
        spec.expiresIn(DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS);
        spec.refreshable(false);

        log.debug("Creating exchange token spec: {}", spec);
        return spec;
    }

    @NonNull
    private List<String> useCasePathToScopes(@NonNull String useCasePath) {
        if ((useCasePath).matches(DISTRIBUTE_TOKEN_PATTERN)) {
            return List.of(ScopeContainer.ADMIN_PERMISSION,
                    INTRANSIT_SCOPE_TOKEN.toString(),
                    RELEASE_BUNDLES_V2_JFDS_SCOPE_TOKEN.toString());
        } else if ((useCasePath).matches(DISTRIBUTION_SPECIFIC_SCOPE_TOKEN_PATTERN)) {
            return List.of(
                    PermissionScope.create(PermissionScopeResourceType.INTERNAL, useCasePath,
                            Action.ANNOTATE).toString());
        } else {
            throw new IllegalArgumentException("Unsupported useCasePath specified: " + useCasePath);
        }
    }
}
