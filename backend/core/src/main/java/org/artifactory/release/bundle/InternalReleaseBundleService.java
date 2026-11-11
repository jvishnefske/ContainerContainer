package org.artifactory.release.bundle;

import lombok.NonNull;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.sapi.common.Lock;
import org.artifactory.storage.db.bundle.model.BundleNode;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * @author Inbar Tal
 */
public interface InternalReleaseBundleService {

    /**
     * Delete a Release Bundle files, artifacts, blobs and the actual bundle from db.
     * Note: no permission check is being made.
     *
     * @param bundleModel                  the release-bundle to delete.
     * @param bundleNodes                  list of all bundle nodes related to the given release-bundle.
     * @param nodeIdsWithArtifactsToDelete list of all node_ids belong to the above bundleNodes that their artifacts
     *                                     need to be deleted (because they are not related to any other release-bundle)
     */
    @Lock
    void deleteReleaseBundleInternal(ArtifactsBundleModel bundleModel,
            List<BundleNode> bundleNodes,
            Set<Long> nodeIdsWithArtifactsToDelete) throws SQLException;

    /**
     * Undeploy the bundle files if needed.
     *
     * @param artifactsBundle           the release-bundle to undeploy.
     * @param bundleNodes               list of all bundle nodes related to the given release-bundle.
     * @param nodeIdsWithArtifactsToDelete list of all node_ids belong to the above bundleNodes that their artifacts
     *                                  need to be deleted
     */
    @Lock
    void undeployIfNeeded(@NonNull ArtifactsBundleModel artifactsBundle,
            @NonNull List<BundleNode> bundleNodes,
            @NonNull Set<Long> nodeIdsWithArtifactsToDelete);

}
