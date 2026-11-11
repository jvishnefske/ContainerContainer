package org.artifactory.addon.retention;

import org.artifactory.addon.license.EdgeBlockedAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.rest.retention.ArtifactRetentionColdRestoreRequest;
import org.artifactory.api.rest.retention.ArtifactRetentionRestoreRequest;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.common.addon.Addon;
import org.artifactory.descriptor.authentication.AuthenticationToken;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicySearchCriteriaDescriptor;
import org.artifactory.entitlement.Entitlement;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.rest.exception.MissingRestAddonException;
import org.artifactory.storage.StorageSummary;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Tamar Rosen
 */
@EdgeBlockedAddon
@Entitlement(EntitlementType.JFRT)
public interface ArtifactRetentionAddon extends Addon {

    default void triggerPolicy(String policyKey, BasicStatusHolder statusHolder) {
        throw new MissingRestAddonException();
    }

    default ArtifactRetentionPolicyModel getPolicy(String policyKey) {
        throw new MissingRestAddonException();
    }

    default ArtifactRetentionPolicyModel addPolicy(String policyKey, ArtifactRetentionPolicyBaseModel policy) {
        throw new MissingRestAddonException();
    }

    default ArtifactRetentionPolicyModel updatePolicy(String policyKey, ArtifactRetentionPolicyBaseModel newPolicy) {
        throw new MissingRestAddonException();
    }

    default void deletePolicy(String policyKey) {
        throw new MissingRestAddonException();
    }

    default ArtifactRetentionRestoreResponseModel restore(ArtifactRetentionRestoreRequest restoreRequest,
            BasicStatusHolder statusHolder) throws IOException {
        throw new MissingRestAddonException();
    }

    default ArtifactRetentionPreviewSummary previewByPolicyKey(String policyKey) {
        throw new MissingRestAddonException();
    }

    default ArtifactRetentionPreviewSummary previewByPolicy(ArtifactRetentionPolicyBaseModel policy) {
        throw new MissingRestAddonException();
    }

    default ArtifactRetentionRestoreResponseModel executeColdRestore(
            ArtifactRetentionColdRestoreRequest coldRestoreRequest) {
        throw new MissingRestAddonException();
    }

    default Map<String, String> getCreateBinaryHeaders(String repoKey) {
        return Collections.emptyMap();
    }

    default Map<String, String> getInitiateBinaryRestoreHeaders() {
        return Collections.emptyMap();
    }

    default Map<String, String> getIsBinaryRestoredHeaders() {
        return Collections.emptyMap();
    }

    default List<ArtifactRetentionPolicyModel> getPolicies() {
        throw new MissingRestAddonException();
    }

    default RetentionStats getArtifactRetentionStats() {
        return null;
    }

    default boolean isColdInstance() {
        return false;
    }

    default boolean isColdInstanceV1orV2() {
        return false;
    }

    default AqlRestResult searchOnColdInstance(String aqlStr, BasicStatusHolder statusHolder) {
        throw new MissingRestAddonException();
    }

    default AqlRestResult searchPackagesOnColdInstance(PackageArchiveSearchCriteria criteria,
            BasicStatusHolder statusHolder, String projectKey) {
        throw new MissingRestAddonException();
    }

    default AqlRestResult searchArtifactsOnColdInstance(ArtifactsArchiveSearchCriteria artifactsSearchCriteria,
            BasicStatusHolder statusHolder, String projectKey) {
        throw new MissingRestAddonException();
    }

    default String generateJFrogColdStorageNamespace() {
        return null;
    }

    default String getJfrogColdStorageArchivingNamespace() {
        return null;
    }

    default List<ArtifactRetentionArchiveExecutionStatus> getArchivePoliciesExecutions(Long extractLongEpoch,
            Long extractLongEpoch1, String searchStr) {
        throw new MissingRestAddonException();
    }

    default List<ArtifactRetentionRestoreExecutionStatus> getRestorePoliciesExecutions(Long extractLongEpoch,
            Long extractLongEpoch1) {
        throw new MissingRestAddonException();
    }

    default StorageSummary getArchiveStorageSummary() {
        throw new MissingRestAddonException();
    }

    default ArtifactRetentionLinkModel getArchiveStorageLink() {
        throw new MissingRestAddonException();
    }

    default boolean isRetentionEnabled() {
        return false;
    }

    default void saveNamespaceAndMasterToken(String baseUrl, String exchangeUrl, String namespace, String tokenId,
            String accessToken, String tokenType, Long expiresIn, String scope) {
        throw new MissingRestAddonException();
    }

    default void markPolicyExecutionAsStopped(String policyKey, BasicStatusHolder statusHolder) {
        throw new MissingRestAddonException();
    }

    default AqlBase convertSearchCriteriaFormToAql(
            ArtifactRetentionPolicySearchCriteriaDescriptor searchCriteriaDescriptor) {
        throw new MissingRestAddonException();
    }

    default void updateMasterToken(String tokenId, String accessToken, String tokenType, Long expiresIn, String scope) {
        throw new MissingRestAddonException();
    }

    default List<String> getNamespacedArchiveRepositories() {
        throw new MissingRestAddonException();
    }

    default List<String> getColdStorageArchiveRepositories(String namespace) {
        throw new MissingRestAddonException();
    }

    default Optional<AuthenticationToken> getJfrogColdStorageAuthenticationToken(String baseUrl) {
        throw new MissingRestAddonException();
    }

    default Collection<AuthenticationToken> getColdStoragePairAuthenticationTokens() {
        throw new MissingRestAddonException();
    }

    default void deleteJfrogColdStorageMasterToken(String baseUrl) {
        throw new MissingRestAddonException();
    }

    default void refreshMasterToken() {
        throw new MissingRestAddonException();
    }

    default ArtifactRetentionRestoreRenderResponseModel getRestoreRenderStatusByOperationId(String restoreOperationId) {
        throw new MissingRestAddonException();
    }

    default List<String> getSupportedPackages() {
        throw new MissingRestAddonException();
    }
}