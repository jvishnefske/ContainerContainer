package org.artifactory.release.bundle;

import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.storage.db.bundle.model.BundleNode;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Deletes a fully persisted release bundle from the bundle storage and artifact tree
 *
 * @author Uriah Levy
 */
@FunctionalInterface
public interface ReleaseBundleDeleteTemplate {
    void deleteBundle(ArtifactsBundleModel artifactsBundle, List<BundleNode> bundleNodes,
            Set<Long> nodeIdsWithArtifactsToDelete) throws SQLException;
}
