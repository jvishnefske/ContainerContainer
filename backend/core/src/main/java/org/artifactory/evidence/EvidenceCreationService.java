package org.artifactory.evidence;

import lombok.NonNull;
import org.artifactory.async.Async;
import org.artifactory.repo.RepoPath;
import org.jfrog.build.api.release.PromotionStatus;

import java.io.InputStream;
import java.util.List;

public interface EvidenceCreationService {

    void createAndDeployEvidenceForBuildPromotion(
            @NonNull String buildRepo,
            @NonNull String buildName,
            @NonNull String buildVersion,
            @NonNull String started,
            @NonNull PromotionStatus promotionStatus);

    /**
     * Creates OCI evidence for a list of repository paths using
     * the provided DSSE payload and base64-encoded public key or
     * a SigStore bundle JSON and null in the encodedPublicKey parameter.
     * <p>
     * Evidence is only created if the instance has an Enterprise Plus license.
     * </p>
     */
    @Async
    void createEvidenceOCI(
            @NonNull List<RepoPath> repoPaths,
            @NonNull String evidence,
            String encodedPublicKey);

    /**
     * Create evidence for a list of repository path.
     *
     * @param evidence The content of the evidence, e.g. a SigStore bundle JSON.
     */
    @Async
    void createEvidenceOCI(
            @NonNull List<RepoPath> repoPaths,
            @NonNull InputStream evidence);

    /**
     * Checks whether OCI evidence creation is allowed in the current Artifactory environment.
     * <p>
     * This includes checking whether:
     * - The OCI evidence feature is enabled.
     * - The Evidence service is enabled.
     * - The node is not a Cold instance.
     * - An Enterprise+ license is installed.
     */
    boolean isEvidenceOCICreationAllowed();
}