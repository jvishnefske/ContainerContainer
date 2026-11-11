package org.artifactory.rest.services.retention;

import org.springframework.beans.factory.annotation.Lookup;

/**
 * @author Omri Ziv
 */
public abstract class ArtifactRetentionArchiveServiceFactory {

    @Lookup
    public abstract GetAllArtifactRetentionPolicyService getAllRetentionPolicyService();

    @Lookup
    public abstract GetArtifactRetentionPolicyService getRetentionPolicyService();

    @Lookup
    public abstract CreateArtifactRetentionPolicyService createRetentionPolicyService();

    @Lookup
    public abstract UpdateArtifactRetentionPolicyService updateRetentionPolicyService();

    @Lookup
    public abstract ArtifactRetentionRestoreService retentionRestoreService();

    @Lookup
    public abstract DeleteArtifactRetentionPolicyService deleteRetentionPolicyService();

    @Lookup
    public abstract DeleteArtifactRetentionPoliciesService deleteRetentionPoliciesService();

    @Lookup
    public abstract ArtifactRetentionSearchService getArtifactRetentionSearchService();

    @Lookup
    public abstract ArtifactRetentionArchivePoliciesExecutionsService getRetentionArchivePoliciesExecutionsService();

    @Lookup
    public abstract ArtifactRetentionRestorePoliciesExecutionsService getRetentionRestorePoliciesExecutionsService();

    @Lookup
    public abstract ArtifactRetentionArchiveStorageSummaryService getArtifactRetentionArchiveStorageSummaryService();

    @Lookup
    public abstract ArtifactRetentionTriggerPolicyService getArtifactRetentionTriggerPolicyService();

    @Lookup
    public abstract ArtifactRetentionPreviewArchivePolicyService previewRetentionPolicyService();

    @Lookup
    public abstract ArtifactRetentionPolicySearchCriteriaToAqlService convertPolicySearchCriteriaToAqlService();

    @Lookup
    public abstract ArtifactRetentionStorageLinkService getArtifactRetentionStorageLinkService();

    @Lookup
    public abstract ArtifactRetentionPreviewByPolicyKeyService getArtifactRetentionPreviewByPolicyKeyService();

    @Lookup
    public abstract ArtifactRetentionStopPolicyService getArtifactRetentionStopPolicyService();

    @Lookup
    public abstract GetArtifactRetentionArchiveRepositoriesService getArtifactRetentionArchiveRepositoriesService();
}
