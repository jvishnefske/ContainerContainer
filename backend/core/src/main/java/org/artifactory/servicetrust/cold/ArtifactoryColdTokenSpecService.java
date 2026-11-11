package org.artifactory.servicetrust.cold;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.access.GenericTokenSpec;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.descriptor.authentication.AuthenticationToken;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.servicetrust.ServiceTrust;
import org.artifactory.servicetrust.ServiceTrustToken;
import org.artifactory.servicetrust.ServiceTrustTokenData;
import org.artifactory.servicetrust.ServiceTrustUrls;
import org.artifactory.servicetrust.ServiceTrustUseCase;
import org.artifactory.servicetrust.TokenExtension;
import org.artifactory.servicetrust.TokenSpecOutboundService;
import org.artifactory.servicetrust.TokenSpecServiceInboundBase;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.retention.service.packages.PackagePolicyStorageService;
import org.artifactory.storage.db.policy.service.PolicyRunSummaryService;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.app.service.trust.dto.ExchangeTokenRequest;
import org.jfrog.app.service.trust.dto.ExchangeTokenResponse;
import org.jfrog.app.service.trust.error.ServiceTrustException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.artifactory.addon.retention.ArtifactRetentionConstants.INSTANCES_ALREADY_PAIRED;
import static org.artifactory.servicetrust.ServiceTrustService.INTERNAL_SCOPE_PREFIX;

/**
 * @author volodymyrm
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ArtifactoryColdTokenSpecService extends TokenSpecServiceInboundBase
        implements TokenSpecOutboundService {

    public static final ArtifactoryColdTokenSpecService ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE =
            new ArtifactoryColdTokenSpecService();

    // Policy-related error messages
    private static final String REVOKING_ARCHIVE_POLICY_RUNNING =
            "Revoking a binding is currently not allowed because an archive policy is running. Please" +
                    " allow the process to complete and make sure all active policies are disabled " +
                    "before attempting to revoke again.";
    private static final String REVOKING_ACTIVE_POLICIES =
            "Revoking a binding is currently not allowed, as there are active archive " +
                    "policies. Please disable all active policies before attempting to revoke again.";

    @Override
    public TokenSpec<GenericTokenSpec> createPairingTokenSpec(@NonNull ServiceTrustTokenData tokenData,
            @NonNull ServiceTrust trust,
            @NonNull ServiceTrustUrls urls) {
        verifyIsColdStorageEnabled();
        return super.createPairingTokenSpec(tokenData, trust, urls);
    }

    @NonNull
    @Override
    protected String getUseCasePathForScope(@NonNull String scope) {
        String useCasePath = getUseCaseFromPairScope(Collections.singletonList(scope));
        if (ServiceTrustUseCase.ARTIFACTORY_COLD.matches(useCasePath)) {
            useCasePath = generateIceUseCaseWithNamespace(useCasePath);
        }
        return useCasePath;
    }

    @Override
    public TokenSpec<GenericTokenSpec> createMasterTokenSpec(@NonNull ServiceTrustTokenData tokenData,
            @NonNull String useCasePath, @NonNull ServiceTrustUrls urls) {
        verifyIsColdStorageEnabled();
        return super.createMasterTokenSpec(tokenData, useCasePath, urls);
    }

    @Override
    public TokenSpec<GenericTokenSpec> createExchangeTokenSpec(ServiceId artifactoryServiceId, String useCasePath) {
        verifyIsColdStorageEnabled();
        return super.createExchangeTokenSpec(artifactoryServiceId, useCasePath);
    }

    @Override
    public ExchangeTokenRequest createExchangeTokenRequest(String subject, JwtAccessToken jwt) {
        verifyIsWarmStorageEnabled();
        String updatedUseCasePath = getUseCaseFromPairScope(jwt.getScope());
        return new ExchangeTokenRequest(subject, updatedUseCasePath);
    }

    @Override
    public void storeMasterTokenData(ExchangeTokenResponse response, TokenExtension extension) {
        verifyIsWarmStorageEnabled();
        assertInstanceNotPaired();
        String scope = response.getScope();
        assertScopeExists(scope);
        String[] tokens = scope.split(" ");
        String tokenWithNamespace = findFirstRetentionToken(Arrays.stream(tokens),
                "Master token scope with the 'artifactory-cold' scope does not exist");
        String namespace = tokenWithNamespace
                .replace(ServiceTrustUseCase.ARTIFACTORY_COLD.getName() + "/", "")
                .replace(ServiceTrustUseCase.ARTIFACTORY_COLD.getName(), "")
                .replace(INTERNAL_SCOPE_PREFIX, "");

        if (StringUtils.isEmpty(namespace)) {
            throw new IllegalArgumentException("Can not parse the master token scope to get a cold namespace: " +
                    "namespace is empty");
        }
        getArtifactRetentionAddon().saveNamespaceAndMasterToken(extension.getBaseUrl(), extension.getExchangeUrl(),
                namespace, response.getTokenId(), response.getAccessToken(), response.getTokenType(),
                response.getExpiresIn(), response.getScope());
    }

    private void assertInstanceNotPaired() {
        if (!getArtifactRetentionAddon().getColdStoragePairAuthenticationTokens().isEmpty()) {
            throw new ArtifactRetentionBindException(INSTANCES_ALREADY_PAIRED);
        }
    }

    private void assertScopeExists(String scope) {
        if (StringUtils.isBlank(scope)) {
            throw new IllegalArgumentException("Master token does not have scope");
        }
    }

    @Override
    public void updateMasterTokenData(ExchangeTokenResponse response) {
        verifyIsWarmStorageEnabled();
        getArtifactRetentionAddon().updateMasterToken(response.getTokenId(), response.getAccessToken(),
                response.getTokenType(), response.getExpiresIn(), response.getScope());
    }

    @Override
    public ExchangeTokenRequest createMasterTokenExchangeRefreshRequest(@NonNull String subject) {
        verifyIsWarmStorageEnabled();
        String namespace = getArtifactRetentionAddon().getJfrogColdStorageArchivingNamespace();
        if (StringUtils.isEmpty(namespace)) {
            throw new IllegalStateException("Can not create exchange request to refresh master token: " +
                    "namespace does not exist");
        }
        return new ExchangeTokenRequest(subject,
                String.join("/", ServiceTrustUseCase.ARTIFACTORY_COLD.getName(), namespace));
    }

    @Override
    public ExchangeTokenRequest createScopedTokenExchangeRequest(@NonNull String subject, String... scopes) {
        verifyIsWarmStorageEnabled();
        String namespace = getArtifactRetentionAddon().getJfrogColdStorageArchivingNamespace();
        if (StringUtils.isEmpty(namespace)) {
            throw new ServiceTrustException("Can not create exchange request to refresh master token: " +
                    "namespace does not exist");
        }
        String target =
                String.format("%s/%s:%s", ServiceTrustUseCase.ARTIFACTORY_COLD.getName(), namespace, LIMITED_SCOPE);
        return new ExchangeTokenRequest(subject, target);
    }

    @Override
    public Optional<String> getAuthenticationToken(String baseUrl) {
        verifyIsWarmStorageEnabled();
        return getArtifactRetentionAddon().getJfrogColdStorageAuthenticationToken(baseUrl)
                .map(AuthenticationToken::getAccessToken);
    }

    @Override
    public Collection<ServiceTrustToken> getPairedInstances() {
        return getArtifactRetentionAddon().getColdStoragePairAuthenticationTokens().stream()
                .map(this::toServiceTrustToken).toList();
    }

    @Override
    public void deleteAuthenticationToken(String baseUrl) {
        verifyIsWarmStorageEnabled();
        getArtifactRetentionAddon().deleteJfrogColdStorageMasterToken(baseUrl);
    }

    @Override
    public boolean isForcedUnpairingAllowed() throws ServiceTrustException {
        checkRunningPolicies();
        checkEnabledPoliciesWithCronExpression();
        return true;
    }

    /**
     * Extract a use case with namespace 'artifactory-cold/namespace' from scopes
     */
    @Override
    @NonNull
    protected String getUseCaseFromPairScope(@NonNull List<String> scope) {
        assertScopesExist(scope);
        return findFirstRetentionToken(scope.stream(), "Pair's token does not have retention related scope");
    }

    private String findFirstRetentionToken(@NonNull Stream<String> tokens, String exceptionMessage) {
        return tokens
                .filter(t -> t.contains(ServiceTrustUseCase.ARTIFACTORY_COLD.getName()))
                .findFirst()
                .map(this::getSimpleScope)
                .orElseThrow(() -> new IllegalArgumentException(exceptionMessage));
    }

    private void verifyIsColdStorageEnabled() {
        ArtifactRetentionAddon artifactRetentionAddon = getArtifactRetentionAddon();
        if (!artifactRetentionAddon.isColdInstanceV1orV2()) {
            throw new IllegalStateException("Cold Storage mode is disabled");
        }
    }

    private void verifyIsWarmStorageEnabled() {
        ArtifactRetentionAddon artifactRetentionAddon = getArtifactRetentionAddon();
        if (artifactRetentionAddon.isColdInstanceV1orV2()) {
            throw new IllegalStateException("Instance running in the Cold Storage mode");
        }
    }

    private ArtifactRetentionAddon getArtifactRetentionAddon() {
        return ContextHelper.get().beanForType(AddonsManager.class)
                .addonByType(ArtifactRetentionAddon.class);
    }

    /**
     * Generates use case with namespace for the retention feature. It should look like 'artifactory-cold/namespace'
     */
    @NonNull
    private String generateIceUseCaseWithNamespace(@NonNull String useCaseWithoutNamespace) {
        ArtifactRetentionAddon artifactRetentionAddon = getArtifactRetentionAddon();
        String namespace = artifactRetentionAddon.generateJFrogColdStorageNamespace();
        return String.join("/", useCaseWithoutNamespace, namespace);
    }

    /**
     * Check for any running policies (archive or cleanup)
     *
     * @throws ServiceTrustException if any policies are running
     */
    private void checkRunningPolicies() throws ServiceTrustException {
        var policyRunSummaryService = ContextHelper.get().beanForType(PolicyRunSummaryService.class);
        try {
            // Check for running archive policies
            if (!policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE).isEmpty()) {
                throw new ServiceTrustException(REVOKING_ARCHIVE_POLICY_RUNNING);
            }
        } catch (StorageException e) {
            throw new ServiceTrustException("Failed to check running policies");
        }
    }

    /**
     * Check for enabled policies with cron schedules
     *
     * @throws ServiceTrustException if there are active policies with cron schedules
     */
    private void checkEnabledPoliciesWithCronExpression() throws ServiceTrustException {
        try {
            var packagePolicyStorage = ContextHelper.get().beanForType(PackagePolicyStorageService.class);
            boolean hasActivePolicies = packagePolicyStorage.getAll(PolicyType.ARCHIVE, PolicyItemType.PACKAGE).stream()
                    .anyMatch(policy -> policy.enabled() && StringUtils.isNotBlank(policy.cronExp()));

            if (hasActivePolicies) {
                throw new ServiceTrustException(REVOKING_ACTIVE_POLICIES);
            }
        } catch (StorageException e) {
            throw new ServiceTrustException("Failed to check for active policies with cron schedules");
        }
    }
}